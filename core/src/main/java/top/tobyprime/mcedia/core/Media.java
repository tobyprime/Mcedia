package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.ITexture;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue; // [新增] 导入线程安全的队列

public class Media implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Media.class);

    private final MediaDecoder decoder;
    private final Thread audioThread;
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    @Nullable
    private ITexture texture;

    private boolean paused = true;
    private boolean isLiveStream = false;
    private float speed = 1;
    private boolean looping = false;

    // [移除] 不再需要手动的锁和单个帧的引用
    // private final Object videoFrameLock = new Object();
    // @Nullable
    // private VideoFrame currentVideoFrame;

    // [新增] 使用线程安全的队列来在解码线程和渲染线程之间传递视频帧
    private final ConcurrentLinkedQueue<VideoFrame> videoFrameQueue = new ConcurrentLinkedQueue<>();


    private boolean isBuffering = true;
    private static final int AUDIO_BUFFER_TARGET = 256;
    private static final int VIDEO_BUFFER_TARGET = 30;

    public Media(String url, DecoderConfiguration config) {
        this(new VideoInfo(url, null), null, config);
    }

    public Media(VideoInfo info, DecoderConfiguration config) {
        this(info, null, config);
    }

    public Media(VideoInfo info, @Nullable String cookie, DecoderConfiguration config) {
        try {
            decoder = new MediaDecoder(info, cookie, config);
            isLiveStream = decoder.getDuration() <= 0 || Double.isInfinite(decoder.getDuration());
            LOGGER.info("检测到 {}流: {}", isLiveStream ? "直播" : "点播", info.getVideoUrl());
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
        audioThread = new Thread(this::playLoop);
        audioThread.setName("Mcedia-Audio-Thread");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void playLoop() {
        long nextPlayTime = System.nanoTime();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (paused) {
                    Thread.sleep(10);
                    continue;
                }

                if (isBuffering) {
                    boolean hasEnoughBuffer = isLiveStream ? (decoder.audioQueue.size() > 50)
                            : (decoder.audioQueue.size() > AUDIO_BUFFER_TARGET && decoder.videoQueue.size() > VIDEO_BUFFER_TARGET);

                    if (hasEnoughBuffer || decoder.isEof()) {
                        isBuffering = false;
                        nextPlayTime = System.nanoTime();
                        LOGGER.debug("Buffering complete, resuming playback.");
                    } else {
                        Thread.sleep(50);
                        continue;
                    }
                }

                Frame currFrame = decoder.audioQueue.poll();
                if (currFrame == null) {
                    if (decoder.isEof()) {
                        LOGGER.debug("音频队列为空且解码器已到达EOF，准备退出播放线程。");
                        break;
                    }

                    if (!isBuffering) {
                        isBuffering = true;
                        LOGGER.warn("缓冲区为空，开始缓冲...");
                    }
                    Thread.sleep(10);
                    continue;
                }

                // [修改] 视频帧处理逻辑
                // 将所有时间戳小于等于当前音频帧的视频帧放入待上传队列
                while (!decoder.videoQueue.isEmpty() && decoder.videoQueue.peek().ptsUs <= currFrame.timestamp) {
                    VideoFrame frameToQueue = decoder.videoQueue.poll();
                    if (frameToQueue != null) {
                        // 如果渲染队列积压过多，则丢弃旧帧以追赶进度
                        if (videoFrameQueue.size() > VIDEO_BUFFER_TARGET) {
                            videoFrameQueue.poll().close(); // 取出并关闭最旧的帧
                        }
                        videoFrameQueue.offer(frameToQueue);
                    }
                }

                uploadBuffer(currFrame);

                Frame nextFrame = decoder.audioQueue.peek();
                long intervalUs = (nextFrame != null) ? (long) ((nextFrame.timestamp - currFrame.timestamp) / speed) : 20_000L;
                if (intervalUs <= 0) intervalUs = 20_000L;
                nextPlayTime += intervalUs * 1000L;
                long sleepNanos = nextPlayTime - System.nanoTime();
                if (sleepNanos > 0) {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                }
                currFrame.close();
            }
        } catch (InterruptedException ignored) {
        }
    }

    public void uploadVideo() {
        if (paused || texture == null) return;

        VideoFrame frameToUpload = null;
        // [修改] 从队列中获取最新的帧，并丢弃所有旧的帧
        // 这可以防止在渲染卡顿时，播放过时的视频帧
        while (videoFrameQueue.size() > 1) {
            VideoFrame oldFrame = videoFrameQueue.poll();
            if (oldFrame != null) {
                oldFrame.close();
            }
        }
        frameToUpload = videoFrameQueue.poll();

        if (frameToUpload != null) {
            try {
                if (frameToUpload.buffer != null && frameToUpload.buffer.hasRemaining()) {
                    texture.upload(frameToUpload);
                }
            } catch (Exception e) {
                LOGGER.error("在视频帧上传期间发生错误", e);
            } finally {
                // [关键修改] 无论上传成功与否，帧的生命周期都在这里结束，立即释放内存
                frameToUpload.close();
            }
        }
    }


    public void setLooping(boolean looping) { this.looping = looping; }
    public void play() { LOGGER.info("开始播放"); paused = false; }
    public void pause() { LOGGER.info("暂停播放"); paused = true; }
    public long getDurationUs() { if (decoder.audioQueue.isEmpty()) return 0; Frame lastFrame = decoder.audioQueue.peekLast(); return (lastFrame != null) ? lastFrame.timestamp : 0; }
    public long getLengthUs() { return decoder.getDuration(); }
    public void setSpeed(float speed) { if (speed == this.speed) return; this.speed = speed; this.audioSources.forEach(s -> s.setPitch(speed)); }
    public void seek(long targetUs) { if (isLiveStream) { LOGGER.warn("直播流不支持seek操作"); return; } LOGGER.info("移动到 {} us", targetUs); try { if (targetUs > getLengthUs()) targetUs = getLengthUs(); if (targetUs < 0) targetUs = 0; decoder.seek(targetUs); isBuffering = true; } catch (Exception e) { LOGGER.error("Seek failed", e); } }
    public void bindTexture(ITexture texture) { this.texture = texture; }
    public void unbindTexture() { this.texture = null; }
    public void bindAudioSource(IAudioSource audioBuffer) { this.audioSources.add(audioBuffer); }
    public void unbindAudioSource(IAudioSource audioBuffer) { this.audioSources.remove(audioBuffer); }
    private void uploadBuffer(Frame frame) { for (var audioSource : audioSources) { audioSource.upload(AudioDataConverter.AsAudioData(frame, -1)); } }
    public boolean isPlaying() { return !paused && !decoder.isEnded(); }
    public boolean isPaused() { return paused; }
    public boolean isEnded() { return !isLiveStream && !audioThread.isAlive(); }
    public boolean isLiveStream() { return isLiveStream; }
    public int getWidth() { return decoder.getWidth(); }
    public int getHeight() { return decoder.getHeight(); }
    public float getAspectRatio() { if (getHeight() == 0) return 16f / 9f; return (float) getWidth() / getHeight(); }

    @Override
    public void close() {
        audioThread.interrupt();
        this.audioSources.forEach(s -> s.setPitch(1));
        try {
            audioThread.join();
        } catch (InterruptedException e) {
            LOGGER.warn("音频上传线程停止异常", e);
        }
        audioSources.forEach(IAudioSource::clearBuffer);

        // [修改] 清理队列中所有剩余的帧
        while (!videoFrameQueue.isEmpty()) {
            VideoFrame frame = videoFrameQueue.poll();
            if (frame != null) {
                frame.close();
            }
        }

        decoder.close();
    }
}
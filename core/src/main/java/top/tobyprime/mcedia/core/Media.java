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
import java.util.concurrent.atomic.AtomicLong;

public class Media implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Media.class);

    private MediaDecoder decoder;
    private Thread audioThread;
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    @Nullable
    private ITexture texture;
    @Nullable
    private VideoFramePool videoFramePool;

    private boolean paused = true;
    private boolean isLiveStream = false;
    private float speed = 1;
    private boolean looping = false;

    private final ConcurrentLinkedQueue<VideoFrame> videoFrameQueue = new ConcurrentLinkedQueue<>();

    private final AtomicLong currentPtsUs = new AtomicLong(0);

    private boolean isBuffering = true;
    private static final int AUDIO_BUFFER_TARGET = 256;
    private static final int VIDEO_BUFFER_TARGET = 60;
    private static final int VIDEO_BUFFER_LOW_WATERMARK = 10;

    public Media(String url, DecoderConfiguration config) {
        this(new VideoInfo(url, null), null, config, 0);
    }

    public Media(VideoInfo info, DecoderConfiguration config) {
        this(info, null, config, 0);
    }

    public Media(VideoInfo info, @Nullable String cookie, DecoderConfiguration config, long initialSeekUs) {
        // 只有需要视频时才创建内存池
        if (config.enableVideo) {
            this.videoFramePool = new VideoFramePool(120, 3840 * 2160 * 4); // 使用硬编码值
        } else {
            this.videoFramePool = null;
        }

        try {
            decoder = new MediaDecoder(info, cookie, config, this.videoFramePool, initialSeekUs);
            isLiveStream = decoder.getDuration() <= 0 || Double.isInfinite(decoder.getDuration());
            // 如果有初始跳转，将时钟设置为跳转时间
            if (initialSeekUs > 0) {
                currentPtsUs.set(initialSeekUs);
            }
            LOGGER.info("检测到 {}流: {}", isLiveStream ? "直播" : "点播", info.getVideoUrl());
        } catch (FFmpegFrameGrabber.Exception e) {
            if (this.videoFramePool != null) {
                this.videoFramePool.close();
            }
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

                // 初始缓冲 或 重新缓冲
                // 如果需要缓冲（isBuffering为true），则持续等待直到满足条件
                if (isBuffering) {
                    // 缓冲完成的条件：解码结束，或者音视频队列都超过目标的一半
                    boolean hasEnoughBuffer = decoder.isEof() ||
                            (decoder.audioQueue.size() > AUDIO_BUFFER_TARGET / 2 && decoder.videoQueue.size() > VIDEO_BUFFER_TARGET / 2);
                    if (hasEnoughBuffer) {
                        isBuffering = false; // 退出缓冲状态
                        nextPlayTime = System.nanoTime(); // 重置播放时钟
                        LOGGER.debug("缓冲完成，恢复播放。");
                    } else {
                        Thread.sleep(50); // 等待解码器填充数据
                        continue;
                    }
                }

                // 正常播放
                // 在播放过程中，如果视频帧被耗尽，则触发重新缓冲
                if (decoder.videoQueue.size() < VIDEO_BUFFER_LOW_WATERMARK && !decoder.isEof()) {
                    LOGGER.warn("视频队列低于水位线 ({})，重新进入缓冲状态...", VIDEO_BUFFER_LOW_WATERMARK);
                    isBuffering = true;
                    continue;
                }

                Frame currFrame = decoder.audioQueue.poll();
                if (currFrame == null) {
                    if (decoder.isEof()) {
                        break;
                    }
                    Thread.sleep(5);
                    continue;
                }

                this.currentPtsUs.set(currFrame.timestamp);

                // 视频帧处理逻辑
                // 将所有时间戳小于等于当前音频帧的视频帧放入待上传队列
                while (!decoder.videoQueue.isEmpty() && decoder.videoQueue.peek().ptsUs <= currFrame.timestamp) {
                    VideoFrame frameToQueue = decoder.videoQueue.poll();
                    if (frameToQueue != null) {
                        if (videoFrameQueue.size() >= VIDEO_BUFFER_TARGET) {
                            videoFrameQueue.poll().close();
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
        } finally {
            LOGGER.info("音频播放循环已结束。");
        }
    }

    public void uploadVideo() {
        if (paused || texture == null) return;

        VideoFrame frameToUpload = null;

        // 丢弃所有过时的帧，只保留最接近当前音频时钟的那一帧进行渲染
        while (!videoFrameQueue.isEmpty() && videoFrameQueue.peek().ptsUs <= this.currentPtsUs.get()) {
            // 从队列中移除一帧
            VideoFrame potentialFrame = videoFrameQueue.poll();

            // 如果队列空了，或者下一帧的时间戳在未来，那么我们手上这帧就是最合适的
            if (videoFrameQueue.isEmpty() || videoFrameQueue.peek().ptsUs > this.currentPtsUs.get()) {
                frameToUpload = potentialFrame;
                break;
            } else {
                // 否则，意味着还有更合适的帧（时间戳更晚，但仍然<=音频时钟），丢弃当前帧
                if (potentialFrame != null) {
                    potentialFrame.close();
                }
            }
        }

        if (frameToUpload != null) {
            try {
                if (frameToUpload.buffer != null && frameToUpload.buffer.hasRemaining()) {
                    texture.upload(frameToUpload);
                }
            } catch (Exception e) {
                LOGGER.error("在视频帧上传期间发生错误", e);
            } finally {
                frameToUpload.close();
            }
        }
    }


    public void setLooping(boolean looping) { this.looping = looping; }
//    public void setInitialSeek(long us) {this.initialSeekUs = us;}
    public void play() {LOGGER.info("开始播放");paused = false;}
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
            audioThread.join(500); // 等待音频线程结束
        } catch (InterruptedException e) {
            LOGGER.warn("音频上传线程停止异常", e);
        }
        audioSources.forEach(IAudioSource::clearBuffer);

        // 清理队列中所有剩余的帧
        while (!videoFrameQueue.isEmpty()) {
            VideoFrame frame = videoFrameQueue.poll();
            if (frame != null) {
                frame.close();
            }
        }

        decoder.close();

        if (videoFramePool != null) {
            videoFramePool.close();
        }
    }
}
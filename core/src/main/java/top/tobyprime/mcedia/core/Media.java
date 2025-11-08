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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class Media implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Media.class);

    private final MediaDecoder decoder;
    private final Thread audioThread;
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    @Nullable private ITexture texture;
    @Nullable private final VideoFramePool videoFramePool;

    private volatile boolean paused = true;
    private boolean isLiveStream = false;
    private float speed = 1;
    private boolean looping = false;
    private volatile boolean isBuffering = true;

    private long baseTime;
    private long baseDuration;
    private long lastAudioPts = -1;

    private final ConcurrentLinkedQueue<VideoFrame> videoFrameQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong currentPtsUs = new AtomicLong(0);

    private static final int AUDIO_BUFFER_TARGET = 512;
    private static final int VIDEO_BUFFER_TARGET = 90;
    private static final int VIDEO_BUFFER_LOW_WATERMARK = 42;


    public Media(VideoInfo info, @Nullable String cookie, DecoderConfiguration config, long initialSeekUs) {
        if (config.enableVideo) {
            this.videoFramePool = new VideoFramePool(120, 3840 * 2160 * 4);
        } else {
            this.videoFramePool = null;
        }

        try {
            decoder = new MediaDecoder(info, cookie, config, this.videoFramePool, initialSeekUs);
            isLiveStream = decoder.getDuration() <= 0 || Double.isInfinite(decoder.getDuration());

            if (initialSeekUs > 0 && !isLiveStream) {
                this.baseDuration = initialSeekUs;
                this.lastAudioPts = initialSeekUs;
                this.currentPtsUs.set(initialSeekUs);
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

                if (isBuffering) {
                    boolean hasEnoughBuffer = decoder.isEof() ||
                            (decoder.audioQueue.size() > AUDIO_BUFFER_TARGET / 2 && decoder.videoQueue.size() > VIDEO_BUFFER_TARGET / 2);
                    if (hasEnoughBuffer) {
                        isBuffering = false;
                        nextPlayTime = System.nanoTime();
                        LOGGER.debug("缓冲完成，恢复播放。");
                    } else {
                        Thread.sleep(50);
                        continue;
                    }
                }

                if (decoder.videoQueue.size() < VIDEO_BUFFER_LOW_WATERMARK && !decoder.isEof()) {
                    LOGGER.warn("视频队列低于水位线 ({})，重新进入缓冲状态...", VIDEO_BUFFER_LOW_WATERMARK);
                    isBuffering = true;
                    continue;
                }

                Frame currFrame = decoder.audioQueue.poll();
                if (currFrame == null) {
                    if (decoder.isEof()) {
                        if (looping && !isLiveStream && getLengthUs() > 0) {
                            LOGGER.info("循环播放：回到起点。");
                            seek(0);
                            continue;
                        } else {
                            break;
                        }
                    }
                    Thread.sleep(5);
                    continue;
                }

                long pts = currFrame.timestamp;
                this.lastAudioPts = pts;
                this.currentPtsUs.set(pts);

                while (!decoder.videoQueue.isEmpty() && decoder.videoQueue.peek().ptsUs <= pts) {
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
                long intervalUs = (nextFrame != null) ? (long) ((nextFrame.timestamp - pts) / speed) : 20_000L;
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
        long currentAudioPts = this.currentPtsUs.get();
        while (!videoFrameQueue.isEmpty() && videoFrameQueue.peek().ptsUs <= currentAudioPts) {
            VideoFrame potentialFrame = videoFrameQueue.poll();
            if (videoFrameQueue.isEmpty() || videoFrameQueue.peek().ptsUs > currentAudioPts) {
                frameToUpload = potentialFrame;
                break;
            } else {
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

    public void play() {
        LOGGER.info("开始播放");
        if (paused) {
            baseTime = System.currentTimeMillis();
            if (!isLiveStream) {
                baseDuration = lastAudioPts > 0 ? lastAudioPts : (baseDuration > 0 ? baseDuration : 0);
            } else {
                baseDuration = 0;
                lastAudioPts = -1;
            }
            paused = false;
        }
    }

    public void pause() {
        LOGGER.info("暂停播放");
        if (!paused) {
            paused = true;
            if (!isLiveStream) {
                baseDuration = getDurationUs();
            }
        }
    }

    public long getDurationUs() {
        if (paused) {
            return isLiveStream ? 0 : baseDuration;
        }
        if (isLiveStream) {
            return lastAudioPts < 0 ? 0 : lastAudioPts;
        }
        return baseDuration + (long)((System.currentTimeMillis() - baseTime) * 1000L * speed);
    }

    /**
     * [最终修复]
     * 跳转到指定时间戳 (微秒)
     */
    public void seek(long targetUs) {
        if (isLiveStream) {
            LOGGER.warn("直播流不支持seek操作");
            return;
        }
        LOGGER.info("移动到 {} us", targetUs);
        try {
            long length = getLengthUs();
            targetUs = Math.max(0, Math.min(targetUs, length));

            // 1. 发送异步seek指令到底层解码器
            decoder.seek(targetUs);

            // 2. [关键修复] 强制、同步地清空当前所有队列，防止播放线程消费旧数据
            decoder.audioQueue.forEach(Frame::close); // 释放内存
            decoder.audioQueue.clear();

            videoFrameQueue.forEach(VideoFrame::close); // 释放内存
            videoFrameQueue.clear();

            // 3. 同步重置所有时钟变量到目标时间
            baseDuration = targetUs;
            baseTime = System.currentTimeMillis();
            lastAudioPts = targetUs;
            currentPtsUs.set(targetUs);

            // 4. 强制进入缓冲状态，等待新数据填充队列
            isBuffering = true;
            LOGGER.debug("Seek 完成，队列已清空，等待数据流恢复...");

        } catch (Exception e) {
            LOGGER.error("Seek failed", e);
        }
    }

    public void setLooping(boolean looping) { this.looping = looping; }
    public long getLengthUs() { return decoder.getDuration(); }
    public void setSpeed(float speed) { if (speed == this.speed) return; this.speed = speed; this.audioSources.forEach(s -> s.setPitch(speed)); }
    public void bindTexture(ITexture texture) { this.texture = texture; }
    public void unbindTexture() { this.texture = null; }
    public void bindAudioSource(IAudioSource audioBuffer) { this.audioSources.add(audioBuffer); }
    public void unbindAudioSource(IAudioSource audioBuffer) { this.audioSources.remove(audioBuffer); }
    private void uploadBuffer(Frame frame) { for (var audioSource : audioSources) { audioSource.upload(AudioDataConverter.AsAudioData(frame, -1)); } }
    public boolean isPlaying() { return !paused; }
    public boolean isPaused() { return paused; }
    public boolean isEnded() { return !isLiveStream && decoder.isEof() && decoder.audioQueue.isEmpty(); }
    public boolean isLiveStream() { return isLiveStream; }
    public int getWidth() { return decoder.getWidth(); }
    public int getHeight() { return decoder.getHeight(); }
    public float getAspectRatio() { if (getHeight() == 0) return 16f / 9f; return (float) getWidth() / getHeight(); }

    @Override
    public void close() {
        audioThread.interrupt();
        this.audioSources.forEach(s -> s.setPitch(1));
        try {
            audioThread.join(500);
        } catch (InterruptedException e) {
            LOGGER.warn("音频上传线程停止异常", e);
        }
        audioSources.forEach(IAudioSource::clearBuffer);
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
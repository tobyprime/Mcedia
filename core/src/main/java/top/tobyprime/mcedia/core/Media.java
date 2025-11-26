package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.IMediaInfo;
import top.tobyprime.mcedia.interfaces.ITexture;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Media implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Media.class);

    private final MediaDecoder decoder;
    private final Thread audioThread;
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    @Nullable
    private ITexture texture;
    @Nullable
    private final VideoFramePool videoFramePool;
    private final VideoInfo videoInfo;

    private volatile boolean paused = true;
    private boolean isLiveStream = false;
    private float speed = 1;
    private boolean looping = false;
    private volatile boolean isBuffering = true;
    private volatile boolean needsTimeReset = false;

    private long baseTime;
    private long baseDuration;
    private long lastAudioPts = -1;

    private final ConcurrentLinkedQueue<VideoFrame> videoFrameQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong currentPtsUs = new AtomicLong(0);
    private final AtomicBoolean needsReconnect = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private static final int AUDIO_BUFFER_TARGET = McediaConfig.getBufferingAudioTarget();
    private static final int VIDEO_BUFFER_TARGET = McediaConfig.getBufferingVideoTarget();
    private static final int VIDEO_BUFFER_LOW_WATERMARK = McediaConfig.getBufferingVideoLowWatermark();
    private static final long STREAM_STALL_TIMEOUT_MS = McediaConfig.getPlayerStallTimeoutMs();

    public VideoInfo getVideoInfo() {
        return this.videoInfo;
    }

    public Media(VideoInfo info, @Nullable String cookie, DecoderConfiguration config, long initialSeekUs) {
        this.videoInfo = info;
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

    public boolean needsReconnect() {
        return needsReconnect.get();
    }

    /**
     * 主播放循环分发器
     * 根据是否为直播流，选择不同的播放逻辑。
     */
    public void playLoop() {
        try {
            if (isLiveStream) {
                playLoopLive();
            } else if (!decoder.hasAudio()) {
                LOGGER.info("未检测到音频流，使用系统时钟驱动视频播放。");
                playLoopVODNoAudio();
            } else {
                playLoopVOD();
            }
        } catch (InterruptedException ignored) {
        } finally {
            LOGGER.info("播放循环已结束。");
        }
    }

    /**
     * 无音频时的 VOD 播放逻辑（基于系统时间）
     */

    private void playLoopVODNoAudio() throws InterruptedException {
        long startTimeNs = -1;
        long startPtsUs = 0;

        // 等待缓冲
        while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
            if (paused) {
                startTimeNs = -1;
                Thread.sleep(10);
                continue;
            }

            // 缓冲逻辑
            if (isBuffering) {
                if (decoder.isEof() || decoder.videoQueue.size() > VIDEO_BUFFER_TARGET / 2) {
                    isBuffering = false;
                    startTimeNs = System.nanoTime();
                    startPtsUs = currentPtsUs.get();
                    LOGGER.info("缓冲完成 (无音频)，开始播放。");
                } else {
                    Thread.sleep(20);
                    continue;
                }
            }

            // 检查水位线
            if (decoder.videoQueue.size() < VIDEO_BUFFER_LOW_WATERMARK && !decoder.isEof()) {
                isBuffering = true;
                continue;
            }

            // 计算目标时间戳
            if (startTimeNs == -1) {
                startTimeNs = System.nanoTime();
                startPtsUs = currentPtsUs.get();
            }

            long elapsedNs = System.nanoTime() - startTimeNs;
            long targetPts = startPtsUs + (long)(elapsedNs / 1000.0 * speed);

            if (needsTimeReset) {
                startTimeNs = System.nanoTime();
                startPtsUs = currentPtsUs.get();
                needsTimeReset = false;
                targetPts = startPtsUs;
            }

            this.currentPtsUs.set(targetPts);

            // 视频结束检测
            if (decoder.videoQueue.isEmpty() && decoder.isEof()) {
                break;
            }

            // 同步视频帧
            while (!decoder.videoQueue.isEmpty()) {
                VideoFrame head = decoder.videoQueue.peek();
                if (head != null && head.ptsUs <= targetPts) {
                    VideoFrame frame = decoder.videoQueue.poll();
                    if (frame != null) {
                        if (videoFrameQueue.size() >= VIDEO_BUFFER_TARGET) {
                            VideoFrame old = videoFrameQueue.poll();
                            if (old != null) old.close();
                        }
                        videoFrameQueue.offer(frame);
                    }
                } else {
                    break;
                }
            }

            Thread.sleep(5);
        }
    }

    /**
     * 用于播放点播视频 (VOD) 的原始逻辑
     * 专门处理VOD。
     */
    private void playLoopVOD() throws InterruptedException {
        long nextPlayTime = System.nanoTime();
        long lastFrameTime = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
            if (paused) {
                Thread.sleep(10);
                continue;
            }

            if (needsTimeReset) {
                nextPlayTime = System.nanoTime();
                needsTimeReset = false;
                LOGGER.debug("从暂停中恢复，已重置播放节拍器。");
            }
            if (isBuffering) {
                boolean hasEnoughBuffer = decoder.isEof() ||
                        (decoder.audioQueue.size() > AUDIO_BUFFER_TARGET / 2 && decoder.videoQueue.size() > VIDEO_BUFFER_TARGET / 2);
                if (hasEnoughBuffer) {
                    isBuffering = false;
                    nextPlayTime = System.nanoTime();
                    LOGGER.info("缓冲完成，恢复播放。音频队列: {}, 视频队列: {}", decoder.audioQueue.size(), decoder.videoQueue.size());
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
                    break;
                }
                if (System.currentTimeMillis() - lastFrameTime > 5000) {
                    LOGGER.warn("直播流或视频流中断，触发重连...");
                    needsReconnect.set(true);
                    break;
                }
                Thread.sleep(5);
                continue;
            }

            try {
                long pts = currFrame.timestamp;
                this.lastAudioPts = pts;
                this.currentPtsUs.set(pts);

                while (!decoder.videoQueue.isEmpty()) {
                    VideoFrame head = decoder.videoQueue.peek();
                    if (head == null) break;
                    if (head.ptsUs <= pts) {
                        VideoFrame frameToQueue = decoder.videoQueue.poll();
                        if (frameToQueue != null) {
                            if (videoFrameQueue.size() >= VIDEO_BUFFER_TARGET) {
                                VideoFrame oldFrame = videoFrameQueue.poll();
                                if (oldFrame != null) oldFrame.close();
                            }
                            videoFrameQueue.offer(frameToQueue);
                        }
                    } else {
                        break;
                    }
                }

                uploadBuffer(currFrame);

                Frame nextFrame = decoder.audioQueue.peek();
                long intervalUs = (nextFrame != null) ? (long) ((nextFrame.timestamp - pts) / speed) : 20_000L;
                if (intervalUs <= 0) intervalUs = 20_000L;
                nextPlayTime += intervalUs * 1000L;
                long sleepNanos = nextPlayTime - System.nanoTime();
                if (sleepNanos > 0) {
                    long sleepMs = sleepNanos / 1_000_000;
                    int sleepNs = (int) (sleepNanos % 1_000_000);
                    Thread.sleep(sleepMs, sleepNs);
                }
            } finally {
                currFrame.close();
            }
        }
    }

    /**
     * 用于播放直播流 (Live Stream) 的新逻辑
     * 这个方法使用实时时钟，不依赖特定流，能健壮地处理各种直播情况。
     */
    private void playLoopLive() throws InterruptedException {
        long streamStartTimeNs = -1;
        long firstPtsUs = -1;
        long lastDataTime = System.currentTimeMillis();

        isBuffering = true;

        while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
            if (paused) {
                streamStartTimeNs = -1;
                Thread.sleep(10);
                continue;
            }

            boolean hasData = !decoder.audioQueue.isEmpty() || !decoder.videoQueue.isEmpty();

            if (hasData) {
                lastDataTime = System.currentTimeMillis();
            } else {
                if (decoder.isEof()) {
                    LOGGER.info("直播流已正常结束。");
                    break;
                }
                if (System.currentTimeMillis() - lastDataTime > STREAM_STALL_TIMEOUT_MS) {
                    LOGGER.warn("直播流数据中断超过 {} 毫秒，触发重连...", STREAM_STALL_TIMEOUT_MS);
                    needsReconnect.set(true);
                    break;
                }
                Thread.sleep(20);
                continue;
            }

            if (streamStartTimeNs == -1) {
                if (decoder.audioQueue.isEmpty() && decoder.videoQueue.isEmpty()) {
                    if (decoder.isEof()) break;
                    Thread.sleep(20);
                    continue;
                }

                Frame firstAudio = decoder.audioQueue.peek();
                VideoFrame firstVideo = decoder.videoQueue.peek();
                firstPtsUs = Long.MAX_VALUE;

                if (firstAudio != null) firstPtsUs = Math.min(firstPtsUs, firstAudio.timestamp);
                if (firstVideo != null) firstPtsUs = Math.min(firstPtsUs, firstVideo.ptsUs);

                streamStartTimeNs = System.nanoTime();
                isBuffering = false;
                LOGGER.info("直播流已接收到首批数据，开始播放。起始 PTS: {}", firstPtsUs);
            }
            long elapsedNs = System.nanoTime() - streamStartTimeNs;
            long currentTargetPts = firstPtsUs + (long) (elapsedNs / 1000.0 * speed);
            this.currentPtsUs.set(currentTargetPts);
            while (!decoder.audioQueue.isEmpty() && decoder.audioQueue.peek().timestamp <= currentTargetPts) {
                Frame audioFrame = decoder.audioQueue.poll();
                if (audioFrame != null) {
                    uploadBuffer(audioFrame);
                    audioFrame.close();
                }
            }
            while (!decoder.videoQueue.isEmpty() && decoder.videoQueue.peek().ptsUs <= currentTargetPts) {
                VideoFrame videoFrame = decoder.videoQueue.poll();
                if (videoFrame != null) {
                    if (videoFrameQueue.size() >= VIDEO_BUFFER_TARGET) {
                        VideoFrame oldFrame = videoFrameQueue.poll();
                        if (oldFrame != null) oldFrame.close();
                    }
                    videoFrameQueue.offer(videoFrame);
                }
            }
            Thread.sleep(10);
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
        if (paused) {
            LOGGER.debug("恢复播放");
            if (isLiveStream) {
                LOGGER.info("正在从暂停中恢复直播，将同步到最新时间...");
                isBuffering = true;
            } else {
                this.needsTimeReset = true;
            }
            paused = false;
        }
    }

    public void pause() {
        LOGGER.info("暂停播放");
        if (!paused) {
            paused = true;
        }
    }

    public long getDurationUs() {
        return currentPtsUs.get();
    }

    /**
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

            decoder.seek(targetUs);

            VideoFrame vf;
            while ((vf = videoFrameQueue.poll()) != null) {
                vf.close();
            }

            currentPtsUs.set(targetUs);

            isBuffering = true;
            LOGGER.debug("Seek 完成，队列已清空，等待数据流恢复...");

        } catch (Exception e) {
            LOGGER.error("Seek failed", e);
        }
    }


    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public long getLengthUs() {
        return decoder.getDuration();
    }

    public void setSpeed(float speed) {
        if (speed == this.speed) return;
        this.speed = speed;
        this.audioSources.forEach(s -> s.setPitch(speed));
    }

    public void bindTexture(ITexture texture) {
        this.texture = texture;
    }

    public void unbindTexture() {
        this.texture = null;
    }

    public void bindAudioSource(IAudioSource audioBuffer) {
        this.audioSources.add(audioBuffer);
    }

    public void unbindAudioSource(IAudioSource audioBuffer) {
        this.audioSources.remove(audioBuffer);
    }

    private void uploadBuffer(Frame frame) {
        for (var audioSource : audioSources) {
            audioSource.upload(AudioDataConverter.AsAudioData(frame, -1));
        }
    }

    public boolean isPlaying() {
        return !paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isBuffering() {
        if (isLiveStream) {
            return false;
        }
        return this.isBuffering;
    }

    public boolean isEnded() {
        return !isLiveStream && decoder.isEof() && decoder.audioQueue.isEmpty();
    }

    public boolean isLiveStream() {
        return isLiveStream;
    }

    public int getWidth() {
        return decoder.getWidth();
    }

    public int getHeight() {
        return decoder.getHeight();
    }

    public float getAspectRatio() {
        if (getHeight() == 0) return 16f / 9f;
        return (float) getWidth() / getHeight();
    }

    @Override
    public void close() {
        if (isClosed.getAndSet(true)) {
            return;
        }
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

    public IMediaInfo getMediaInfo() {
        return this.videoInfo;
    }
}
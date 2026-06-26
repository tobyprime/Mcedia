package top.tobyprime.mcedia.decoder.ffmpeg;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.audio.AudioFrame;
import top.tobyprime.mcedia.api.config.DecoderConfiguration;
import top.tobyprime.mcedia.api.decoder.Decoder;
import top.tobyprime.mcedia.api.media.MediaPlayInfo;
import top.tobyprime.mcedia.api.decoder.metrics.DecoderMetrics;
import top.tobyprime.mcedia.api.stream.FrameStream;
import top.tobyprime.mcedia.api.video.VideoFrame;
import top.tobyprime.mcedia.decoder.ffmpeg.frame.FfmpegAudioFrame;
import top.tobyprime.mcedia.decoder.ffmpeg.frame.FfmpegVideoFrame;
import top.tobyprime.mcedia.decoder.ffmpeg.internal.FfmpegProcessImageFlags;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FfmpegDecoder implements Decoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegDecoder.class);
    private static final int LOW_OVERHEAD_MAX_VIDEO_FRAMES = 8;

    static {
        FFmpegLogCallback.set();
    }

    private final ReentrantReadWriteLock masterGrabberLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock audioGrabberLock = new ReentrantReadWriteLock();
    private final MediaPlayInfo mediaDisc;
    private final DecoderConfiguration config;
    private static final long DECODER_THREAD_JOIN_TIMEOUT_MILLIS = 2_000L;
    private final AtomicBoolean isOpen = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean isEnded = new AtomicBoolean(false);
    private final AtomicBoolean closeRequested = new AtomicBoolean(false);
    private final AtomicBoolean metricsDecoderOpened = new AtomicBoolean(false);
    private final AtomicLong decodeGeneration = new AtomicLong(0);
    private static final int DECODER_MAX_AUDIO_FRAMES = 128; // 低于 100 frame 对于 bilibili hls 直播切片可能会卡顿
    private static final int DECODER_MAX_VIDEO_FRAMES = 24;
    private final AtomicBoolean lowOverhead = new AtomicBoolean(false);
    private final AtomicBoolean runtimeVideoEnabled = new AtomicBoolean(true);
    private final AtomicBoolean runtimeAudioEnabled = new AtomicBoolean(true);
    private final FrameStream<VideoFrame> videoStream = new FrameStream<>(DECODER_MAX_VIDEO_FRAMES);
    private final FrameStream<AudioFrame> audioStream = new FrameStream<>(DECODER_MAX_AUDIO_FRAMES);

    @Nullable
    private volatile Thread masterDecoderThread;
    @Nullable
    private volatile Thread audioDecodeThread;
    @Nullable
    private volatile FFmpegFrameGrabber masterGrabber = null;
    @Nullable
    private volatile FFmpegFrameGrabber audioGrabber = null;

    public FfmpegDecoder(MediaPlayInfo mediaDisc, DecoderConfiguration decoderConfiguration) {
        this.mediaDisc = mediaDisc;
        config = decoderConfiguration;
    }

    public void open() {
        if (isOpen.get()) return;
        masterGrabberLock.writeLock().lock();
        audioGrabberLock.writeLock().lock();
        try {
            runtimeVideoEnabled.set(config.getEnableVideo());
            runtimeAudioEnabled.set(config.getEnableAudio());
            if (config.getEnableVideo()) {
                masterGrabber = buildGrabber(mediaDisc.getUrl(), true);
                masterGrabber.start();
                applyRuntimeVideoState(masterGrabber);
            }

            if (config.getEnableAudio() && mediaDisc.hasAudio()) {
                var audioUrl = mediaDisc.getAudioUrl();
                if (audioUrl == null) {
                    throw new IllegalStateException("audioUrl must not be null when hasAudio() is true");
                }
                // 如果没有视频流，则将音频流作为主流
                if (masterGrabber == null) {
                    masterGrabber = buildGrabber(audioUrl, false);
                    masterGrabber.start();
                } else {
                    audioGrabber = buildGrabber(audioUrl, false);
                    audioGrabber.start();
                }
            }

            if (masterGrabber == null) {
                throw new RuntimeException("无可用流");
            }

            isEnded.set(false);
            isClosed.set(false);
            closeRequested.set(false);
            isOpen.set(true);
            if (metricsDecoderOpened.compareAndSet(false, true)) {
                DecoderMetrics.tracker().onDecoderOpened();
            }
            LOGGER.info("打开媒体完成. 长度 {} 地址 {}", getDuration(), mediaDisc.getUrl());
        } catch (Exception e) {
            LOGGER.info("打开解码器失败. 地址 {}", mediaDisc.getUrl());
            RuntimeException cleanupError = null;
            try {
                closeGrabbersUnsafe();
            } catch (RuntimeException closeError) {
                cleanupError = closeError;
            }
            isOpen.set(false);
            isClosed.set(true);
            isEnded.set(true);

            if (e instanceof RuntimeException runtimeException) {
                if (cleanupError != null) {
                    runtimeException.addSuppressed(cleanupError);
                }
                throw runtimeException;
            }

            RuntimeException wrapped = new RuntimeException(e);
            if (cleanupError != null) {
                wrapped.addSuppressed(cleanupError);
            }
            throw wrapped;
        } finally {
            masterGrabberLock.writeLock().unlock();
            audioGrabberLock.writeLock().unlock();
        }

        startDecoder();
    }

    public void close() {
        boolean shouldCloseStreams = false;
        Thread masterThread = null;
        Thread audioThread = null;
        RuntimeException closeError = null;
        masterGrabberLock.writeLock().lock();
        audioGrabberLock.writeLock().lock();

        try {
            if (!isOpen.get() && masterGrabber == null && audioGrabber == null) {
                return;
            }

            isOpen.set(false);
            isClosed.set(true);
            closeRequested.set(true);
            decodeGeneration.incrementAndGet();
            masterThread = masterDecoderThread;
            audioThread = audioDecodeThread;
            clearQueue();
            shouldCloseStreams = true;
        } finally {
            masterGrabberLock.writeLock().unlock();
            audioGrabberLock.writeLock().unlock();
        }

        interruptDecoderThread(masterThread);
        interruptDecoderThread(audioThread);
        awaitDecoderThreadShutdown(masterThread, true);
        awaitDecoderThreadShutdown(audioThread, false);

        masterGrabberLock.writeLock().lock();
        audioGrabberLock.writeLock().lock();
        try {
            closeGrabbersUnsafe();
        } catch (RuntimeException e) {
            closeError = e;
        } finally {
            audioGrabberLock.writeLock().unlock();
            masterGrabberLock.writeLock().unlock();
            if (shouldCloseStreams) {
                videoStream.close();
                audioStream.close();
            }
            isEnded.set(true);
            if (metricsDecoderOpened.compareAndSet(true, false)) {
                DecoderMetrics.tracker().onDecoderClosed();
            }
        }

        if (closeError != null) {
            throw closeError;
        }
    }

    public void startDecoder() {
        isEnded.set(false);
        closeRequested.set(false);

        if (masterGrabber != null && masterDecoderThread == null) {
            Thread thread = new Thread(this::masterDecodeLoop);
            thread.setDaemon(true);
            thread.setName("mcedia-decoder-thread-master");
            thread.start();
            masterDecoderThread = thread;
        }

        if (audioGrabber != null && audioDecodeThread == null) {
            Thread thread = new Thread(this::audioDecodeLoop);
            thread.setDaemon(true);
            thread.setName("mcedia-decoder-thread-audio");
            thread.start();
            audioDecodeThread = thread;
        }
    }

    private FFmpegFrameGrabber buildGrabber(String url, boolean isVideoGrabber) {

        var grabber = new FFmpegFrameGrabber(url);
        if (url.startsWith("http")) {
            StringBuilder headerStrBuilder = new StringBuilder();

            if (mediaDisc.getCustomHeaders() != null) {
                mediaDisc.getCustomHeaders().forEach((k, v) -> headerStrBuilder.append(k).append(": ").append(v).append("\r\n"));
            }

            if ((mediaDisc.getCustomHeaders() == null || !mediaDisc.getCustomHeaders().containsKey("User-Agent")) && config.getUserAgent() != null) {
                headerStrBuilder.append("User-Agent: ").append(config.getUserAgent()).append("\r\n");
            }

            if (mediaDisc.getCookie() != null && !mediaDisc.getCookie().isEmpty() && (mediaDisc.getCustomHeaders() == null || !mediaDisc.getCustomHeaders().containsKey("Cookie"))) {
                headerStrBuilder.append("Cookie: ").append(mediaDisc.getCookie()).append("\r\n");
            }

            grabber.setOption("headers", headerStrBuilder.toString());

            grabber.setOption("reconnect", "1");
            grabber.setOption("reconnect_streamed", "1");
            grabber.setOption("reconnect_delay_max", "5");
            grabber.setOption("timeout", String.valueOf(config.getTimeout()));
            grabber.setOption("rw_timeout", String.valueOf(config.getTimeout()));
        }

        grabber.setOption("buffer_size", String.valueOf(config.getBufferSize()));
        grabber.setOption("probesize", String.valueOf(config.getProbesize()));
        grabber.setOption("analyzeduration", String.valueOf(config.getCacheDuration()));

        if (config.getUseHardwareDecoding()) grabber.setOption("hwaccel", "auto");
        if (isVideoGrabber) {
            grabber.setOption("an", config.getEnableAudio() ? "0" : "1"); // 视频解码禁用音频

            grabber.setOption("vf", "format=rgba");
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            if (config.getMaxVideoWidth() > 0 || config.getMaxVideoHeight() > 0) {
                var width = config.getMaxVideoWidth() > 0 ? config.getMaxVideoWidth() : -1;
                var height = config.getMaxVideoHeight() > 0 ? config.getMaxVideoHeight() : -1;
                grabber.setImageWidth(width);
                grabber.setImageHeight(height);
            }
        } else {
            grabber.setOption("vn", "1"); // 音频解码禁用视频

            if (config.getAudioSampleRate() > 0) {
                grabber.setSampleRate(config.getAudioSampleRate());
            }
        }
        return grabber;
    }


    @Override
    public FrameStream<VideoFrame> getVideoStream() {
        return videoStream;
    }

    @Override
    public FrameStream<AudioFrame> getAudioStream() {
        return audioStream;
    }

    @Override
    public boolean isOpen() {
        return isOpen.get();
    }

    @Override
    public boolean isEnded() {
        return isEnded.get();
    }

    @Override
    public long getDuration() {
        masterGrabberLock.readLock().lock();
        try {
            if (masterGrabber == null) return 0;
            return masterGrabber.getLengthInTime();
        } finally {
            masterGrabberLock.readLock().unlock();
        }
    }

    @Override
    public long getTime() {
        masterGrabberLock.readLock().lock();
        try {
            if (masterGrabber == null) return 0;
            return masterGrabber.getTimestamp();
        } finally {
            masterGrabberLock.readLock().unlock();
        }
    }

    @Override
    public void seek(long time) {
        masterGrabberLock.writeLock().lock();
        audioGrabberLock.writeLock().lock();
        try {
            if (isLiveStream(masterGrabber) || isLiveStream(audioGrabber)) {
                LOGGER.debug("忽略直播流 seek 请求: target={} url={}", time, mediaDisc.getUrl());
                return;
            }
            decodeGeneration.incrementAndGet();
            this.clearQueue();
            try {
                if (masterGrabber != null) {
                    masterGrabber.setTimestamp(Math.clamp(time, 0, masterGrabber.getLengthInTime()), true);
                }
                if (audioGrabber != null) {
                    audioGrabber.setTimestamp(Math.clamp(time, 0, audioGrabber.getLengthInTime()), true);
                }

                startDecoder();
            } catch (FFmpegFrameGrabber.Exception e) {
                LOGGER.warn("Seek 出现异常", e);
            }
        } finally {
            audioGrabberLock.writeLock().unlock();
            masterGrabberLock.writeLock().unlock();
        }
    }

    @Override
    public boolean isLowOverhead() {
        return lowOverhead.get();
    }

    @Override
    public void setLowOverhead(boolean lowOverhead) {
        this.lowOverhead.set(lowOverhead);
    }

    @Override
    public void setRuntimeVideoEnabled(boolean enabled) {
        boolean effectiveEnabled = enabled && config.getEnableVideo();
        runtimeVideoEnabled.set(effectiveEnabled);
        masterGrabberLock.writeLock().lock();
        try {
            applyRuntimeVideoState(masterGrabber);
            if (!effectiveEnabled) {
                videoStream.clear();
            }
        } finally {
            masterGrabberLock.writeLock().unlock();
        }
    }

    @Override
    public void setRuntimeAudioEnabled(boolean enabled) {
        boolean effectiveEnabled = enabled && config.getEnableAudio();
        runtimeAudioEnabled.set(effectiveEnabled);
        if (!effectiveEnabled) {
            audioStream.clear();
        }
    }

    private void clearQueue() {
        getVideoStream().clear();
        getAudioStream().clear();
    }

    private void waitForLowOverheadVideoCapacity() throws InterruptedException {
        while (lowOverhead.get() && videoStream.size() >= LOW_OVERHEAD_MAX_VIDEO_FRAMES) {
            Thread.sleep(1L);
        }
    }

    private boolean isLiveStream(@Nullable FFmpegFrameGrabber grabber) {
        return grabber != null && grabber.getLengthInTime() < 0;
    }

    private void applyRuntimeVideoState(@Nullable FFmpegFrameGrabber grabber) {
        if (grabber != null) {
            FfmpegProcessImageFlags.setProcessImage(grabber, runtimeVideoEnabled.get());
        }
    }

    private void interruptDecoderThread(@Nullable Thread thread) {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    private void awaitDecoderThreadShutdown(@Nullable Thread thread, boolean master) {
        if (thread == null || !thread.isAlive()) {
            return;
        }
        try {
            thread.join(DECODER_THREAD_JOIN_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            LOGGER.warn("等待{}解码线程退出超时: thread={}", master ? "主" : "音频", thread.getName());
        }
    }

    private boolean shouldAcceptFrames(long generation) {
        return !closeRequested.get() && decodeGeneration.get() == generation;
    }

    private void closeGrabbersUnsafe() {
        FrameGrabber.Exception closeError = null;

        if (masterGrabber != null) {
            try {
                FfmpegProcessImageFlags.remove(masterGrabber);
                masterGrabber.close();
            } catch (FrameGrabber.Exception e) {
                closeError = e;
            } finally {
                masterGrabber = null;
            }
        }

        if (audioGrabber != null) {
            try {
                FfmpegProcessImageFlags.remove(audioGrabber);
                audioGrabber.close();
            } catch (FrameGrabber.Exception e) {
                if (closeError == null) {
                    closeError = e;
                } else {
                    closeError.addSuppressed(e);
                }
            } finally {
                audioGrabber = null;
            }
        }

        if (closeError != null) {
            throw new RuntimeException(closeError);
        }
    }

    private void updateEndedStateIfStopped() {
        if (masterDecoderThread == null && audioDecodeThread == null) {
            isEnded.set(true);
        }
    }

    private void masterDecodeLoop() {
        try {
            while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
                long generation = decodeGeneration.get();
                Frame frame;
                long decodeElapsedNanos;
                masterGrabberLock.readLock().lock();
                try {
                    if (masterGrabber == null || isClosed.get()) {
                        break;
                    }

                    long decodeStartNanos = System.nanoTime();
                    frame = masterGrabber.grab();
                    decodeElapsedNanos = System.nanoTime() - decodeStartNanos;

                    if (frame == null) {
                        break;
                    }

                } catch (FFmpegFrameGrabber.Exception e) {
                    if (!isClosed.get()) {
                        LOGGER.warn("视频解码发生异常.", e);
                    }
                    break;
                } finally {
                    masterGrabberLock.readLock().unlock();
                }
                boolean isAudio = frame.samples != null;
                boolean isVideo = frame.image != null;
                if (!shouldAcceptFrames(generation)) {
                    frame.close();
                    continue;
                }

                boolean acceptAudio = isAudio && runtimeAudioEnabled.get();
                boolean acceptVideo = isVideo && runtimeVideoEnabled.get() && config.getEnableVideo();
                if (isVideo) {
                    DecoderMetrics.tracker().onVideoDecodeLatencySample(decodeElapsedNanos);
                } else if (isAudio) {
                    DecoderMetrics.tracker().onAudioDecodeLatencySample(decodeElapsedNanos);
                }

                if (acceptAudio && !acceptVideo) {
                    getAudioStream().put(new FfmpegAudioFrame(frame));
                    continue;
                }

                if (acceptVideo && !acceptAudio) {
                    if (lowOverhead.get()) {
                        waitForLowOverheadVideoCapacity();
                    }
                    videoStream.put(new FfmpegVideoFrame(frame));
                    continue;
                }

                if (acceptAudio) {
                    getAudioStream().put(new FfmpegAudioFrame(frame));
                }

                if (acceptVideo) {
                    if (lowOverhead.get()) {
                        waitForLowOverheadVideoCapacity();
                    }
                    videoStream.put(new FfmpegVideoFrame(frame));
                    continue;
                }

                frame.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("在解码循环中发生未捕获的错误", e);
            }
        } finally {
            masterDecoderThread = null;
            updateEndedStateIfStopped();
        }
    }

    private void audioDecodeLoop() {
        try {
            while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
                long generation = decodeGeneration.get();
                Frame frame;
                FFmpegFrameGrabber currentAudioGrabber;

                masterGrabberLock.readLock().lock();
                audioGrabberLock.readLock().lock();
                try {
                    currentAudioGrabber = audioGrabber;
                    if (currentAudioGrabber == null) {
                        return;
                    }

                    if (isClosed.get()) {
                        break;
                    }

                    long decodeStartNanos = System.nanoTime();
                    frame = currentAudioGrabber.grabSamples();
                    DecoderMetrics.tracker().onAudioDecodeLatencySample(System.nanoTime() - decodeStartNanos);

                } catch (FFmpegFrameGrabber.Exception e) {
                    if (!isClosed.get()) {
                        LOGGER.warn("音频解码发生异常.", e);
                    }
                    break;
                } finally {
                    audioGrabberLock.readLock().unlock();
                    masterGrabberLock.readLock().unlock();
                }
                if (frame == null) {
                    break;
                }

                boolean isAudio = frame.samples != null;
                if (!shouldAcceptFrames(generation)) {
                    frame.close();
                    continue;
                }
                if (isAudio && runtimeAudioEnabled.get()) {
                    audioStream.put(new FfmpegAudioFrame(frame));
                    continue;
                }
                frame.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("在解码循环中发生未捕获的错误", e);
            }
        } finally {
            audioDecodeThread = null;
            updateEndedStateIfStopped();
        }
    }

}

package top.tobyprime.mcedia.decoders.ffmpeg;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.core.MediaInfo;
import top.tobyprime.mcedia.decoders.DecoderConfiguration;
import top.tobyprime.mcedia.interfaces.IAudioData;
import top.tobyprime.mcedia.interfaces.IMediaDecoder;
import top.tobyprime.mcedia.interfaces.IVideoData;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FfmpegMediaDecoder implements Closeable, IMediaDecoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegMediaDecoder.class);

    public final LinkedBlockingDeque<FfmpegVideoData> videoQueue;
    public final LinkedBlockingDeque<FfmpegAudioData> audioQueue;

    private final DecoderConfiguration configuration;
    private final List<Thread> decoderThreads = new ArrayList<>();
    private final List<FFmpegFrameGrabber> grabbers = new ArrayList<>();
    private final FFmpegFrameGrabber primaryGrabber;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ReentrantReadWriteLock grabberLock = new ReentrantReadWriteLock();
    private final AtomicInteger runningDecoders = new AtomicInteger(0);

    public FfmpegMediaDecoder(MediaInfo info, DecoderConfiguration configuration) {
        this.configuration = configuration;

        this.videoQueue = new LinkedBlockingDeque<>(Configs.DECODER_MAX_VIDEO_FRAMES);
        this.audioQueue = new LinkedBlockingDeque<>(Configs.DECODER_MAX_AUDIO_FRAMES);

        primaryGrabber = buildGrabber(info.streamUrl, info.headers, info.cookie, configuration, true);
        grabbers.add(primaryGrabber);

        if (info.audioUrl != null && !info.audioUrl.isEmpty()) {
            FFmpegFrameGrabber audioGrabber = buildGrabber(info.audioUrl, info.headers, info.cookie, configuration, false);
            grabbers.add(audioGrabber);
        }

        try {
            for (FFmpegFrameGrabber grabber : grabbers) {
                grabber.start();

            }

        } catch (Exception e) {
            close();
            throw new RuntimeException(e);
        }
        startDecoder();
    }

    public void startDecoder() {
        if (runningDecoders.get() != 0) {
            return;
        }
        for (FFmpegFrameGrabber grabber : grabbers) {
            runningDecoders.incrementAndGet();
            Thread thread = new Thread(() -> decodeLoop(grabber));
            thread.setName("Mcedia-Decoder-" + (grabbers.indexOf(grabber) == 0 ? "Video" : "Audio"));
            thread.setDaemon(true);
            decoderThreads.add(thread);
            thread.start();
        }
    }

    @Override
    public boolean isLiveStream() {
        return getLength() <= 0 || Double.isInfinite(getLength());
    }

    @Override
    public LinkedBlockingDeque<? extends IVideoData> getVideoQueue() {
        return videoQueue;
    }

    @Override
    public LinkedBlockingDeque<? extends IAudioData> getAudioQueue() {
        return audioQueue;
    }

    private FFmpegFrameGrabber buildGrabber(String url, @Nullable Map<String, String> customHeaders, @Nullable String cookie, DecoderConfiguration configuration, boolean isVideoGrabber) {
        var grabber = new FFmpegFrameGrabber(url);
        if (url.startsWith("http")) {
            StringBuilder headerStrBuilder = new StringBuilder();

            if (customHeaders != null) {
                customHeaders.forEach((k, v) -> headerStrBuilder.append(k).append(": ").append(v).append("\r\n"));
            }

            if ((customHeaders == null || !customHeaders.containsKey("User-Agent")) && configuration.userAgent != null) {
                headerStrBuilder.append("User-Agent: ").append(configuration.userAgent).append("\r\n");
            }

            if (cookie != null && !cookie.isEmpty() && (customHeaders == null || !customHeaders.containsKey("Cookie"))) {
                headerStrBuilder.append("Cookie: ").append(cookie).append("\r\n");
            }

            grabber.setOption("headers", headerStrBuilder.toString());

            grabber.setOption("reconnect", "1");
            grabber.setOption("reconnect_streamed", "1");
            grabber.setOption("reconnect_delay_max", "5");
            grabber.setOption("timeout", String.valueOf(configuration.timeout));
            grabber.setOption("rw_timeout", String.valueOf(configuration.timeout));
        }
        grabber.setOption("buffer_size", String.valueOf(configuration.bufferSize));
        grabber.setOption("probesize", String.valueOf(configuration.probesize));
        grabber.setOption("analyzeduration", "10000000");

        if (configuration.useHardwareDecoding) grabber.setOption("hwaccel", "auto");
        if (isVideoGrabber) {
            grabber.setOption("vn", configuration.enableVideo ? "0" : "1");
            grabber.setOption("vf", "format=rgba");
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        } else {
            grabber.setOption("vn", "1");
            if (configuration.audioSampleRate > 0) {
                grabber.setSampleRate(configuration.audioSampleRate);
            }
        }
        grabber.setAudioChannels(1);
        grabber.setOption("an", configuration.enableAudio ? "0" : "1");
        return grabber;
    }

    private void decodeLoop(FFmpegFrameGrabber grabber) {
        try {
            while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
                grabberLock.readLock().lock();
                try {
                    if (isClosed.get()) {
                        break;
                    }

                    Frame frame = grabber.grab();
                    if (frame == null) {
                        break;
                    }

                    boolean isVideo = frame.image != null && configuration.enableVideo;
                    boolean isAudio = frame.samples != null && configuration.enableAudio;

                    if (isVideo) {
                        videoQueue.put(new FfmpegVideoData(frame));
                    } else if (isAudio) {
                        audioQueue.put(new FfmpegAudioData(frame));
                    }

                } catch (FFmpegFrameGrabber.Exception e) {
                    if (!isClosed.get()) {
                        LOGGER.warn("在 grabber.grab() 期间发生错误，解码循环将终止。", e);
                    }
                    break;
                } finally {
                    grabberLock.readLock().unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("在解码循环中发生未捕获的错误", e);
            }
        } finally {
            runningDecoders.decrementAndGet();
        }
    }

    public boolean isEnded() {
        return runningDecoders.get() == 0;
    }


    public long getLength() {
        return primaryGrabber.getLengthInTime();
    }


    @Override
    public long getDuration() {
        return primaryGrabber.getTimestamp();
    }

    public int getWidth() {
        return primaryGrabber.getImageWidth();
    }

    public int getHeight() {
        return primaryGrabber.getImageHeight();
    }

    @Override
    public int getSampleRate() {
        return 0;
    }

    @Override
    public int getChannels() {
        return 0;
    }

    public void seek(long timestamp) {
        if (getLength() <= 0) return;
        if (runningDecoders.get() == 0) {
            startDecoder();
        }
        timestamp = Math.max(0, Math.min(timestamp, getLength()));

        grabberLock.writeLock().lock();
        try {
            clearQueue();
            for (FFmpegFrameGrabber grabber : grabbers) {
                grabber.setTimestamp(timestamp, true);
            }
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException("Seek failed", e);
        } finally {
            grabberLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }

        decoderThreads.forEach(Thread::interrupt);
        grabberLock.writeLock().lock();
        try {
            for (FFmpegFrameGrabber grabber : grabbers) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    LOGGER.warn("停止或释放 grabber 时出错", e);
                }
            }
        } finally {
            grabberLock.writeLock().unlock();
        }

        for (Thread thread : decoderThreads) {
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
            }
        }

        clearQueue();
    }

    private void clearQueue() {
        videoQueue.forEach(FfmpegVideoData::close);
        audioQueue.forEach(FfmpegAudioData::close);
        videoQueue.clear();
        audioQueue.clear();
    }
}
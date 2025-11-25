package top.tobyprime.mcedia.core;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MediaDecoder implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDecoder.class);

    public final LinkedBlockingDeque<VideoFrame> videoQueue;
    public final LinkedBlockingDeque<Frame> audioQueue;

    private final DecoderConfiguration configuration;
    private final List<Thread> decoderThreads = new ArrayList<>();
    private final List<FFmpegFrameGrabber> grabbers = new ArrayList<>();
    private final FFmpegFrameGrabber primaryGrabber;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private volatile int runningDecoders = 0;

    @Nullable
    private final VideoFramePool videoFramePool;

    private final ReentrantReadWriteLock grabberLock = new ReentrantReadWriteLock();

    public MediaDecoder(VideoInfo info, @Nullable String cookie, DecoderConfiguration configuration, @Nullable VideoFramePool pool, long initialSeekUs) throws FFmpegFrameGrabber.Exception {
        this.configuration = configuration;
        this.videoFramePool = pool;
        this.videoQueue = new LinkedBlockingDeque<>(McediaConfig.getDecoderMaxVideoFrames());
        this.audioQueue = new LinkedBlockingDeque<>(McediaConfig.getDecoderMaxAudioFrames());

        primaryGrabber = buildGrabber(info, info.getVideoUrl(), cookie, configuration, true);
        grabbers.add(primaryGrabber);

        if (info.getAudioUrl() != null && !info.getAudioUrl().isEmpty()) {
            FFmpegFrameGrabber audioGrabber = buildGrabber(info, info.getAudioUrl(), cookie, configuration, false);
            grabbers.add(audioGrabber);
        }

        try {
            for (FFmpegFrameGrabber grabber : grabbers) {
                grabber.start();
                if (initialSeekUs > 0 && grabber.getLengthInTime() > 0) {
                    grabber.setTimestamp(initialSeekUs, true);
                }
            }
        } catch (FFmpegFrameGrabber.Exception e) {
            close();
            throw e;
        }

        for (FFmpegFrameGrabber grabber : grabbers) {
            Thread thread = new Thread(() -> decodeLoop(grabber));
            thread.setName("Mcedia-Decoder-" + (grabbers.indexOf(grabber) == 0 ? "Video" : "Audio"));
            thread.setDaemon(true);
            decoderThreads.add(thread);
            thread.start();
        }
    }

    private FFmpegFrameGrabber buildGrabber(VideoInfo info, String url, @Nullable String cookie, DecoderConfiguration configuration, boolean isVideoGrabber) {
        var grabber = new FFmpegFrameGrabber(url);
        if (url.startsWith("http")) {
            StringBuilder headers = new StringBuilder();
            Map<String, String> customHeaders = info.getHeaders();

            if (customHeaders != null && !customHeaders.isEmpty()) {
                customHeaders.forEach((key, value) -> headers.append(key).append(": ").append(value).append("\r\n"));
            } else {
                headers.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\r\n");
                headers.append("Referer: https://www.bilibili.com/\r\n");
                headers.append("Origin: https://www.bilibili.com\r\n");
            }

            if (cookie != null && !cookie.isEmpty() && (customHeaders == null || !customHeaders.containsKey("Cookie"))) {
                headers.append("Cookie: ").append(cookie).append("\r\n");
            }

            grabber.setOption("headers", headers.toString());
            grabber.setOption("reconnect", "1");
            grabber.setOption("reconnect_streamed", "1");
            grabber.setOption("reconnect_delay_max", "5");
            grabber.setOption("timeout", String.valueOf(configuration.timeout));
        }
        grabber.setOption("buffer_size", String.valueOf(configuration.bufferSize));
        grabber.setOption("probesize", String.valueOf(configuration.probesize));
        grabber.setOption("analyzeduration", "10000000");
        if (configuration.useHardwareDecoding) grabber.setOption("hwaccel", "auto");
        if (isVideoGrabber) {
            grabber.setOption("vn", configuration.enableVideo ? "0" : "1");
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        } else {
            grabber.setOption("vn", "1");
        }
        grabber.setAudioChannels(1);
        grabber.setOption("an", configuration.enableAudio ? "0" : "1");
        return grabber;
    }

    private void decodeLoop(FFmpegFrameGrabber grabber) {
        runningDecoders++;
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
                        if (videoFramePool != null) {
                            int width = frame.imageWidth;
                            int height = frame.imageHeight;
                            int stride = frame.imageStride;
                            int tightStride = width * 4;

                            ByteBuffer rawBuffer = (ByteBuffer) frame.image[0];

                            VideoFramePool finalPool = videoFramePool;
                            ByteBuffer copiedBuffer = videoFramePool.acquire();

                            if (copiedBuffer.capacity() < tightStride * height) {
                                videoFramePool.release(copiedBuffer);
                                copiedBuffer = MemoryUtil.memAlloc(tightStride * height);
                                finalPool = null;
                            }

                            long rawBufferAddress = MemoryUtil.memAddress(rawBuffer);
                            long copiedBufferAddress = MemoryUtil.memAddress(copiedBuffer);

                            if (stride == tightStride) {
                                MemoryUtil.memCopy(rawBufferAddress, copiedBufferAddress, (long)tightStride * height);
                            } else {
                                for (int y = 0; y < height; y++) {
                                    long sourceAddress = rawBufferAddress + (long)y * stride;
                                    long destAddress = copiedBufferAddress + (long)y * tightStride;
                                    MemoryUtil.memCopy(sourceAddress, destAddress, tightStride);
                                }
                            }

                            copiedBuffer.limit(tightStride * height);
                            copiedBuffer.rewind();

                            VideoFrame videoFrame = new VideoFrame(copiedBuffer, width, height, frame.timestamp, finalPool);
                            try {
                                videoQueue.put(videoFrame);
                            } catch (InterruptedException e) {
                                videoFrame.close();
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } else if (isAudio) {
                        Frame audioFrame = frame.clone();
                        try {
                            audioQueue.put(audioFrame);
                        } catch (InterruptedException e) {
                            audioFrame.close();
                            Thread.currentThread().interrupt();
                            break;
                        }
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
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("在解码循环中发生未捕获的错误", e);
            }
        } finally {
            runningDecoders--;
        }
    }

    public static void prewarm() {
        LOGGER.info("开始预热 FFmpeg 原生库...");
        int oldLevel = avutil.av_log_get_level();
        try {
            avutil.av_log_set_level(avutil.AV_LOG_QUIET);
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("prewarm");
            grabber.close();
            LOGGER.info("FFmpeg 原生库预热成功。");
        } catch (Exception e) {
            LOGGER.error("预热 FFmpeg 失败。", e);
        } finally {
            avutil.av_log_set_level(oldLevel);
        }
    }

    public boolean isEof() {
        return runningDecoders == 0;
    }

    public long getDuration() {
        return primaryGrabber.getLengthInTime();
    }

    public int getWidth() {
        return primaryGrabber.getImageWidth();
    }

    public int getHeight() {
        return primaryGrabber.getImageHeight();
    }

    public void seek(long timestamp) {
        if (getDuration() <= 0) return;
        timestamp = Math.max(0, Math.min(timestamp, getDuration()));

        grabberLock.writeLock().lock();
        try {
            clearQueue();
            for (FFmpegFrameGrabber grabber : grabbers) {
                grabber.setTimestamp(timestamp, true);
            }
        } catch (FFmpegFrameGrabber.Exception e) {
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
            } catch (InterruptedException ignored) {}
        }

        clearQueue();
        if (videoFramePool != null) {
            videoFramePool.close();
        }
    }

    private void clearQueue() {
        VideoFrame vf;
        while ((vf = videoQueue.poll()) != null) {
            vf.close();
        }
        Frame f;
        while ((f = audioQueue.poll()) != null) {
            f.close();
        }
    }
}
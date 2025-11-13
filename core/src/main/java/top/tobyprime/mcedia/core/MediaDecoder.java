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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

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

    private final ReentrantLock decodeLock = new ReentrantLock();
    private final AtomicBoolean decodingPaused = new AtomicBoolean(false);

    @Nullable
    private VideoFramePool videoFramePool;

    public MediaDecoder(VideoInfo info, @Nullable String cookie, DecoderConfiguration configuration, @Nullable VideoFramePool pool, long initialSeekUs) throws FFmpegFrameGrabber.Exception {
        this.configuration = configuration;
        this.videoFramePool = pool;
        this.videoQueue = new LinkedBlockingDeque<>(McediaConfig.DECODER_MAX_VIDEO_FRAMES);
        this.audioQueue = new LinkedBlockingDeque<>(McediaConfig.DECODER_MAX_AUDIO_FRAMES);

        primaryGrabber = buildGrabber(info, info.getVideoUrl(), cookie, configuration, true);
        grabbers.add(primaryGrabber);

        if (info.getAudioUrl() != null && !info.getAudioUrl().isEmpty()) {
            FFmpegFrameGrabber audioGrabber = buildGrabber(info, info.getAudioUrl(), cookie, configuration, false);
            grabbers.add(audioGrabber);
        }

        try {
            for (FFmpegFrameGrabber grabber : grabbers) {
                grabber.start();
                if (configuration.useHardwareDecoding && grabbers.indexOf(grabber) == 0) {
                    LOGGER.info("硬件解码模式: {}", grabber.getVideoCodecName());
                }
                if (initialSeekUs > 0 && grabber.getLengthInTime() > 0) {
                    LOGGER.debug("为 grabber 设置初始时间戳: {} us", initialSeekUs);
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
                LOGGER.debug("为 {} 应用自定义请求头...", url);
                customHeaders.forEach((key, value) -> headers.append(key).append(": ").append(value).append("\r\n"));
            } else {
                LOGGER.debug("为 {} 应用默认 Bilibili 请求头...", url);
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
            grabber.setOption("reconnect_on_http_error", "4xx,5xx");
            grabber.setOption("http_persistent", "1");
            grabber.setOption("http_multiple", "1");
            grabber.setOption("timeout", String.valueOf(configuration.timeout));
            grabber.setOption("rw_timeout", String.valueOf(configuration.timeout));
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
                if (decodingPaused.get()) {
                    decodeLock.lock();
                    decodeLock.unlock();
                }
                Frame frame;
                try {
                    synchronized (this) {
                        if (isClosed.get()) break;
                        frame = grabber.grab();
                    }
                } catch (FFmpegFrameGrabber.Exception e) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }

                if (frame == null) break;
                if (isClosed.get()) break;

                boolean isVideo = frame.image != null && configuration.enableVideo;
                boolean isAudio = frame.samples != null && configuration.enableAudio;

                if (isVideo) {
                    if (videoFramePool == null) continue;

                    int width = frame.imageWidth;
                    int height = frame.imageHeight;
                    int stride = frame.imageStride;
                    int tightStride = width * 4;

                    ByteBuffer rawBuffer = (ByteBuffer) frame.image[0];
                    long rawBufferAddress = MemoryUtil.memAddress(rawBuffer);

                    ByteBuffer copiedBuffer = videoFramePool.acquire();
                    if (copiedBuffer.capacity() < tightStride * height) {
                        videoFramePool.release(copiedBuffer);
                        copiedBuffer = MemoryUtil.memAlloc(tightStride * height);
                    }
                    long copiedBufferAddress = MemoryUtil.memAddress(copiedBuffer);

                    if (stride == tightStride) {
                        // 如果内存是连续的（没有padding），我们可以进行一次性的、最高效的复制
                        MemoryUtil.memCopy(rawBufferAddress, copiedBufferAddress, (long)tightStride * height);
                    } else {
                        // 如果内存不是连续的（例如，每行末尾有填充字节），我们必须逐行复制有效数据
                        for (int y = 0; y < height; y++) {
                            long sourceAddress = rawBufferAddress + (long)y * stride;
                            long destAddress = copiedBufferAddress + (long)y * tightStride;
                            MemoryUtil.memCopy(sourceAddress, destAddress, tightStride);
                        }
                    }
                    // --- 结束修复 ---

                    // 设置新buffer的limit并重置position，准备放入队列
                    copiedBuffer.limit(tightStride * height);
                    copiedBuffer.rewind();

                    // 将复制了安全数据的VideoFrame放入待处理队列
                    videoQueue.put(new VideoFrame(copiedBuffer, width, height, frame.timestamp, videoFramePool));

                } else if (isAudio) {
                    // 音频帧直接克隆，因为它们通常不涉及复杂的本地内存问题
                    audioQueue.put(frame.clone());
                }
            }
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("在解码循环中发生错误", e);
            }
        } finally {
            runningDecoders--;
        }
    }

    public void pauseDecoding() {
        if (decodingPaused.compareAndSet(false, true)) {
            decodeLock.lock(); // 获取锁，让解码线程在下一次循环时阻塞
        }
    }

    public void resumeDecoding() {
        if (decodingPaused.compareAndSet(true, false)) {
            decodeLock.unlock(); // 释放锁，让解码线程继续运行
        }
    }

    public boolean isEof() {
        return runningDecoders == 0;
    }

    public boolean isEnded() {
        return isEof() && audioQueue.isEmpty() && videoQueue.isEmpty();
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

    public int getSampleRate() {
        return primaryGrabber.getSampleRate();
    }

    public int getChannels() {
        return primaryGrabber.getAudioChannels();
    }

    public void seek(long timestamp) {
        if (getDuration() <= 0) return;
        timestamp = Math.max(0, Math.min(timestamp, getDuration()));
        clearQueue();
        try {
            synchronized (this) {
                for (FFmpegFrameGrabber grabber : grabbers) {
                    grabber.setTimestamp(timestamp, true);
                }
            }
            LOGGER.debug("FFmpeg grabbers have been seeked to {} us.", timestamp);
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new RuntimeException("Seek failed", e);
        }
    }

    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        decoderThreads.forEach(Thread::interrupt);
        synchronized (this) {
            for (FFmpegFrameGrabber grabber : grabbers) {
                try {
                    grabber.stop();
                } catch (Exception e) {
                    LOGGER.warn("停止 grabber 时出错", e);
                }
            }
        }
        for (Thread thread : decoderThreads) {
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
            }
        }
        clearQueue();
        if (videoFramePool != null) {
            videoFramePool.close();
        }
        new Thread(() -> {
            for (FFmpegFrameGrabber grabber : grabbers) {
                try {
                    grabber.release();
                } catch (Exception e) {
                    LOGGER.error("释放 grabber 时出错", e);
                }
            }
            clearQueue();
        }, "Mcedia-Grabber-Closer").start();
    }

    private void clearQueue() {
        videoQueue.forEach(VideoFrame::close);
        audioQueue.forEach(Frame::close);
        videoQueue.clear();
        audioQueue.clear();
    }
}
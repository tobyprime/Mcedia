package top.tobyprime.mcedia.core;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MediaDecoder implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDecoder.class);

    private static final int MAX_VIDEO_FRAMES = 120;
    private static final int MAX_AUDIO_FRAMES = 1024;

    public final LinkedBlockingDeque<VideoFrame> videoQueue = new LinkedBlockingDeque<>(MAX_VIDEO_FRAMES);
    public final LinkedBlockingDeque<Frame> audioQueue = new LinkedBlockingDeque<>(MAX_AUDIO_FRAMES);

    private final DecoderConfiguration configuration;
    private final List<Thread> decoderThreads = new ArrayList<>();
    private final List<FFmpegFrameGrabber> grabbers = new ArrayList<>();
    private final FFmpegFrameGrabber primaryGrabber;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean isSeeking = new AtomicBoolean(false);
    private final AtomicLong seekTargetUs = new AtomicLong(-1);

    private volatile int runningDecoders = 0;

    @Nullable
    private VideoFramePool videoFramePool;

    public MediaDecoder(VideoInfo info, @Nullable String cookie, DecoderConfiguration configuration, @Nullable VideoFramePool pool, long initialSeekUs) throws FFmpegFrameGrabber.Exception {
        this.configuration = configuration;
        this.videoFramePool = pool;

        // [关键修改] 将 VideoInfo 传递给 buildGrabber
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

    // [关键修改] buildGrabber 新增 VideoInfo 参数
    private FFmpegFrameGrabber buildGrabber(VideoInfo info, String url, @Nullable String cookie, DecoderConfiguration configuration, boolean isVideoGrabber) {
        var grabber = new FFmpegFrameGrabber(url);
        if (url.startsWith("http")) {
            StringBuilder headers = new StringBuilder();
            Map<String, String> customHeaders = info.getHeaders();

            if (customHeaders != null && !customHeaders.isEmpty()) {
                // 如果 VideoInfo 提供了自定义 headers，就使用它们
                LOGGER.debug("为 {} 应用自定义请求头...", url);
                customHeaders.forEach((key, value) -> headers.append(key).append(": ").append(value).append("\r\n"));
            } else {
                // 否则，使用默认的 Bilibili 请求头 (保持兼容性)
                LOGGER.debug("为 {} 应用默认 Bilibili 请求头...", url);
                headers.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\r\n");
                headers.append("Referer: https://www.bilibili.com/\r\n");
                headers.append("Origin: https://www.bilibili.com\r\n");
            }

            // 如果 cookie 存在且自定义 headers 中没有 Cookie，则添加
            if (cookie != null && !cookie.isEmpty() && (customHeaders == null || !customHeaders.containsKey("Cookie"))) {
                headers.append("Cookie: ").append(cookie).append("\r\n");
            }

            grabber.setOption("headers", headers.toString());
            grabber.setOption("reconnect", "1");
            grabber.setOption("reconnect_streamed", "1");
            grabber.setOption("reconnect_delay_max", "5");
        }
        grabber.setOption("timeout", String.valueOf(configuration.timeout));
        grabber.setOption("rw_timeout", String.valueOf(configuration.timeout));
        grabber.setOption("buffer_size", String.valueOf(configuration.bufferSize));
        grabber.setOption("probesize", String.valueOf(configuration.probesize));
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

    // decodeLoop 和其他方法保持不变...
    private void decodeLoop(FFmpegFrameGrabber grabber) {
        runningDecoders++;

        try {
            while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
                Frame frame;
                try {
                    frame = grabber.grab();
                } catch (FFmpegFrameGrabber.Exception e) {
                    try { Thread.sleep(50); } catch (InterruptedException interruptedException) { Thread.currentThread().interrupt(); }
                    continue;
                }

                if (frame == null) {
                    break;
                }

                if (isSeeking.get()) {
                    long target = seekTargetUs.get();
                    if (target != -1 && frame.timestamp < target) {
                        continue;
                    } else {
                        isSeeking.set(false);
                        seekTargetUs.set(-1);
                        LOGGER.debug("Seek target reached. Resuming normal decoding.");
                    }
                }

                if (isClosed.get()) {
                    break;
                }

                boolean isVideo = frame.image != null && configuration.enableVideo;
                boolean isAudio = frame.samples != null && configuration.enableAudio;

                if (isVideo) {
                    if (videoFramePool == null) continue;

                    int width = frame.imageWidth;
                    int height = frame.imageHeight;
                    int stride = frame.imageStride;

                    ByteBuffer rawBuffer = (ByteBuffer) frame.image[0];
                    rawBuffer.rewind();

                    int tightStride = width * 4;
                    ByteBuffer copiedBuffer = videoFramePool.acquire();
                    if (copiedBuffer.capacity() < tightStride * height) {
                        videoFramePool.release(copiedBuffer);
                        copiedBuffer = MemoryUtil.memAlloc(tightStride * height);
                    }
                    copiedBuffer.limit(tightStride * height);

                    if (stride == tightStride) {
                        if (rawBuffer.remaining() < tightStride * height) {
                            LOGGER.warn("视频帧数据大小不足，期望 {}，实际 {}。将截断复制。", tightStride * height, rawBuffer.remaining());
                            rawBuffer.limit(rawBuffer.position() + Math.min(rawBuffer.remaining(), copiedBuffer.remaining()));
                        }
                        copiedBuffer.put(rawBuffer);
                    } else {
                        for (int y = 0; y < height; y++) {
                            int rowStart = y * stride;
                            if (rowStart >= rawBuffer.limit() || rowStart + tightStride > rawBuffer.limit()) {
                                LOGGER.warn("检测到损坏或不规范的视频帧 (行: {}, stride: {})，该行数据超出缓冲区有效数据范围 (limit: {})。跳过此帧。", y, stride, rawBuffer.limit());
                                copiedBuffer.limit(0);
                                break;
                            }
                            rawBuffer.position(rowStart);
                            rawBuffer.limit(rowStart + tightStride);
                            copiedBuffer.put(rawBuffer);
                            rawBuffer.limit(rawBuffer.capacity());
                        }
                    }

                    if (copiedBuffer.limit() > 0) {
                        copiedBuffer.rewind();
                        videoQueue.put(new VideoFrame(copiedBuffer, width, height, frame.timestamp, videoFramePool));
                    } else {
                        videoFramePool.release(copiedBuffer);
                    }
                    rawBuffer.clear();
                } else if (isAudio) {
                    audioQueue.put(frame.clone());
                }
            }
        } catch (Exception e) {
            if (!isClosed.get()) LOGGER.error("Error in decoder loop", e);
        } finally {
            runningDecoders--;
        }
    }

    public boolean isEof() {
        return runningDecoders == 0;
    }

    public boolean isEnded() {
        return isEof() && audioQueue.isEmpty() && videoQueue.isEmpty();
    }
    public long getDuration() { return primaryGrabber.getLengthInTime(); }
    public int getWidth() { return primaryGrabber.getImageWidth(); }
    public int getHeight() { return primaryGrabber.getImageHeight(); }
    public int getSampleRate() { return primaryGrabber.getSampleRate(); }
    public int getChannels() { return primaryGrabber.getAudioChannels(); }
    public void seek(long timestamp) {
        if (getDuration() <= 0) return;
        timestamp = Math.max(0, timestamp);
        timestamp = Math.min(timestamp, getDuration());
        clearQueue();
        this.seekTargetUs.set(timestamp);
        this.isSeeking.set(true);
        try {
            synchronized (this) {
                for (FFmpegFrameGrabber grabber : grabbers) {
                    grabber.setTimestamp(timestamp, true);
                }
            }
        } catch (FFmpegFrameGrabber.Exception e) {
            this.isSeeking.set(false);
            this.seekTargetUs.set(-1);
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
                    LOGGER.warn("Error stopping grabber", e);
                }
            }
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
        new Thread(() -> {
            for (FFmpegFrameGrabber grabber : grabbers) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    LOGGER.error("Error stopping or releasing grabber", e);
                }
            }

            for (Thread thread : decoderThreads) {
                try {
                    thread.join(200);
                } catch (InterruptedException ignored) {}
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
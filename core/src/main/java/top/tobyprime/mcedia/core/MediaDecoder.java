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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MediaDecoder implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDecoder.class);

    private static final int MAX_VIDEO_FRAMES = 120;
    private static final int MAX_AUDIO_FRAMES = 1024;
    private static final int VIDEO_BUFFER_CAPACITY = 120; // 预缓冲120帧视频
    private static final int AUDIO_BUFFER_CAPACITY = 1024; // 预缓冲1024帧音频

    public final LinkedBlockingDeque<VideoFrame> videoQueue = new LinkedBlockingDeque<>(MAX_VIDEO_FRAMES);
    public final LinkedBlockingDeque<Frame> audioQueue = new LinkedBlockingDeque<>(MAX_AUDIO_FRAMES);

    private final DecoderConfiguration configuration;
    private final List<Thread> decoderThreads = new ArrayList<>();
    private final List<FFmpegFrameGrabber> grabbers = new ArrayList<>();
    private final FFmpegFrameGrabber primaryGrabber;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean isSeeking = new AtomicBoolean(false);

    private volatile int runningDecoders = 0;

    @Nullable
    private VideoFramePool videoFramePool;

    public MediaDecoder(VideoInfo info, @Nullable String cookie, DecoderConfiguration configuration, @Nullable VideoFramePool pool, long initialSeekUs) throws FFmpegFrameGrabber.Exception {
        this.configuration = configuration;
        this.videoFramePool = pool;

        primaryGrabber = buildGrabber(info.getVideoUrl(), cookie, configuration, true);
        grabbers.add(primaryGrabber);

        if (info.getAudioUrl() != null && !info.getAudioUrl().isEmpty()) {
            FFmpegFrameGrabber audioGrabber = buildGrabber(info.getAudioUrl(), cookie, configuration, false);
            grabbers.add(audioGrabber);
        }

        try {
            // 在这里完成启动和跳转
            for (FFmpegFrameGrabber grabber : grabbers) {
                grabber.start();
                if (configuration.useHardwareDecoding && grabbers.indexOf(grabber) == 0) {
                    LOGGER.info("硬件解码模式: {}", grabber.getVideoCodecName());
                }
                // 只有当提供了大于0的跳转时间，并且当前流不是直播时，才执行跳转
                if (initialSeekUs > 0 && grabber.getLengthInTime() > 0) {
                    LOGGER.debug("为 grabber 设置初始时间戳: {} us", initialSeekUs);
                    grabber.setTimestamp(initialSeekUs, true);
                }
            }
        } catch (FFmpegFrameGrabber.Exception e) {
            // 如果在启动或跳转时出错，确保所有资源都被清理
            close();
            throw e;
        }

        // 在所有 grabber 都准备就绪后，再启动解码线程
        for (FFmpegFrameGrabber grabber : grabbers) {
            Thread thread = new Thread(() -> decodeLoop(grabber));
            thread.setName("Mcedia-Decoder-" + (grabbers.indexOf(grabber) == 0 ? "Video" : "Audio"));
            thread.setDaemon(true);
            decoderThreads.add(thread);
            thread.start();
        }
    }

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

                // 在 grab() 之后，检查 seek 标志
                // compareAndSet 是原子操作，能确保这个逻辑块只在 seek 后被执行一次
                if (this.isSeeking.compareAndSet(true, false)) {
                    // 这是 seek 后的第一帧！现在是清空旧队列的最佳时机。
                    LOGGER.debug("Seek 完成，已抓取到新时间点的第一帧，正在清理旧队列...");
                    clearQueue();
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

                    // 分配一块新的、独立的、大小正好的堆外内存
                    ByteBuffer copiedBuffer = videoFramePool.acquire();
                    if (copiedBuffer.capacity() < tightStride * height) {
                        // 内存池的块不够大，释放它并创建一个足够大的
                        videoFramePool.release(copiedBuffer);
                        copiedBuffer = MemoryUtil.memAlloc(tightStride * height);
                    }
                    copiedBuffer.limit(tightStride * height);

                    // 检查解码器输出的跨距是否已经合适
                    if (stride == tightStride) {
                        // 如果跨度一致，直接进行一次性的、最高效的内存块复制
                        copiedBuffer.put(rawBuffer);
                    } else {
                        // 如果跨度不一致，采用高效的逐行 ByteBuffer 复制
                        for (int y = 0; y < height; y++) {
                            // 设置源缓冲区的读取范围，使其精确地指向当前行的数据
                            rawBuffer.position(y * stride);
                            rawBuffer.limit(y * stride + tightStride);
                            // 将这一行的数据直接复制到目标缓冲区
                            copiedBuffer.put(rawBuffer);
                        }
                    }

                    copiedBuffer.rewind();

                    // 将这个内存安全的 VideoFrame 对象放入队列
                    VideoFrame videoFrame = new VideoFrame(copiedBuffer, width, height, frame.timestamp, videoFramePool);
                    videoQueue.put(videoFrame);
                } else if (isAudio) {
                    // 对于音频，javacv的Frame.clone()是安全的，因为它会创建新的数组
                    audioQueue.put(frame.clone());
                }
                // grabber返回的原始frame可以被内部重用了，我们已经复制了我们需要的数据
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("Error in decoder loop", e);
            }
        } finally {
            runningDecoders--;
        }
    }

    private FFmpegFrameGrabber buildGrabber(String url, @Nullable String cookie, DecoderConfiguration configuration, boolean isVideoGrabber) {
        var grabber = new FFmpegFrameGrabber(url);
        if (url.startsWith("http")) {
            StringBuilder headers = new StringBuilder();
            headers.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\r\n");
            headers.append("Referer: https://www.bilibili.com/\r\n");
            headers.append("Origin: https://www.bilibili.com\r\n");
            if (cookie != null && !cookie.isEmpty()) {
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
        // clearQueue();
        this.isSeeking.set(true);
        try {
            // seek操作也应该同步，防止与grab()冲突
            synchronized (this) {
                for (FFmpegFrameGrabber grabber : grabbers) {
                    grabber.setTimestamp(timestamp, true);
                }
            }
        } catch (FFmpegFrameGrabber.Exception e) {
            this.isSeeking.set(false);
            throw new RuntimeException("Seek failed", e);
        }
    }

    @Override
    public void close() {
        // 使用 compareAndSet 确保 close 逻辑只执行一次
        if (!isClosed.compareAndSet(false, true)) {
            return; // 已经被其他线程关闭了
        }

        // 中断所有解码线程，让它们从 grab() 或 sleep() 中醒来并退出循环
        decoderThreads.forEach(Thread::interrupt);

        // 立即停止 grabber，这会让阻塞在 grab() 的线程快速返回
        // 必须与 grab() 调用同步
        synchronized (this) {
            for (FFmpegFrameGrabber grabber : grabbers) {
                try {
                    grabber.stop();
                } catch (Exception e) {
                    LOGGER.warn("Error stopping grabber", e);
                }
            }
        }

        // 等待所有解码线程完全终止
        for (Thread thread : decoderThreads) {
            try {
                thread.join(500); // 设置一个超时，以防万一
            } catch (InterruptedException ignored) {}
        }

        // 此时可以保证没有任何线程在使用内存池或队列了，现在清理资源是安全的
        clearQueue();
        if (videoFramePool != null) {
            videoFramePool.close();
        }

        // 在新线程中释放 grabber 的底层资源
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
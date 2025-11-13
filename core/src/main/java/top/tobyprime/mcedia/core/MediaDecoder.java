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

    // [最终方案] 使用读写锁来保护对 grabber 的原生调用
    private final ReentrantReadWriteLock grabberLock = new ReentrantReadWriteLock();

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

        // 强制禁用硬件解码，确保稳定性
        // if (configuration.useHardwareDecoding) grabber.setOption("hwaccel", "auto");

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
                Frame frame = null;

                // [核心修复] 在抓取帧前后获取和释放读锁
                grabberLock.readLock().lock();
                try {
                    if (isClosed.get()) break;
                    frame = grabber.grab();
                } catch (FFmpegFrameGrabber.Exception e) {
                    // 忽略抓取过程中的错误，这可能是流的正常结束或网络问题
                } finally {
                    grabberLock.readLock().unlock();
                }

                if (frame == null) {
                    // grab() 返回 null 或抛出异常都意味着流结束或不可用
                    break;
                }

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
                    videoQueue.put(new VideoFrame(copiedBuffer, width, height, frame.timestamp, videoFramePool));

                } else if (isAudio) {
                    audioQueue.put(frame.clone());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed.get()) LOGGER.error("在解码循环中发生未捕获的错误", e);
        } finally {
            runningDecoders--;
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

        // [核心修复] seek 是一个“写”操作，需要获取写锁
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

        // [核心修复] 获取写锁来安全地关闭 grabber，这会等待所有 grab() 操作完成
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
                thread.join(500); // 等待线程终止
            } catch (InterruptedException ignored) {}
        }

        clearQueue();
        if (videoFramePool != null) {
            videoFramePool.close();
        }
    }

    private void clearQueue() {
        videoQueue.forEach(VideoFrame::close);
        audioQueue.forEach(Frame::close);
        videoQueue.clear();
        audioQueue.clear();
    }
}
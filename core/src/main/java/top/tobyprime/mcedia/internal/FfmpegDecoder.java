package top.tobyprime.mcedia.internal;


import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.PlayConfiguration;

import java.io.Closeable;
import java.util.concurrent.LinkedBlockingQueue;

public class FfmpegDecoder implements Closeable {
    private final static Logger LOGGER = LoggerFactory.getLogger(FfmpegDecoder.class);
    private final LinkedBlockingQueue<TimedFrame> videoQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<TimedFrame> audioQueue = new LinkedBlockingQueue<>();
    private FFmpegFrameGrabber grabber;
    private PlayConfiguration config;
    private Thread grabberThread;
    private volatile boolean running = false;

    private void stopGrabberIfNeed() {
        if (grabber != null) {
            try {
                grabber.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void initGrabber() {
        LOGGER.info("[{}] 初始化播放 '{}'", this.hashCode(), config.getInputUrl());
        grabber = new FFmpegFrameGrabber(config.getInputUrl());
        grabber.setOption("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");
        grabber.setOption("reconnect", "1");
        grabber.setOption("reconnect_streamed", "1");
        grabber.setOption("reconnect_delay_max", "5");

        grabber.setOption("timeout", "5000000"); // 连接超时 5s
        grabber.setOption("rw_timeout", "5000000"); // 读写超时

        grabber.setOption("buffer_size", "1048576"); // 1MB 缓冲
//         不设置max_delay，避免最后一秒丢帧
        grabber.setOption("max_delay", "500000"); // 最大延迟 0.5s
        grabber.setOption("flags", "low_delay");
        grabber.setOption("flush_packets", "1");

        int videoBufferSizeBytes = Math.max(config.getVideoBufferSize() * 1024 * 1024, 4 * 1024 * 1024); // 至少4MB

        grabber.setVideoOption("buffer_size", String.valueOf(videoBufferSizeBytes));
        grabber.setAudioOption("buffer_size", String.valueOf(config.getAudioBufferSize() * 1024 * 1024));
        grabber.setSampleRate(config.getAudioSampleRate());
        grabber.setAudioChannels(config.isAudioDualChannel() ? 2 : 1);
        // 硬件解码可选项
        if (config.isVideoAlpha()) {
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        } else {
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGB24);

        }
        if (config.isUseHardwareDecoding()) {
            grabber.setOption("hwaccel", "auto");
        }
        // 只启用需要的流 
        grabber.setOption("vn", config.isEnableVideo() ? "0" : "1");
        grabber.setOption("an", config.isEnableAudio() ? "0" : "1");
        LOGGER.info("[{}] 初始化完成", this.hashCode());

    }

    public void load(PlayConfiguration decoderConfiguration) {
        this.config = decoderConfiguration;
        stopGrabberIfNeed();
        initGrabber();
    }

    private void processFrame(Frame frame) throws InterruptedException {
        long ptsUs = frame.timestamp;

        if (frame.image != null && config.isEnableVideo()) {
            while (videoQueue.size() >= config.getMaxVideoQueueLength() && running) {
                Thread.sleep(10);
            }
            videoQueue.offer(new TimedFrame(frame.clone(), ptsUs));
        } else if (frame.samples != null && config.isEnableAudio()) {
            while (audioQueue.size() >= config.getMaxAudioQueueLength() && running) {
                Thread.sleep(10);
            }
            audioQueue.offer(new TimedFrame(frame.clone(), ptsUs));
        }
    }

    public void start() throws FFmpegFrameGrabber.Exception {
        if (grabber == null) throw new IllegalStateException("Grabber not loaded");
        if (running) {
            LOGGER.warn("[{}] 已经启动", this.hashCode());
            return;
        }
        running = true;

        // 启动grabber线程 - 负责从媒体源获取帧并直接创建TimedFrame
        grabber.start();
        grabberThread = new Thread(() -> {
            try {
                LOGGER.info("[{}] Grabber 线程启动", this.hashCode());
                final int maxNullTries = 100;      // 放宽 null 尝试次数
                final long nullRetryInterval = 10; // 每次等待间隔
                final long nullWaitTimeMs = maxNullTries * nullRetryInterval;

                long nullStartTime = 0;

                while (running && !Thread.currentThread().isInterrupted()) {
                    Frame frame = grabber.grab();
                    if (frame != null) {
                        // 有效帧，重置 null 状态
                        nullStartTime = 0;

                        // 分类处理 frame
                        processFrame(frame);
                        frame.close();
                        continue;
                    }

                    // 记录第一次遇到 null 的时间
                    if (nullStartTime == 0) nullStartTime = System.currentTimeMillis();

                    // 检查是否超时
                    if (System.currentTimeMillis() - nullStartTime > nullWaitTimeMs) {
                        LOGGER.warn("[{}] grabber 超过 {}ms 均返回 null，退出", this.hashCode(), nullWaitTimeMs);
                        return;
                    } else {
                        // 等待一小段时间后继续
                        Thread.sleep(nullRetryInterval);
                    }
                }

            } catch (Exception e) {
                LOGGER.error("[{}] Grabber 线程异常", this.hashCode(), e);
            } finally {
                LOGGER.info("[{}] Grabber 线程退出", this.hashCode());
                try {
                    grabber.stop();
                } catch (Exception ignored) {
                }
                running = false;
            }
        }, "Decoder-Grabber-Thread");
        grabberThread.start();
    }

    public void stop() {
        LOGGER.info("[{}] 停止播放", this.hashCode());
        running = false;

        // 停止grabber线程
        if (grabberThread != null) {
            grabberThread.interrupt();
            try {
                grabberThread.join();
            } catch (InterruptedException ignored) {
            }
            grabberThread = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public LinkedBlockingQueue<TimedFrame> getVideoQueue() {
        return videoQueue;
    }

    public LinkedBlockingQueue<TimedFrame> getAudioQueue() {
        return audioQueue;
    }

    public void close() {
        LOGGER.info("[{}] 关闭", this.hashCode());
        stop();
        if (grabber != null) {
            try {
                grabber.release();
            } catch (Exception ignored) {
            }
            grabber = null;
        }
        videoQueue.clear();
        audioQueue.clear();
    }

    public int getWidth() {
        return grabber.getImageWidth();
    }

    public int getHeight() {
        return grabber.getImageHeight();
    }

    public double getAspectRatio() {
        return (double) getWidth() / (double) getHeight();
    }
}

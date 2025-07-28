package top.tobyprime.mcedia.core;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

public class MediaDecoder implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDecoder.class);
    public final String inputUrl;
    public final LinkedBlockingDeque<Frame> videoQueue = new LinkedBlockingDeque<>();
    public final LinkedBlockingDeque<Frame> audioQueue = new LinkedBlockingDeque<>();
    private final FFmpegFrameGrabber grabber;
    private final Thread decoderThread;
    private final DecoderConfiguration configuration;
    private volatile boolean decodeEnded = false;

    public MediaDecoder(String inputUrl, DecoderConfiguration configuration) throws FFmpegFrameGrabber.Exception {
        this.configuration = configuration;
        this.inputUrl = inputUrl;

        this.grabber = buildGrabber();
        this.grabber.start();
        this.decoderThread = new Thread(() -> {
            LOGGER.info("解码线程启动");
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Frame frame = grabber.grab();
                    // 如果 eof 则一直等待
                    if (frame == null) {
                        if (!decodeEnded) {
                            LOGGER.info("EOF");
                        }
                        decodeEnded = true;
                        Thread.sleep(10);
                        continue;
                    }
                    processFrame(frame.clone());
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                LOGGER.warn("解码异常", e);
            } finally {
                LOGGER.info("解码线程退出");
            }
        });
        this.decoderThread.start();
    }

    public FFmpegFrameGrabber buildGrabber() {
        var grabber = new FFmpegFrameGrabber(inputUrl);
        grabber.setOption("reconnect", "1"); // 自动重连
        grabber.setOption("reconnect_streamed", "1"); // 流式传输自动重连
        grabber.setOption("reconnect_delay_max", "5"); // 最大自动重连延迟
        if (configuration.userAgent != null) grabber.setOption("user_agent", configuration.userAgent);

        grabber.setOption("timeout", String.valueOf(configuration.timeout));
        grabber.setOption("rw_timeout", String.valueOf(configuration.timeout));
        grabber.setOption("buffer_size", String.valueOf(configuration.bufferSize));
        grabber.setOption("probesize", String.valueOf(configuration.probesize));

        if (configuration.useHardwareDecoding) {
            grabber.setOption("hwaccel", "auto");
        }

        grabber.setAudioChannels(1);  // 不设置为 1 音频会有问题

        if (configuration.videoAlpha) {
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        } else {
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGB24);
        }
        grabber.setOption("vn", configuration.enableVideo ? "0" : "1");
        grabber.setOption("an", configuration.enableAudio ? "0" : "1");
        return grabber;
    }

    /**
     * 解码已结束且 frame 消费完
     */
    public boolean isEnded() {
        return decodeEnded && audioQueue.isEmpty();
    }

    /**
     * 解码结束
     */
    public boolean isDecodeEnded() {
        return decodeEnded;
    }

    public long getDuration() {
        return grabber.getLengthInTime();
    }

    /**
     * 视频宽度
     */
    public int getWidth() {
        return grabber.getImageWidth();
    }

    /**
     * 视频高度
     */
    public int getHeight() {
        return grabber.getImageHeight();
    }

    /**
     * 音频采样率
     */
    public int getSampleRate() {
        return grabber.getSampleRate();
    }

    /**
     * 音频通道数
     */
    public int getChannels() {
        return grabber.getAudioChannels();
    }

    public void seek(long timestamp) {
        if (timestamp < 0) {
            timestamp = 0;
        }
        var duration = this.getDuration();

        if (duration <= 0) {
            return;
        }

        if (timestamp >= duration) {
            timestamp = duration;
        }
        clearQueue();
        try {
            this.grabber.setTimestamp(timestamp, true);
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void processFrame(Frame frame) throws InterruptedException {
        long frameTimestamp = frame.timestamp;

        // 等待缓存被消费
        while (true) {
            if (frame.timestamp < 0 ) break;
            if (audioQueue.isEmpty()) {
                break;
            }
            if (frameTimestamp - audioQueue.peek().timestamp < configuration.cacheDuration) {
                break;
            }
            Thread.sleep(10);
        }

        if (frame.image != null) {
            videoQueue.offer(frame.clone());
        } else if (frame.samples != null) {
            audioQueue.offer(frame.clone());
        }
        frame.close();
    }

    public void clearQueue() {
        for (int i = 0; i < audioQueue.size(); i++) {
            audioQueue.poll().close();
        }
        for (int i = 0; i < videoQueue.size(); i++) {
            videoQueue.poll().close();
        }
    }

    @Override
    public void close() {

        try {
            this.decoderThread.interrupt();
            this.decoderThread.join();
        } catch (Exception e) {
            LOGGER.warn("关闭解码线程异常", e);
        }
        this.decodeEnded = true;
        clearQueue();
        try {
            this.grabber.release();
        } catch (Exception e) {
            LOGGER.warn("关闭解码器异常", e);
        }
    }
}

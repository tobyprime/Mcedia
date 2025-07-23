package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.internal.*;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public abstract class MediaDecoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDecoder.class);
    private final FfmpegDecoder decoder;
    private final AVSyncManager syncManager;
    private final AudioFrameConverter audioConverter;
    private final VideoFrameConverter videoConverter;
    private Thread audioThread;
    private volatile boolean playing = false;
    private PlayConfiguration config;

    public MediaDecoder() {
        this.audioConverter = new AudioFrameConverter();
        this.videoConverter = new VideoFrameConverter();

        this.syncManager = new AVSyncManager();
        this.decoder = new FfmpegDecoder();
    }


    public synchronized void load(PlayConfiguration config) {
        this.config = config;
        this.syncManager.reset();
        decoder.load(config);
    }

    public synchronized void play() {
        LOGGER.info("[{}] 开始播放 {}", hashCode(), this.config.getInputUrl());

        if (playing) return;
        playing = true;
        try {
            decoder.start();
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
        startAudioThread();
    }

    public synchronized void pause() {
        playing = false;
    }

    public synchronized void stop() {
        LOGGER.info("[{}] 正在暂停", hashCode());
        playing = false;
        if (audioThread != null) {
            audioThread.interrupt();
            try {
                audioThread.join();
            } catch (InterruptedException ignored) {
            }
            audioThread = null;
        }
        syncManager.reset();
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    public synchronized void seekTo(long positionUs) {
        LOGGER.info("[{}] 设置位置到 {}", hashCode(), positionUs);
        syncManager.reset();
    }

    public synchronized long getCurrentPositionUs() {
        return syncManager.getCurrentMediaTimeUs();
    }

    public VideoFrame getBestVideoFrame() {
        TimedFrame bestFrame = null;
        long targetPts = syncManager.getVideoDisplayTimeUs();

        var first = decoder.getVideoQueue().peek();
        if (first != null) {
            if (first.getPtsUs() > targetPts) {
                return null;
            }
        }

        // 消费队列中过期的帧，找到目标帧
        while (!decoder.getVideoQueue().isEmpty()) {
            if (bestFrame != null) bestFrame.close();

            bestFrame = decoder.getVideoQueue().poll();
            assert bestFrame != null;
            if (bestFrame.getPtsUs() >= targetPts) {
                break;
            }
        }
        if (bestFrame != null) {

            VideoFrame vf = videoConverter.AsVideoFrame(bestFrame);
            bestFrame.close(); // 释放资源
            return vf;
        }
        return null;
    }

    public void close() {
        stop();
        decoder.close();
    }

    protected abstract void processAudioFrame(AudioFrame frame);

    private void startAudioThread() {
        if (audioThread != null && audioThread.isAlive()) return;
        audioThread = new Thread(() -> {
            LOGGER.info("[{}] 音频线程已启动", hashCode());

            long nextPlayTime = System.nanoTime();
            while (playing && !Thread.currentThread().isInterrupted()) {
                try {
                    TimedFrame currFrame = decoder.getAudioQueue().poll(10, TimeUnit.MILLISECONDS);
                    if (currFrame == null) continue;
                    TimedFrame nextFrame = decoder.getAudioQueue().peek(); // 只看不取
                    syncManager.setAudioClock(currFrame.getPtsUs());
                    AudioFrame af = audioConverter.AsAudioFrame(currFrame);
                    if (af != null) {
                        processAudioFrame(af);
                    }
                    long intervalUs;
                    if (nextFrame != null) {
                        intervalUs = nextFrame.getPtsUs() - currFrame.getPtsUs();
                        if (intervalUs <= 0) intervalUs = 20000L; // 防止pts异常
                    } else {
                        intervalUs = 20000L; // 队列空时用默认值
                    }
                    nextPlayTime += intervalUs * 1000L;
                    long sleepNanos = nextPlayTime - System.nanoTime();
                    if (sleepNanos > 0) {
                        Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                    }
                    currFrame.close();
                } catch (InterruptedException e) {
                    break;
                }
            }
            LOGGER.info("[{}] 音频线程已退出", hashCode());
        }, "MediaPlayer-AudioThread");
        audioThread.start();
    }

    public PlayConfiguration getConfig() {
        return config;
    }

    public int getWidth() {
        return decoder.getWidth();
    }

    public int getHeight() {
        return decoder.getHeight();
    }

    public double getAspect() {
        return decoder.getAspectRatio();
    }
}

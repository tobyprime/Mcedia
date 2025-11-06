package top.tobyprime.mcedia.core;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

public class MediaDecoder implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDecoder.class);

    private static final int MAX_VIDEO_FRAMES = 60;
    private static final int MAX_AUDIO_FRAMES = 512;

    public final LinkedBlockingDeque<Frame> videoQueue = new LinkedBlockingDeque<>(MAX_VIDEO_FRAMES);
    public final LinkedBlockingDeque<Frame> audioQueue = new LinkedBlockingDeque<>(MAX_AUDIO_FRAMES);

    private final DecoderConfiguration configuration;
    private final List<Thread> decoderThreads = new ArrayList<>();
    private final List<FFmpegFrameGrabber> grabbers = new ArrayList<>();
    private final FFmpegFrameGrabber primaryGrabber;
    private volatile boolean isClosed = false;
    private volatile int runningDecoders = 0;

    public MediaDecoder(VideoInfo info, @Nullable String cookie, DecoderConfiguration configuration) throws FFmpegFrameGrabber.Exception {
        this.configuration = configuration;

        primaryGrabber = buildGrabber(info.getVideoUrl(), cookie, configuration, true);
        grabbers.add(primaryGrabber);

        if (info.getAudioUrl() != null && !info.getAudioUrl().isEmpty()) {
            FFmpegFrameGrabber audioGrabber = buildGrabber(info.getAudioUrl(), cookie, configuration, false);
            grabbers.add(audioGrabber);
        }

        for (FFmpegFrameGrabber grabber : grabbers) {
            int index = grabbers.indexOf(grabber);
            try {
                grabber.start();
            } catch (FFmpegFrameGrabber.Exception e) {
                throw e;
            }
        }

        for (FFmpegFrameGrabber grabber : grabbers) {
            Thread thread = new Thread(() -> decodeLoop(grabber));
            thread.setName("Mcedia-Decoder-" + (grabbers.indexOf(grabber) == 0 ? "Video" : "Audio"));
            thread.setDaemon(true);
            decoderThreads.add(thread);
            thread.start();
        }
    }

    private void decodeLoop(FFmpegFrameGrabber grabber) {
        int index = grabbers.indexOf(grabber);
        runningDecoders++;

        long videoFramesDecoded = 0;
        long audioFramesDecoded = 0;

        try {
            while (!Thread.currentThread().isInterrupted() && !isClosed) {
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

                Frame clonedFrame = frame.clone();

                if (clonedFrame.image != null && configuration.enableVideo) {
                    videoFramesDecoded++;
                    if (videoFramesDecoded == 1);
                    videoQueue.put(clonedFrame);
                } else if (clonedFrame.samples != null && configuration.enableAudio) {
                    audioFramesDecoded++;
                    if (audioFramesDecoded == 1);
                    audioQueue.put(clonedFrame);
                } else {
                    clonedFrame.close();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed) {
            }
        } finally {
            runningDecoders--;
        }
    }

    private FFmpegFrameGrabber buildGrabber(String url, @Nullable String cookie, DecoderConfiguration configuration, boolean isVideoGrabber) {
        var grabber = new FFmpegFrameGrabber(url);
        StringBuilder headers = new StringBuilder();
        headers.append("Referer: https://www.bilibili.com/\r\n");
        headers.append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\r\n");
        if (cookie != null && !cookie.isEmpty()) {
            headers.append("Cookie: ").append(cookie).append("\r\n");
        }
        grabber.setOption("headers", headers.toString());
        grabber.setOption("reconnect", "1");
        grabber.setOption("reconnect_streamed", "1");
        grabber.setOption("reconnect_delay_max", "5");
        grabber.setOption("timeout", String.valueOf(configuration.timeout));
        grabber.setOption("rw_timeout", String.valueOf(configuration.timeout));
        grabber.setOption("buffer_size", String.valueOf(configuration.bufferSize));
        grabber.setOption("probesize", String.valueOf(configuration.probesize));
        if (configuration.useHardwareDecoding) grabber.setOption("hwaccel", "auto");
        if (isVideoGrabber) {
            grabber.setOption("vn", configuration.enableVideo ? "0" : "1");
            grabber.setPixelFormat(configuration.videoAlpha ? avutil.AV_PIX_FMT_RGBA : avutil.AV_PIX_FMT_RGB24);
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
        clearQueue();
        try {
            for (FFmpegFrameGrabber grabber : grabbers) {
                grabber.setTimestamp(timestamp, true);
            }
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new RuntimeException("Seek failed", e);
        }
    }
    public void clearQueue() {
        videoQueue.forEach(Frame::close);
        audioQueue.forEach(Frame::close);
        videoQueue.clear();
        audioQueue.clear();
    }
    @Override
    public void close() {
        if (isClosed) return;
        isClosed = true;
        for (Thread thread : decoderThreads) {
            thread.interrupt();
        }
        for (Thread thread : decoderThreads) {
            try {
                thread.join(1000);
            } catch (InterruptedException ignored) {}
        }
        clearQueue();
        for (FFmpegFrameGrabber grabber : grabbers) {
            try {
                grabber.release();
            } catch (Exception e) {
            }
        }
    }
}
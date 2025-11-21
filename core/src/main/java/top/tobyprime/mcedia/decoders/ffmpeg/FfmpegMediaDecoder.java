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
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FfmpegMediaDecoder implements Closeable, IMediaDecoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegMediaDecoder.class);

    private boolean lowOverhead = false;
    public final LinkedBlockingDeque<FfmpegVideoData> videoQueue;
    public final LinkedBlockingDeque<FfmpegAudioData> audioQueue;

    private final DecoderConfiguration configuration;
    @Nullable
    private Thread masterDecoderThread;
    @Nullable
    private Thread audioDecodeThread;

    private final FFmpegFrameGrabber masterGrabber;
    private final FFmpegFrameGrabber audioGrabber;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ReentrantReadWriteLock masterGrabberLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock audioGrabberLock = new ReentrantReadWriteLock();

    public FfmpegMediaDecoder(MediaInfo info, DecoderConfiguration configuration) {
        this.configuration = configuration;

        this.videoQueue = new LinkedBlockingDeque<>(Configs.DECODER_MAX_AUDIO_FRAMES);
        this.audioQueue = new LinkedBlockingDeque<>(Configs.DECODER_MAX_AUDIO_FRAMES);

        try {
            if (configuration.enableVideo) {
                masterGrabber = buildGrabber(info.streamUrl, info.headers, info.cookie, configuration, true);
                masterGrabber.start();
            } else {
                masterGrabber = null;
            }

            if (configuration.enableAudio) {
                if (info.audioUrl != null && !info.audioUrl.isEmpty()) {
                    audioGrabber = buildGrabber(info.audioUrl, info.headers, info.cookie, configuration, false);
                    audioGrabber.start();
                } else {
                    audioGrabber = null; // buildGrabber(info.streamUrl, info.headers, info.cookie, configuration, false);
                }


            }else {
                audioGrabber = null;
            }
        }
        catch (FFmpegFrameGrabber.Exception e) {
            this.close();
            throw new RuntimeException(e);
        }
        if (masterGrabber == null) {
            throw new RuntimeException("No available grabber");
        }
        startDecoder();
    }

    public void startDecoder() {
        if (masterGrabber != null && masterDecoderThread == null) {
            Thread thread = new Thread(this::masterDecodeLoop);
            thread.setName("Mcedia-Decoder-Video");
            thread.setDaemon(true);
            thread.start();
            masterDecoderThread = thread;
        }

        if (audioGrabber != null && audioDecodeThread == null) {
            Thread thread = new Thread(this::audioDecodeLoop);
            thread.setName("Mcedia-Decoder-Audio");
            thread.setDaemon(true);
            thread.start();
            audioDecodeThread = thread;
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

    @Override
    public void setLowOverhead(boolean lowOverhead) {
        this.lowOverhead = lowOverhead;
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

        if (configuration.useHardwareDecoding) grabber.setOption("hwaccel", "auto");
        if (isVideoGrabber) {
            grabber.setOption("an", configuration.enableAudio ?"0" : "1"); // 视频解码禁用音频

            grabber.setOption("vf", "format=rgba");
            grabber.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        } else {
            grabber.setOption("vn", "1"); // 音频解码禁用视频

            if (configuration.audioSampleRate > 0) {
                grabber.setSampleRate(configuration.audioSampleRate);
            }
        }
        grabber.setAudioChannels(1);
        return grabber;
    }


    private void masterDecodeLoop() {
        try {
            boolean grabVideo = true;
            long lastVideoPts = 0;
            while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
                masterGrabberLock.readLock().lock();
                try {
                    if (isClosed.get()) {
                        break;
                    }
                    if (audioGrabber != null && masterGrabber.getTimestamp() - audioGrabber.getTimestamp() > 100_000){
                        Thread.sleep(10);
                        continue;
                    }
                    Frame frame = masterGrabber.grabFrame(true, grabVideo, true, false, false);
                    if (frame == null) {
                        break;
                    }

                    boolean isVideo = frame.image != null && configuration.enableVideo;
                    boolean isAudio = frame.samples != null && configuration.enableAudio;

                    if (isVideo) {
                        lastVideoPts = frame.timestamp;

                        var max = lowOverhead ? Configs.DECODER_LOW_OVERHEAD_VIDEO_FRAMES : Configs.DECODER_MAX_VIDEO_FRAMES;
                        while (videoQueue.size() > max) {
                            Thread.sleep(10);
                        }
                        videoQueue.put(new FfmpegVideoData(frame));
                    }
                    if (isAudio) {
                        audioQueue.put(new FfmpegAudioData(frame));
                    }
                    if (lowOverhead) {
                        grabVideo = Math.abs(frame.timestamp - lastVideoPts) > 200_000;
                    } else {
                        grabVideo = true;
                    }
                } catch (FFmpegFrameGrabber.Exception e) {
                    if (!isClosed.get()) {
                        LOGGER.warn("视频解码发生异常.", e);
                    }
                    break;
                } finally {
                    masterGrabberLock.readLock().unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("在解码循环中发生未捕获的错误", e);
            }
        } finally {
            masterDecoderThread = null;
        }
    }

    private void audioDecodeLoop() {
        try {
            while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
                audioGrabberLock.readLock().lock();
                try {
                    if (masterGrabber != null &&  audioGrabber.getTimestamp() - masterGrabber.getTimestamp() > 100_000){
                        Thread.sleep(10);
                        continue;
                    }

                    if (isClosed.get()) {
                        break;
                    }

                    Frame frame = audioGrabber.grabSamples();
                    if (frame == null) {
                        break;
                    }

                    boolean isAudio = frame.samples != null && configuration.enableAudio;
                    if (isAudio) {
                        audioQueue.put(new FfmpegAudioData(frame));
                    }

                } catch (FFmpegFrameGrabber.Exception e) {
                    if (!isClosed.get()) {
                        LOGGER.warn("音频解码发生异常.", e);
                    }
                    break;
                } finally {
                    audioGrabberLock.readLock().unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("在解码循环中发生未捕获的错误", e);
            }
        } finally {
            audioDecodeThread = null;
        }
    }

    public boolean isEnded() {
        return audioDecodeThread == null;
    }

    public FFmpegFrameGrabber getPrimaryGrabber(){
        if (masterGrabber != null) return masterGrabber;
        if (audioGrabber != null) return audioGrabber;
        throw new RuntimeException("No active grabber found");
    }

    public long getLength() {
        return getPrimaryGrabber().getLengthInTime();
    }


    @Override
    public long getDuration() {
        return getPrimaryGrabber().getTimestamp();
    }

    public int getWidth() {
        if (masterGrabber == null) return -1;
        return masterGrabber.getImageWidth();
    }

    public int getHeight() {
        if (masterGrabber == null) return -1;
        return masterGrabber.getImageHeight();
    }

    @Override
    public int getSampleRate() {
        if (audioGrabber == null) return masterGrabber.getSampleRate();
        return audioGrabber.getSampleRate();
    }

    @Override
    public int getChannels() {
        if (audioGrabber == null) return masterGrabber.getAudioChannels();
        return audioGrabber.getAudioChannels();
    }

    public void seek(long timestamp) {
        if (getLength() <= 0) return;
        timestamp = Math.max(0, Math.min(timestamp, getLength()));

        masterGrabberLock.writeLock().lock();
        try {
            if (masterGrabber != null) {
                masterGrabber.setTimestamp(timestamp);
            }
        } catch (FFmpegFrameGrabber.Exception e) {
            LOGGER.error("seek failed.", e);
            throw new RuntimeException(e);
        } finally {
            masterGrabberLock.writeLock().unlock();
        }

        audioGrabberLock.writeLock().lock();
        try {
            if (audioGrabber != null) {
                audioGrabber.setTimestamp(timestamp);
            }
        } catch (FrameGrabber.Exception e) {
            LOGGER.error("seek failed.", e);
            throw new RuntimeException("Seek failed", e);
        } finally {
            audioGrabberLock.writeLock().unlock();
        }

        startDecoder();
        clearQueue();
    }

    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        if (audioDecodeThread != null) {
            audioDecodeThread.interrupt();
        }
        if (masterDecoderThread != null) {
            masterDecoderThread.interrupt();
        }
        masterGrabberLock.writeLock().lock();


        audioGrabberLock.writeLock().lock();
        try {
            try {
                if (masterGrabber != null) {
                    masterGrabber.stop();
                    masterGrabber.release();
                }
                if (audioGrabber != null) {
                    audioGrabber.stop();
                    audioGrabber.release();
                }
            } catch (Exception e) {
                LOGGER.warn("停止或释放 grabber 时出错", e);
            }
        } finally {
            masterGrabberLock.writeLock().unlock();
            audioGrabberLock.writeLock().unlock();
        }

       if (masterDecoderThread != null) {
           masterDecoderThread.interrupt();
       }
       if (audioDecodeThread != null) {
           audioDecodeThread.interrupt();
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
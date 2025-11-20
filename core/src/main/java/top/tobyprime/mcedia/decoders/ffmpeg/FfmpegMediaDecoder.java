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
    private Thread videoDecodeThread;
    @Nullable
    private Thread audioDecodeThread;

    private final FFmpegFrameGrabber videoGrabber;
    private final FFmpegFrameGrabber audioGrabber;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ReentrantReadWriteLock videoGrabberLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock audioGrabberLock = new ReentrantReadWriteLock();

    public FfmpegMediaDecoder(MediaInfo info, DecoderConfiguration configuration) {
        this.configuration = configuration;

        this.videoQueue = new LinkedBlockingDeque<>(Configs.DECODER_MAX_VIDEO_FRAMES);
        this.audioQueue = new LinkedBlockingDeque<>(Configs.DECODER_MAX_AUDIO_FRAMES);

        try {
            if (configuration.enableVideo) {
                videoGrabber = buildGrabber(info.streamUrl, info.headers, info.cookie, configuration, true);
                videoGrabber.start();
            } else {
                videoGrabber = null;
            }

            if (configuration.enableAudio) {
                if (info.audioUrl != null && !info.audioUrl.isEmpty()) {
                    audioGrabber = buildGrabber(info.audioUrl, info.headers, info.cookie, configuration, false);
                } else {
                    audioGrabber = buildGrabber(info.streamUrl, info.headers, info.cookie, configuration, false);
                }

                audioGrabber.start();

            }else {
                audioGrabber = null;
            }
        }
        catch (FFmpegFrameGrabber.Exception e) {
            this.close();
            throw new RuntimeException(e);
        }
        if (audioGrabber == null && videoGrabber == null) {
            throw new RuntimeException("No available grabber");
        }
        startDecoder();
    }

    public void startDecoder() {
        if (videoGrabber != null && videoDecodeThread == null) {
            Thread thread = new Thread(this::videoDecodeLoop);
            thread.setName("Mcedia-Decoder-Video");
            thread.setDaemon(true);
            thread.start();
            videoDecodeThread = thread;
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
            grabber.setOption("an", "1"); // 视频解码禁用音频

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


    private void videoDecodeLoop() {
        try {
            while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
                videoGrabberLock.readLock().lock();
                try {
                    if (isClosed.get()) {
                        break;
                    }

                    Frame frame = videoGrabber.grabImage();
                    if (frame == null) {
                        break;
                    }

                    var curTimestamp = frame.timestamp;

                    boolean isVideo = frame.image != null && configuration.enableVideo;

                    if (isVideo) {
                        // 如果进入低开销模式，则降低到最低缓存，同时放慢解码频率
                        while (lowOverhead && videoQueue.size() > Configs.DECODER_LOW_OVERHEAD_VIDEO_FRAMES) {
                            Thread.sleep(50);
                        }
                        videoQueue.put(new FfmpegVideoData(frame));
                        while (lowOverhead) {
                            var skiped = videoGrabber.grabFrame(false, true, true, false);
                            if (skiped == null) break;
                            if (skiped.timestamp - curTimestamp >= 200000) {
                                break;
                            }
                            Thread.sleep(10);
                        }
                    }

                } catch (FFmpegFrameGrabber.Exception e) {
                    if (!isClosed.get()) {
                        LOGGER.warn("视频解码发生异常.", e);
                    }
                    break;
                } finally {
                    videoGrabberLock.readLock().unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!isClosed.get()) {
                LOGGER.error("在解码循环中发生未捕获的错误", e);
            }
        } finally {
            videoDecodeThread = null;
        }
    }

    private void audioDecodeLoop() {
        try {
            while (!Thread.currentThread().isInterrupted() && !isClosed.get()) {
                audioGrabberLock.readLock().lock();
                try {
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
        if (videoGrabber != null) return videoGrabber;
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
        if (videoGrabber == null) return -1;
        return videoGrabber.getImageWidth();
    }

    public int getHeight() {
        if (videoGrabber == null) return -1;
        return videoGrabber.getImageHeight();
    }

    @Override
    public int getSampleRate() {
        if (audioGrabber == null) return -1;
        return audioGrabber.getSampleRate();
    }

    @Override
    public int getChannels() {
        if (audioGrabber == null) return -1;
        return audioGrabber.getAudioChannels();
    }

    public void seek(long timestamp) {
        if (getLength() <= 0) return;
        timestamp = Math.max(0, Math.min(timestamp, getLength()));

        videoGrabberLock.writeLock().lock();
        audioGrabberLock.writeLock().lock();
        try {
            if (audioGrabber != null) {
                audioGrabber.setTimestamp(timestamp);
            }
            if (videoGrabber != null) {
                videoGrabber.setTimestamp(timestamp);
            }
            startDecoder();
            clearQueue();
        } catch (FrameGrabber.Exception e) {
            throw new RuntimeException("Seek failed", e);
        } finally {
            videoGrabberLock.writeLock().unlock();
            audioGrabberLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        if (audioDecodeThread != null) {
            audioDecodeThread.interrupt();
        }
        if (videoDecodeThread != null) {
            videoDecodeThread.interrupt();
        }
        videoGrabberLock.writeLock().lock();
        audioGrabberLock.writeLock().lock();
        try {
            try {
                if (videoGrabber != null) {
                    videoGrabber.stop();
                    videoGrabber.release();
                }
                if (audioGrabber != null) {
                    audioGrabber.stop();
                    audioGrabber.release();
                }
            } catch (Exception e) {
                LOGGER.warn("停止或释放 grabber 时出错", e);
            }
        } finally {
            videoGrabberLock.writeLock().unlock();
            audioGrabberLock.writeLock().unlock();
        }

       if (videoDecodeThread != null) {
           videoDecodeThread.interrupt();
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
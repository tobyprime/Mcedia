package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.ITexture;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.io.Closeable;
import java.util.ArrayList;

public class Media implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Media.class);

    private final MediaDecoder decoder;
    private final Thread audioThread;
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    @Nullable
    private ITexture texture;

    private boolean paused = true;
    private boolean isLiveStream = false;
    private float speed = 1;
    private boolean looping = false;
    @Nullable
    private Frame currentVideoFrame;

    private boolean isBuffering = true;
    private static final int AUDIO_BUFFER_TARGET = 256;
    private static final int VIDEO_BUFFER_TARGET = 30;

    public Media(String url, DecoderConfiguration config) {
        this(new VideoInfo(url, null), null, config);
    }

    public Media(VideoInfo info, DecoderConfiguration config) {
        this(info, null, config);
    }

    public Media(VideoInfo info, @Nullable String cookie, DecoderConfiguration config) {
        try {
            decoder = new MediaDecoder(info, cookie, config);
            isLiveStream = decoder.getDuration() <= 0 || Double.isInfinite(decoder.getDuration());
            LOGGER.info("检测到 {}流: {}", isLiveStream ? "直播" : "点播", info.getVideoUrl());
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
        audioThread = new Thread(this::playLoop);
        audioThread.setName("Mcedia-Audio-Thread");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void playLoop() {
        long nextPlayTime = System.nanoTime();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (paused) {
                    Thread.sleep(10);
                    continue;
                }

                if (isBuffering) {
                    boolean hasEnoughBuffer = isLiveStream ? (decoder.audioQueue.size() > 50)
                            : (decoder.audioQueue.size() > AUDIO_BUFFER_TARGET && decoder.videoQueue.size() > VIDEO_BUFFER_TARGET);

                    if (hasEnoughBuffer || decoder.isEnded()) {
                        isBuffering = false;
                        nextPlayTime = System.nanoTime();
                        LOGGER.debug("Buffering complete, resuming playback.");
                    } else {
                        Thread.sleep(50);
                        continue;
                    }
                }

                Frame currFrame = decoder.audioQueue.poll();
                if (currFrame == null) {
                    if (decoder.isEnded() && looping && decoder.getDuration() > 0) {
                        LOGGER.info("Looping stream.");
                        this.seek(0);
                    } else if (!decoder.isEnded()) {
                        isBuffering = true;
                        LOGGER.warn("Buffer empty, starting to buffer...");
                    }
                    Thread.sleep(10);
                    continue;
                }

                while (!decoder.videoQueue.isEmpty()) {
                    Frame videoFrame = decoder.videoQueue.peek();
                    if (videoFrame == null) break;
                    if (videoFrame.timestamp < currFrame.timestamp) {
                        synchronized (this) {
                            if (currentVideoFrame != null) currentVideoFrame.close();
                            currentVideoFrame = decoder.videoQueue.poll();
                        }
                    } else {
                        break;
                    }
                }

                uploadBuffer(currFrame);

                Frame nextFrame = decoder.audioQueue.peek();
                long intervalUs = (nextFrame != null) ? (long) ((nextFrame.timestamp - currFrame.timestamp) / speed) : 20_000L;
                if (intervalUs <= 0) intervalUs = 20_000L;
                nextPlayTime += intervalUs * 1000L;
                long sleepNanos = nextPlayTime - System.nanoTime();
                if (sleepNanos > 0) {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                }
                currFrame.close();
            }
        } catch (InterruptedException ignored) {
        }
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void play() {
        LOGGER.info("开始播放");
        paused = false;
    }

    public void pause() {
        LOGGER.info("暂停播放");
        paused = true;
    }

    public long getDurationUs() {
        if (decoder.audioQueue.isEmpty()) return 0;
        Frame lastFrame = decoder.audioQueue.peekLast();
        return (lastFrame != null) ? lastFrame.timestamp : 0;
    }

    public long getLengthUs() {
        return decoder.getDuration();
    }

    public double getDurationSeconds() {
        return getDurationUs() / 1_000_000.0;
    }

    public void setSpeed(float speed) {
        if (speed == this.speed) return;
        this.speed = speed;
        this.audioSources.forEach(s -> s.setPitch(speed));
    }

    public void seek(long targetUs) {
        if (isLiveStream) {
            LOGGER.warn("直播流不支持seek操作");
            return;
        }
        LOGGER.info("移动到 {} us", targetUs);
        try {
            if (targetUs > getLengthUs()) targetUs = getLengthUs();
            if (targetUs < 0) targetUs = 0;

            decoder.seek(targetUs);
            isBuffering = true;
        } catch (Exception e) {
            LOGGER.error("Seek failed", e);
        }
    }

    public void bindTexture(ITexture texture) {
        this.texture = texture;
    }

    public void unbindTexture() {
        this.texture = null;
    }

    public void bindAudioSource(IAudioSource audioBuffer) {
        this.audioSources.add(audioBuffer);
    }

    public void unbindAudioSource(IAudioSource audioBuffer) {
        this.audioSources.remove(audioBuffer);
    }

    public synchronized void uploadVideo() {
        if (paused || texture == null || currentVideoFrame == null) return;
        var frame = currentVideoFrame;
        currentVideoFrame = null;
        VideoFrame vf = VideoDataConverter.convertToVideoFrame(frame);
        texture.upload(vf);
        frame.close();
    }

    private void uploadBuffer(Frame frame) {
        for (var audioSource : audioSources) {
            audioSource.upload(AudioDataConverter.AsAudioData(frame, -1));
        }
    }

    public boolean isPlaying() {
        return !paused && !decoder.isEnded();
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isEnded() {
        return !isLiveStream && decoder.isEnded();
    }

    public int getWidth() {
        return decoder.getWidth();
    }

    public int getHeight() {
        return decoder.getHeight();
    }

    public float getAspectRatio() {
        if (getHeight() == 0) return 16f / 9f; // 防止除零
        return (float) getWidth() / getHeight();
    }

    @Override
    public void close() {
        audioThread.interrupt();
        this.audioSources.forEach(s -> s.setPitch(1));
        try {
            audioThread.join();
        } catch (InterruptedException e) {
            LOGGER.warn("音频上传线程停止异常", e);
        }
        audioSources.forEach(IAudioSource::clearBuffer);
        decoder.close();
    }
}
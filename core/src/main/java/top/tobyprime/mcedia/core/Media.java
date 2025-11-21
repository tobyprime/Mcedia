package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.danmaku.Danmaku;
import top.tobyprime.mcedia.danmaku.DanmakuEntity;
import top.tobyprime.mcedia.danmaku.DanmakuScreen;
import top.tobyprime.mcedia.decoders.DecoderConfiguration;
import top.tobyprime.mcedia.decoders.VideoFrame;
import top.tobyprime.mcedia.decoders.ffmpeg.FfmpegMediaDecoder;
import top.tobyprime.mcedia.interfaces.*;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

/**
 * 负责单一媒体文件的音视频同步解码，支持直播和点播
 */
public class Media implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Media.class);

    private final IMediaDecoder decoder;
    private final Thread audioThread;
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    private final MediaInfo mediaInfo;
    private final DanmakuScreen danmakuScreen;
    public long lastDanmakuUpdateDurationUs = -1;
    public long lastDanmakuDurationUpdateTimeUs = -1;
    private @Nullable ITexture texture;
    private boolean paused = true;
    private boolean isLiveStream = false; // 标识是否为直播
    private long lastAudioPts = -1; // 最近上传的音频帧时间戳
    private float speed = 1;
    private boolean looping = false;
    // 最近上传的视频帧时间戳
    private @Nullable IVideoData currentVideoFrame;
    public Media(MediaInfo info, DecoderConfiguration config) {
        decoder = new FfmpegMediaDecoder(info, config);

        // 检测是否为直播流（假设duration无效或为0表示直播）
        isLiveStream = decoder.isLiveStream();
        audioThread = new Thread(this::playLoop);
        audioThread.start();
        mediaInfo = info;
        if (mediaInfo.danmakus != null)
            this.danmakuScreen = new DanmakuScreen(mediaInfo.danmakus);
        else
            this.danmakuScreen = null;

    }

    public void setLowOverhead(boolean lowOverhead) {
        this.decoder.setLowOverhead(lowOverhead);
    }

    public @Nullable Collection<DanmakuEntity> updateAndGetDanmakus() {
        var screen = danmakuScreen;
        if (screen != null) {
            long durationUs = this.getDuration();
            long nowUs = System.currentTimeMillis();
            double durationSecs = durationUs / 1_000_000.0;

            if (!paused && lastDanmakuUpdateDurationUs == durationUs) {
                durationSecs += (nowUs - lastDanmakuDurationUpdateTimeUs) / 1_000_000.0;
            } else {
                lastDanmakuUpdateDurationUs = durationUs;
                lastDanmakuDurationUpdateTimeUs = nowUs;
            }

            return screen.update((float) durationSecs);
        }
        return null;
    }

    public MediaInfo getMediaInfo() {
        return mediaInfo;
    }

    /**
     * 播放结束
     */
    public boolean isEnd(){
        return !looping && this.getDuration() >= this.getLength();
    }
    volatile long nextPlayTime;

    public void playLoop() {
        nextPlayTime = System.nanoTime();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (!paused) {
                    IAudioData currFrame = decoder.getAudioQueue().poll();
                    if (currFrame == null) {
                        if (looping && decoder.isEnded() && decoder.getAudioQueue().isEmpty() && this.getDuration() != 0) {
                            // 如果播放结束，且需要循环则设置时间到 0
                            LOGGER.info("looping");
                            this.seek(0);
                        }
                        // 无帧时，短暂睡眠避免忙等待
                        Thread.sleep(10);
                        continue;
                    }
                    // 消费掉过期的视频帧
                    while (!decoder.getVideoQueue().isEmpty()) {
                        IVideoData videoFrame = decoder.getVideoQueue().peek();
                        if (videoFrame == null) break;
                        // 如果视频帧的时间戳小于当前音频帧，则认为过期，消费掉
                        if (videoFrame.getTimestamp() < currFrame.getTimestamp()) {
                            synchronized (this) {
                                if (currentVideoFrame != null)
                                    currentVideoFrame.close();
                                currentVideoFrame = decoder.getVideoQueue().poll();
                            }
                        } else {
                            break;
                        }
                    }

                    uploadBuffer(currFrame);
                    lastAudioPts = currFrame.getTimestamp();

                    // 计算下一个音频帧的间隔
                    IAudioData nextFrame = decoder.getAudioQueue().peek();
                    long intervalUs;
                    if (nextFrame != null) {
                        intervalUs = (long) ((nextFrame.getTimestamp() - currFrame.getTimestamp()) / speed);
                        if (intervalUs <= 0) intervalUs = 20_000L; // 防止pts异常
                    } else {
                        intervalUs = 20_000L; // 队列空时用默认值
                    }
                    nextPlayTime += intervalUs * 1000L;
                    long sleepNanos = nextPlayTime - System.nanoTime();
                    if (sleepNanos > 0) {
                        Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                    }
                    currFrame.close();
                } else {
                    // 暂停时，短暂睡眠避免忙等待
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException ignored) {
        }

    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    /**
     * 播放
     */
    public void play() {
        LOGGER.info("开始播放");
        if (paused) {
            paused = false;
            nextPlayTime = System.nanoTime();
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        LOGGER.info("暂停播放");
        if (!paused) {
            paused = true;
            // 直播时不更新baseDuration
        }
    }

    /**
     * 当前播放时长 (微秒)
     */
    public long getDuration() {
        return lastAudioPts;
    }

    public long getLength() {
        return decoder.getLength();
    }

    /**
     * 获取秒数（方便UI）
     */
    public double getDurationSeconds() {
        return getDuration() / 1_000_000.0;
    }

    public void setSpeed(float speed) {
        if (speed == this.speed) {
            return;
        }
        this.speed = speed;
        this.audioSources.forEach(s -> s.setPitch(speed));
    }

    /**
     * 跳转到指定时间戳 (微秒)
     */
    public void seek(long targetUs) {
        if (isLiveStream) {
            LOGGER.warn("直播流不支持seek操作");
            return;
        }
        LOGGER.info("移动到 {}", targetUs);
        try {
            if (targetUs > getLength()) {
                targetUs = getLength();
            }
            if (targetUs < 0) {
                targetUs = 0;
            }
            decoder.seek(targetUs);
            lastAudioPts = targetUs;
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

    private long getCurrentMediaTimeUs() {
        return getDuration();
    }

    public synchronized void uploadVideo() {
        if (paused || texture == null) return;

        if (currentVideoFrame == null) return;

        // 否则正常渲染
        var frame = currentVideoFrame;
        currentVideoFrame = null;
        VideoFrame vf = frame.toFrame();
        texture.upload(vf);
        frame.close();
    }

    private void uploadBuffer(IAudioData frame) {
        for (var audioSource : audioSources) {
            audioSource.upload(frame.getMergedAudioData());
            if (Configs.AUDIO_SOURCE_CONSUMER != null && Configs.PHYSICS){
                Configs.AUDIO_SOURCE_CONSUMER.accept(audioSource);
            }
        }
    }

    public boolean isPlaying() {
        if (paused) return false;
        return !decoder.isEnded();
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

    public void setDanmakuWidthPredictor(@Nullable Function<Danmaku, Float> danmakuWidthPredictor) {
        if (this.danmakuScreen == null) {
            return;
        }
        this.danmakuScreen.setWidthPredictor(danmakuWidthPredictor);
    }
}
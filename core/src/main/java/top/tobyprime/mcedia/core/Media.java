package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.ITexture;
import top.tobyprime.mcedia.provider.VideoInfo; // 导入 VideoInfo

import java.io.Closeable;
import java.util.ArrayList;

/**
 * 负责单一媒体文件的音视频同步解码，支持直播和点播
 */
public class Media implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Media.class);

    private final MediaDecoder decoder;
    private final Thread audioThread;
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    private @Nullable ITexture texture;
    private long baseTime;       // 播放开始的系统时间 (ms)
    private long baseDuration;   // 点播时累计播放时长 (us), 直播时不使用
    private boolean paused = true;
    private boolean isLiveStream = false; // 标识是否为直播
    private long lastAudioPts = -1; // 最近上传的音频帧时间戳
    private float speed = 1;
    private boolean looping = false;

    private @Nullable Frame currentVideoFrame;

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
            if (isLiveStream) {
                LOGGER.info("检测到直播流: {}", info.getVideoUrl());
            } else {
                LOGGER.info("检测到点播流: {}", info.getVideoUrl());
            }
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
                if (!paused) {
                    Frame currFrame = decoder.audioQueue.poll();
                    if (currFrame == null) {
                        if (decoder.isEnded() && looping && decoder.getDuration() != 0) {
                            // 如果循环播放且播放结束则从头开始
                            LOGGER.info("looping");
                            this.seek(0);
                        }
                        // 无帧时，短暂睡眠避免忙等待
                        Thread.sleep(10);
                        continue;
                    }
                    // 消费掉过期的视频帧
                    while (!decoder.videoQueue.isEmpty()) {
                        Frame videoFrame = decoder.videoQueue.peek();
                        if (videoFrame == null) break;
                        // 如果视频帧的时间戳小于当前音频帧，则认为过期，消费掉
                        if (videoFrame.timestamp < currFrame.timestamp) {
                            synchronized (this) {
                                if (currentVideoFrame != null)
                                    currentVideoFrame.close();
                                currentVideoFrame = decoder.videoQueue.poll();
                            }
                        } else {
                            break;
                        }
                    }

                    uploadBuffer(currFrame);
                    lastAudioPts = currFrame.timestamp;

                    // 计算下一个音频帧的间隔
                    Frame nextFrame = decoder.audioQueue.peek();
                    long intervalUs;
                    if (nextFrame != null) {
                        intervalUs = (long) ((nextFrame.timestamp - currFrame.timestamp)/speed);
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
            baseTime = System.currentTimeMillis();
            if (!isLiveStream) {
                baseDuration = lastAudioPts < 0 ? 0 : lastAudioPts; // 点播时使用上次PTS
            } else {
                baseDuration = 0; // 直播时重置
                lastAudioPts = -1;
            }
            paused = false;
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        LOGGER.info("暂停播放");
        if (!paused) {
            paused = true;
            if (!isLiveStream) {
                baseDuration = lastAudioPts < 0 ? 0 : lastAudioPts; // 点播时记录暂停时的PTS
            }
            // 直播时不更新baseDuration
        }
    }

    /**
     * 当前播放时长 (微秒)
     */
    public long getDurationUs() {
        if (paused) {
            return isLiveStream ? 0 : baseDuration; // 直播暂停时返回0
        }
        if (isLiveStream) {
            return lastAudioPts < 0 ? 0 : lastAudioPts; // 直播时返回最新音频PTS
        }
        return baseDuration + (System.currentTimeMillis() - baseTime) * 1000L;
    }

    public long getLengthUs() {
        return decoder.getDuration();
    };

    /**
     * 获取秒数（方便UI）
     */
    public double getDurationSeconds() {
        return getDurationUs() / 1_000_000.0;
    }
    public void setSpeed(float speed) {
        if (speed == this.speed) {return;}
        this.speed = speed;
        this.audioSources.forEach(s->s.setPitch(speed));
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
            if (targetUs > getLengthUs()) {
                targetUs =  getLengthUs();
            }
            if (targetUs < 0) {
                targetUs = 0;
            }
            decoder.seek(targetUs);
            baseDuration = targetUs;
            baseTime = System.currentTimeMillis();
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
        return getDurationUs();
    }

    public synchronized void uploadVideo() {
        if (paused || texture == null) return;

        if (currentVideoFrame == null) return;

        var frame = currentVideoFrame;
        currentVideoFrame = null;
        VideoFrame vf = VideoDataConverter.convertToVideoFrame(frame);
        texture.upload(vf);
        frame.close();
    }

    private void uploadBuffer(Frame frame) {
        for (var audioSource: audioSources){
            audioSource.upload(AudioBufferDataConverter.AsAudioData(frame, -1));
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
        this.audioSources.forEach(s->s.setPitch(1));

        try {
            audioThread.join();
        } catch (InterruptedException e) {
            LOGGER.warn("音频上传线程停止异常", e);
        }
        audioSources.forEach(IAudioSource::clearBuffer);
        decoder.close();
    }
}
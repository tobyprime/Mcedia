package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.ITexture;

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
    private long lastVideoPts = -1; // 最近上传的视频帧时间戳
    private @Nullable Frame currentVideoFrame;
    public Media(String url, DecoderConfiguration config) {
        try {
            decoder = new MediaDecoder(url, config);
            // 检测是否为直播流（假设duration无效或为0表示直播）
            isLiveStream = decoder.getDuration() <= 0 || Double.isInfinite(decoder.getDuration());
            if (isLiveStream) {
                LOGGER.info("检测到直播流: {}", url);
            } else {
                LOGGER.info("检测到点播流: {}", url);
            }
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new RuntimeException(e);
        }
        audioThread = new Thread(() -> {
            LOGGER.info("音频上传线程启动");
            long nextPlayTime = System.nanoTime();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (!paused) {
                        Frame currFrame = decoder.audioQueue.poll();
                        if (currFrame == null) {
                            // 无帧时，短暂睡眠避免忙等待
                            Thread.sleep(10);
                            continue;
                        }
                        Frame nextFrame = decoder.audioQueue.peek(); // 只看不取

                        // 消费掉过期的视频帧
                        while (!decoder.videoQueue.isEmpty()) {
                            Frame videoFrame = decoder.videoQueue.peek();
                            if (videoFrame == null) break;
                            // 如果视频帧的时间戳小于当前音频帧，则认为过期，消费掉
                            if (videoFrame.timestamp < currFrame.timestamp) {
                                synchronized (this){
                                    if (currentVideoFrame != null)
                                        currentVideoFrame.close();
                                    currentVideoFrame = decoder.videoQueue.poll();
                                }
                                lastVideoPts = videoFrame.timestamp;
                            } else {
                                break;
                            }
                        }

                        uploadBuffer(currFrame);
                        lastAudioPts = currFrame.timestamp;

                        // 计算下一个音频帧的间隔
                        long intervalUs;
                        if (nextFrame != null) {
                            intervalUs = nextFrame.timestamp - currFrame.timestamp;
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
            LOGGER.info("音频上传线程退出");
        });
        audioThread.start();
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
                lastVideoPts = -1;
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

    /**
     * 获取秒数（方便UI）
     */
    public double getDurationSeconds() {
        return getDurationUs() / 1_000_000.0;
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
            decoder.seek(targetUs);
            baseDuration = targetUs;
            baseTime = System.currentTimeMillis();
            lastAudioPts = targetUs;
            lastVideoPts = targetUs;
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

        // 否则正常渲染
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
        try {
            audioThread.join();
        } catch (InterruptedException e) {
            LOGGER.warn("音频上传线程停止异常", e);
        }
        audioSources.forEach(IAudioSource::clearBuffer);
        decoder.close();
    }
}
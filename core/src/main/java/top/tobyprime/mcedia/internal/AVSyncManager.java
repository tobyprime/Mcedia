package top.tobyprime.mcedia.internal;


/**
 * 简单的音视频同步管理器，以音频为主时钟。
 */
public class AVSyncManager  {
    // 音频时钟基准（微秒）
    private long audioClockUs = 0L;
    // 设置音频时钟时的系统时间（纳秒）
    private long systemNanoWhenSet = 0L;
    // 是否已设置基准
    private boolean clockSet = false;

    public synchronized void setAudioClock(long audioPtsUs) {
        this.audioClockUs = audioPtsUs;
        this.systemNanoWhenSet = System.nanoTime();
        this.clockSet = true;
    }

    public synchronized long getCurrentMediaTimeUs() {
        if (!clockSet) return 0L;
        long elapsedUs = (System.nanoTime() - systemNanoWhenSet) / 1000L;
        return audioClockUs + elapsedUs;
    }

    public synchronized long getVideoDisplayTimeUs() {
        // 通常视频帧应与音频时钟对齐
        return getCurrentMediaTimeUs();
    }

    public synchronized void reset() {
        audioClockUs = 0L;
        systemNanoWhenSet = 0L;
        clockSet = false;
    }
} 
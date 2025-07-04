package top.tobyprime.mcedia.internal;

import org.bytedeco.javacv.Frame;
import java.io.Closeable;

/**
 * 通用的带时间戳的帧，延迟转换为 AudioFrame/VideoFrame，节约内存。
 */
public class TimedFrame implements Closeable {
    private final Frame frame;
    private final long ptsUs;
    private boolean released = false;

    public TimedFrame(Frame frame, long ptsUs) {
        this.frame = frame;
        this.ptsUs = ptsUs;
    }

    public long getPtsUs() {
        return ptsUs;
    }

    public Frame getRawFrame() {
        return frame;
    }

    @Override
    public void close() {
        if (!released && frame != null) {
            frame.close();
            released = true;
        }
    }
} 
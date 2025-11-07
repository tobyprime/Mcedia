// VideoFrame.java
package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.nio.ByteBuffer;

public class VideoFrame implements Closeable {
    public final int width;
    public final int height;
    public final long ptsUs; // Presentation Timestamp in Microseconds
    private boolean released;
    public ByteBuffer buffer;

    @Nullable
    private final VideoFramePool pool;

    public VideoFrame(ByteBuffer buffer, int width, int height, long ptsUs, @Nullable VideoFramePool pool) {
        this.buffer = buffer;
        this.width = width;
        this.height = height;
        this.ptsUs = ptsUs;
        this.pool = pool;
    }

    // 兼容旧代码的构造函数
    public VideoFrame(ByteBuffer buffer, int width, int height, long ptsUs) {
        this(buffer, width, height, ptsUs, null);
    }

    @Override
    public String toString() {
        return "VideoFrame{" +
                "width=" + width +
                ", height=" + height +
                ", pts=" + ptsUs +
                ", buffer_capacity=" + (buffer != null ? buffer.capacity() : "null") +
                ", released=" + released +
                '}';
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.release(this.buffer);
        } else if (this.buffer != null) {
            // 回退到手动释放
            MemoryUtil.memFree(this.buffer);
        }
        this.buffer = null; // 防止重复释放
    }
}
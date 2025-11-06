// VideoFrame.java
package top.tobyprime.mcedia.core;

import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.nio.ByteBuffer;

public class VideoFrame implements Closeable {
    public final ByteBuffer buffer;
    public final int width;
    public final int height;
    public final long ptsUs; // Presentation Timestamp in Microseconds
    private boolean released;

    public VideoFrame(ByteBuffer buffer, int width, int height, long ptsUs) {
        this.buffer = buffer;
        this.width = width;
        this.height = height;
        this.ptsUs = ptsUs; // 存储时间戳
        this.released = false;
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
        if (!released) {
            // 确保buffer不为null
            if (buffer != null) {
                MemoryUtil.memFree(buffer);
            }
            released = true;
        }
    }
}
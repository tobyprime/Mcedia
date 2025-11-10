// VideoFrame.java
package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoFrame implements Closeable {
    public final int width;
    public final int height;
    public final long ptsUs; // Presentation Timestamp in Microseconds
    private boolean released;
    public ByteBuffer buffer;

    @Nullable
    private final VideoFramePool pool;

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

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

    /**
     * 创建一个用于预热 GPU 渲染管线的全黑视频帧
     * @param width 视频宽度
     * @param height 视频高度
     * @return 一个新的 VideoFrame 实例，其 ByteBuffer 需要手动管理（例如通过 try-with-resources）
     */
    public static VideoFrame createBlack(int width, int height) {
        // 分配一块新的堆外内存，默认填充为0（即黑色 RGBA(0,0,0,0)）
        ByteBuffer blackBuffer = MemoryUtil.memAlloc(width * height * 4);
        // 因为没有池，所以 pool 参数传 null
        return new VideoFrame(blackBuffer, width, height, -1L, null);
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
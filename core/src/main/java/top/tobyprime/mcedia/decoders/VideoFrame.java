package top.tobyprime.mcedia.decoders;

import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.nio.ByteBuffer;

public class VideoFrame implements Closeable {
    public final ByteBuffer buffer;  // 原始 RGBA 像素数据（连续）
    public final int width;
    public final int height;
    private boolean released;
    public boolean shouldFree =true;

    public VideoFrame(ByteBuffer buffer, int width, int height, boolean shouldFree   ) {
        this.buffer = buffer;
        this.width = width;
        this.height = height;
        this.released = false;
        this.shouldFree = shouldFree;
    }

    @Override
    public void close() {
        if (!released) {
            if (shouldFree)
            MemoryUtil.memFree(buffer);
            released = true;
        }
    }
}
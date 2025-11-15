package top.tobyprime.mcedia.core;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VideoFramePool {
    private final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final int bufferSize;

    public VideoFramePool(int capacity, int bufferSize) {
        this.bufferSize = bufferSize;
        for (int i = 0; i < capacity; i++) {
            pool.offer(MemoryUtil.memAlloc(bufferSize));
        }
    }

    /**
     * 从池中获取一个可用的ByteBuffer。如果池为空，则创建一个新的。
     */
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            // 池耗尽时动态创建一个，以防万一
            return MemoryUtil.memAlloc(bufferSize);
        }
        buffer.clear(); // 重置 position 和 limit，准备写入
        return buffer;
    }

    /**
     * 将使用完毕的ByteBuffer归还到池中。
     */
    public void release(ByteBuffer buffer) {
        if (buffer != null && buffer.capacity() == bufferSize) {
            pool.offer(buffer);
        } else if (buffer != null) {
            // 如果buffer大小不对（例如动态创建的），则直接释放
            MemoryUtil.memFree(buffer);
        }
    }

    public void close() {
        while (!pool.isEmpty()) {
            MemoryUtil.memFree(pool.poll());
        }
    }
}
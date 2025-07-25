package top.tobyprime.mcedia;

import org.lwjgl.system.MemoryUtil;

import java.nio.*;

public class BufferHelper {
    public static Buffer cloneBuffer(Buffer src) {
        switch (src) {
            case ByteBuffer byteBuf -> {
                ByteBuffer dup = byteBuf.duplicate();
                dup.clear();
                ByteBuffer copy = MemoryUtil.memAlloc(dup.remaining());
                copy.put(dup);
                copy.flip();
                return copy;
            }
            case ShortBuffer shortBuf -> {
                ShortBuffer dup = shortBuf.duplicate();
                dup.clear();
                ShortBuffer copy = MemoryUtil.memAllocShort(dup.remaining());
                copy.put(dup);
                copy.flip();
                return copy;
            }
            case IntBuffer intBuf -> {
                IntBuffer dup = intBuf.duplicate();
                dup.clear();
                IntBuffer copy = MemoryUtil.memAllocInt(dup.remaining());
                copy.put(dup);
                copy.flip();
                return copy;
            }
            case FloatBuffer floatBuf -> {
                FloatBuffer dup = floatBuf.duplicate();
                dup.clear();
                FloatBuffer copy = MemoryUtil.memAllocFloat(dup.remaining());
                copy.put(dup);
                copy.flip();
                return copy;
            }
            case LongBuffer longBuf -> {
                LongBuffer dup = longBuf.duplicate();
                dup.clear();
                LongBuffer copy = MemoryUtil.memAllocLong(dup.remaining());
                copy.put(dup);
                copy.flip();
                return copy;
            }
            case DoubleBuffer doubleBuf -> {
                DoubleBuffer dup = doubleBuf.duplicate();
                dup.clear();
                DoubleBuffer copy = MemoryUtil.memAllocDouble(dup.remaining());
                copy.put(dup);
                copy.flip();
                return copy;
            }
            default -> throw new IllegalArgumentException("Unsupported buffer type: " + src.getClass());
        }
    }
}

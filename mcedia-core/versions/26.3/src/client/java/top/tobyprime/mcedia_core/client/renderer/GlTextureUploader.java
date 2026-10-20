package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.backend.opengl.GlConst;
import com.mojang.renderpearl.backend.opengl.GlStateManager;

import java.nio.ByteBuffer;

public final class GlTextureUploader implements TextureUploader {
    private static final int GL_MAP_INVALIDATE_BUFFER_BIT = 0x0008;
    private static final int GL_MAP_UNSYNCHRONIZED_BIT = 0x0020;
    private static final int GL_STREAM_DRAW = 0x88E0;

    private final int[] pbo = new int[2];
    private final int[] pboAllocSize = new int[2];
    private boolean closed;
    private boolean initialized;

    public GlTextureUploader() {
        RenderSystem.assertOnRenderThread();
        this.pbo[0] = GlStateManager._glGenBuffers();
        this.pbo[1] = GlStateManager._glGenBuffers();
    }

    @Override
    public void upload(int textureId, int width, int height, ByteBuffer data, int index) {
        RenderSystem.assertOnRenderThread();
        if (closed || !data.hasRemaining()) {
            return;
        }

        int slot = index & 1;
        int writePbo = pbo[slot];
        int readPbo = pbo[1 - slot];
        int requiredBytes = data.remaining();

        // 首次上传预分配两个 PBO 存储，使 texSubImage2D 从 readPbo 读取时有有效数据
        if (!initialized) {
            for (int i = 0; i < 2; i++) {
                GlStateManager._glBindBuffer(GlConst.GL_PIXEL_UNPACK_BUFFER, pbo[i]);
                GlStateManager._glBufferData(GlConst.GL_PIXEL_UNPACK_BUFFER, requiredBytes, GL_STREAM_DRAW);
                pboAllocSize[i] = requiredBytes;
            }
            initialized = true;
        }

        // Step 1: 写入 writePbo (当前数据)
        if (requiredBytes > pboAllocSize[slot]) {
            GlStateManager._glBindBuffer(GlConst.GL_PIXEL_UNPACK_BUFFER, writePbo);
            GlStateManager._glBufferData(GlConst.GL_PIXEL_UNPACK_BUFFER, requiredBytes, GL_STREAM_DRAW);
            pboAllocSize[slot] = requiredBytes;
        } else {
            GlStateManager._glBindBuffer(GlConst.GL_PIXEL_UNPACK_BUFFER, writePbo);
        }

        int access = GlConst.GL_MAP_WRITE_BIT
                | GL_MAP_INVALIDATE_BUFFER_BIT
                | GL_MAP_UNSYNCHRONIZED_BIT;
        ByteBuffer mapped = GlStateManager._glMapBufferRange(
                GlConst.GL_PIXEL_UNPACK_BUFFER, 0, requiredBytes, access);
        if (mapped != null) {
            mapped.put(data);
            GlStateManager._glUnmapBuffer(GlConst.GL_PIXEL_UNPACK_BUFFER);
        } else {
            data.rewind();
            GlStateManager._glBufferData(GlConst.GL_PIXEL_UNPACK_BUFFER, data, GL_STREAM_DRAW);
        }

        // Step 2: 从 readPbo 做 DMA 上传（读写分离，避免驱动在 _glUnmapBuffer 同步等待）
        if (requiredBytes > pboAllocSize[1 - slot]) {
            GlStateManager._glBindBuffer(GlConst.GL_PIXEL_UNPACK_BUFFER, readPbo);
            GlStateManager._glBufferData(GlConst.GL_PIXEL_UNPACK_BUFFER, requiredBytes, GL_STREAM_DRAW);
            pboAllocSize[1 - slot] = requiredBytes;
        }

        GlStateManager._bindTexture(textureId);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_ALIGNMENT, 4);

        GlStateManager._glBindBuffer(GlConst.GL_PIXEL_UNPACK_BUFFER, readPbo);
        GlStateManager._texSubImage2D(
            GlConst.GL_TEXTURE_2D,
            0,
            0, 0,
            width, height,
            GlConst.GL_RGBA,
            GlConst.GL_UNSIGNED_BYTE,
            0L
        );

        GlStateManager._glBindBuffer(GlConst.GL_PIXEL_UNPACK_BUFFER, 0);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (!closed) {
            closed = true;
            GlStateManager._glBindBuffer(GlConst.GL_PIXEL_UNPACK_BUFFER, 0);
            GlStateManager._glDeleteBuffers(pbo[0]);
            GlStateManager._glDeleteBuffers(pbo[1]);
        }
    }
}

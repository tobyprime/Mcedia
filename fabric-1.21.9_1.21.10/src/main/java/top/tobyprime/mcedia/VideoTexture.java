package top.tobyprime.mcedia;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.decoders.VideoFrame;
import top.tobyprime.mcedia.interfaces.ITexture;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;

public class VideoTexture extends AbstractTexture implements ITexture {
    private final Logger logger = LoggerFactory.getLogger(VideoTexture.class);
    // PBO 双缓冲
    private final int[] pboIds = new int[2];
    private int width;
    private int height;
    private final ResourceLocation resourceLocation;
    private int pboIndex = 0;
    private boolean pboInitialized = false;

    public VideoTexture(ResourceLocation id) {
        super();
        this.resourceLocation = id;
        Minecraft.getInstance().getTextureManager().register(id, this);
        setSize(1920, 1080);
    }

    public void setSize(int width, int height) {
        if (this.width != width || this.height != height) {
            resize(width, height);
        }
    }

    public void resize(int width, int height) {
        logger.info("修改尺寸 {}x{}", width, height);
        this.width = width;
        this.height = height;

        GpuDevice gpuDevice = RenderSystem.getDevice();

        int usage = GpuTexture.USAGE_TEXTURE_BINDING
                | GpuTexture.USAGE_RENDER_ATTACHMENT
                | GpuTexture.USAGE_COPY_SRC
                | GpuTexture.USAGE_COPY_DST;

        this.texture = gpuDevice.createTexture(resourceLocation::toString,
                usage, TextureFormat.RGBA8, width, height, 1, 1);
        this.textureView = gpuDevice.createTextureView(this.texture);
        this.setClamp(true);
        this.setFilter(true, false);

        releasePbo();
        initPBO();
        pboInitialized = true;
    }

    private void initPBO() {
        glGenBuffers(pboIds);
        for (int i = 0; i < 2; i++) {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[i]);
            glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4L, GL_STREAM_DRAW);
        }
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    public ResourceLocation getResourceLocation() {
        return this.resourceLocation;
    }


    /**
     * 异步上传 RGBA buffer 使用 PBO 双缓冲
     */
    public void uploadBuffer(ByteBuffer buffer) {
        buffer.rewind();
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer 必须是 direct 类型");
        }
        if (this.texture == null) {
            return;
        }

        // 当前 PBO
        int currPBO = pboIds[pboIndex];
        int nextPBO = pboIds[(pboIndex + 1) % 2];

        // 绑定当前 PBO
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, currPBO);

        // 映射写入数据
        ByteBuffer mapped = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY);
        if (mapped == null) {
            throw new IllegalStateException("Could not map buffer to glMapBuffer: " + glGetError());
        }

        mapped.put(buffer);
        mapped.flip();
        glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);

        // 绑定纹理 + 上一 PBO 异步上传到 GPU
        GlStateManager._bindTexture(((GlTexture) this.texture).glId());
        GlStateManager._pixelStore(GL_UNPACK_ALIGNMENT, 1);
        GlStateManager._pixelStore(GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL_UNPACK_SKIP_ROWS, 0);

        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, nextPBO);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL_RGBA, GL_UNSIGNED_BYTE, 0);

        // 切换 PBO
        pboIndex = (pboIndex + 1) % 2;

        // 解绑
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    @Override
    public void upload(@Nullable VideoFrame frame) {
        if (frame == null) return;

        setSize(frame.width, frame.height);
        RenderSystem.assertOnRenderThread();


        uploadBuffer(frame.buffer);
        frame.close();
    }

    public void releasePbo() {
        // 删除 PBO
        if (pboInitialized) {
            glDeleteBuffers(pboIds);
            pboInitialized = false;
        }
    }

    public void close() {
        releasePbo();
        super.close();
    }
}

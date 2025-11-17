package top.tobyprime.mcedia;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.decoders.VideoFrame;
import top.tobyprime.mcedia.interfaces.ITexture;

import java.nio.ByteBuffer;

import static com.mojang.blaze3d.platform.GlConst.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;

public class VideoTexture extends AbstractTexture implements ITexture {
    private final Logger logger = LoggerFactory.getLogger(VideoTexture.class);
    private int width;
    private int height;
    // PBO 双缓冲
    private final int[] pboIds = new int[2];
    private ResourceLocation resourceLocation;
    private int pboIndex = 0;
    private boolean pboInitialized = false;

    public VideoTexture(ResourceLocation id) {
        super();
        this.resourceLocation = id;
        Minecraft.getInstance().getTextureManager().register(id, this);
        setSize(1920,1080);
    }

    public void setSize(int width,int height) {
        if (this.width != width || this.height != height) {
            resize(width, height);
        }
    }

    public void resize(int width, int height) {
        logger.info("修改尺寸 {}x{} -> {}x{}", this.width, this.height, width, height);

        this.width = width;
        this.height = height;
        this.bind();

        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);

        this.setFilter(true, false);
        releasePbo();
        initPBO();
        pboInitialized = true;
    }

    private void initPBO() {
        glGenBuffers(pboIds);
        for (int i = 0; i < 2; i++) {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[i]);
            glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4, GL_STREAM_DRAW);
        }
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    public ResourceLocation getResourceLocation(){
        return this.resourceLocation;
    }

    @Override
    public void load(ResourceManager resourceManager) {
        // 不需要加载资源
    }

    /**
     * 异步上传 RGBA buffer 使用 PBO 双缓冲
     */
    public void uploadBuffer(ByteBuffer buffer) {
        buffer.rewind();
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer 必须是 direct 类型");
        }

        // 当前 PBO
        int currPBO = pboIds[pboIndex];
        int nextPBO = pboIds[(pboIndex + 1) % 2];

        // 绑定当前 PBO
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, currPBO);

        // 映射写入数据
        ByteBuffer mapped = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY);
        mapped.put(buffer);
        mapped.flip();
        glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);

        // 绑定纹理 + 上一 PBO 异步上传到 GPU
        this.bind();
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
        this.bind();
        RenderSystem.assertOnRenderThreadOrInit();

        GlStateManager._pixelStore(GL_UNPACK_ALIGNMENT, 1);
        GlStateManager._pixelStore(GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL_UNPACK_SKIP_ROWS, 0);

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

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
import top.tobyprime.mcedia.core.VideoFrame;
import top.tobyprime.mcedia.interfaces.ITexture;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static com.mojang.blaze3d.platform.GlConst.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL11.*;

public class VideoTexture extends AbstractTexture implements ITexture {
    private final Logger logger = LoggerFactory.getLogger(VideoTexture.class);
    private int width;
    private int height;
    ResourceLocation resourceLocation;

    public VideoTexture(ResourceLocation id) {
        super();
        this.resourceLocation = id;
        Minecraft.getInstance().getTextureManager().register(id, this);
        setSize(100,100);
    }

    public void setSize(int width,int height) {
        if (this.width != width || this.height != height) {
            resize(width, height);
        }
    }

    public void resize(int width, int height) {
        logger.info("修改尺寸 {}x{} -> {}x{}",this.width,this.height ,width, height);

        this.width = width;
        this.height = height;
        this.bind();

        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);

        this.setFilter(true, false);
    }
    public ResourceLocation getResourceLocation(){
        return this.resourceLocation;
    }

    @Override
    public void load(ResourceManager resourceManager) {

    }

    public void uploadBuffer(ByteBuffer buffer) {
        buffer.rewind();
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer 必须是 direct 类型");
        }
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer);
    }

    @Override
    public void upload(@Nullable VideoFrame frame) {
        if (frame == null) {
            return;
        }
        setSize(frame.width,frame.height);
        this.bind();
        RenderSystem.assertOnRenderThreadOrInit();
        GlStateManager._pixelStore(GL_UNPACK_ALIGNMENT, 1);     // 每行像素对齐方式
        GlStateManager._pixelStore(GL_UNPACK_ROW_LENGTH, 0);    // 行长度（0 = 紧密排列）
        GlStateManager._pixelStore(GL_UNPACK_SKIP_ROWS, 0);     // 跳过的行数

        uploadBuffer(frame.buffer);
        frame.close();
    }
}

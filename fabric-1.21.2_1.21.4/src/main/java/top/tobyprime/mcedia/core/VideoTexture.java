package top.tobyprime.mcedia.core;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static com.mojang.blaze3d.platform.GlConst.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL11.*;

public class VideoTexture extends AbstractTexture {
    private final Logger logger = LoggerFactory.getLogger(VideoTexture.class);
    private int width;
    private int height;
    ResourceLocation id;

    public VideoTexture(ResourceLocation id) {
        super();
        this.id = id;
        setSize(100,100);
    }

    public void setSize(int width,int height) {
        if (this.width != width || this.height != height) {
            resize(width, height);
        }
    }

    public void resize(int width, int height) {
        logger.info("修改尺寸 {}x{}", width, height);

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

    public void uploadData(ByteBuffer buffer){
        buffer.rewind();
        buffer.order(java.nio.ByteOrder.nativeOrder());
        this.bind();
        RenderSystem.assertOnRenderThreadOrInit();
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("ByteBuffer 必须是 direct 类型");
        }

        GlStateManager._pixelStore(GL_UNPACK_ALIGNMENT, 1);     // 每行像素对齐方式
        GlStateManager._pixelStore(GL_UNPACK_ROW_LENGTH, 0);    // 行长度（0 = 紧密排列）
        GlStateManager._pixelStore(GL_UNPACK_SKIP_ROWS, 0);     // 跳过的行数

        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                GlConst.GL_RGBA, GL_UNSIGNED_BYTE, buffer);

    }

    @Override
    public void load(ResourceManager resourceManager) {

    }
}

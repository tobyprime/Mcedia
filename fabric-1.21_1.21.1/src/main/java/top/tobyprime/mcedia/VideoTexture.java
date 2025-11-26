package top.tobyprime.mcedia;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.VideoFrame;
import top.tobyprime.mcedia.interfaces.ITexture;

import java.io.IOException;

public class VideoTexture extends AbstractTexture implements ITexture {
    private final Logger logger = LoggerFactory.getLogger(VideoTexture.class);
    private final ResourceLocation resourceLocation;
    private volatile boolean isInitialized = false;
    private int width = -1;
    private int height = -1;

    public VideoTexture(ResourceLocation id) {
        super();
        this.resourceLocation = id;
        Minecraft.getInstance().getTextureManager().register(id, this);
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
    }

    public boolean isInitialized() {
        return this.isInitialized;
    }

    public void prepareAndPrewarm(int width, int height, Runnable onReadyCallback) {
        if (width <= 0 || height <= 0) {
            logger.warn("尝试使用无效的尺寸 {}x{} 准备纹理", width, height);
            return;
        }

        RenderSystem.assertOnRenderThread();
        if (this.getId() == -1 || this.width != width || this.height != height) {
            this.width = width;
            this.height = height;

            TextureUtil.prepareImage(this.getId(), 0, width, height);

            this.bind();
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }

        try (var blackFrame = VideoFrame.createBlack(width, height)) {
            upload(blackFrame);
        }

        this.isInitialized = true;
        if (onReadyCallback != null) {
            onReadyCallback.run();
        }
    }

    @Override
    public void upload(VideoFrame frame) {
        RenderSystem.assertOnRenderThread();
        if (!this.isInitialized || this.getId() == -1 || frame.buffer == null || this.width != frame.width || this.height != frame.height) {
            return;
        }

        this.bind();
        frame.buffer.rewind();

        GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                0,
                0,
                frame.width,
                frame.height,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                frame.buffer
        );
    }

    @Override
    public void close() {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(this::closeInternal);
        } else {
            closeInternal();
        }
    }

    private void closeInternal() {
        RenderSystem.assertOnRenderThread();
        super.close();
        this.isInitialized = false;
        this.width = -1;
        this.height = -1;
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }
}
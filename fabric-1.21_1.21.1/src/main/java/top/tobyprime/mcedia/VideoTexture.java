package top.tobyprime.mcedia;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
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

    // PBO 状态
    private int pboId = -1;
    private int pboSize = -1;

    public VideoTexture(ResourceLocation id) {
        super();
        this.resourceLocation = id;
        Minecraft.getInstance().getTextureManager().register(id, this);
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {}

    public boolean isInitialized() {
        return this.isInitialized;
    }

    private void ensurePbo(int size) {
        if (pboId == -1 || pboSize < size) {
            if (pboId != -1) GL15.glDeleteBuffers(pboId);
            pboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pboId);
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, size, GL15.GL_STREAM_DRAW);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            pboSize = size;
        }
    }

    public void prepareAndPrewarm(int width, int height, Runnable onReadyCallback) {
        if (width <= 0 || height <= 0) return;

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

            ensurePbo(width * height * 4);
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
        int size = frame.width * frame.height * 4;
        ensurePbo(size);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pboId);
        frame.buffer.rewind();
        GL15.glBufferSubData(GL21.GL_PIXEL_UNPACK_BUFFER, 0, frame.buffer);
        this.bind();
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, frame.width, frame.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
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
        if (pboId != -1) {
            GL15.glDeleteBuffers(pboId);
            pboId = -1;
        }
        this.isInitialized = false;
        this.width = -1;
        this.height = -1;
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }
}
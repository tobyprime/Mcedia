package top.tobyprime.mcedia;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.VideoFrame;
import top.tobyprime.mcedia.interfaces.ITexture;

public class VideoTexture extends AbstractTexture implements ITexture {
    private final Logger logger = LoggerFactory.getLogger(VideoTexture.class);
    private final ResourceLocation resourceLocation;
    private volatile boolean isInitialized = false;
    private int width = -1;
    private int height = -1;
    @Nullable
    private GpuTexture gpuTexture;
    @Nullable private GpuTextureView gpuTextureView;

    public VideoTexture(ResourceLocation id) {
        super();
        this.resourceLocation = id;
        Minecraft.getInstance().getTextureManager().register(id, this);
    }

    public boolean isInitialized() {
        return this.isInitialized;
    }

    public void prepareAndPrewarm(int width, int height, Runnable onReadyCallback) {
        if (width <= 0 || height <= 0) {
            logger.warn("尝试使用无效的尺寸 {}x{} 准备纹理", width, height);
            return;
        }

        Minecraft.getInstance().execute(() -> {
            RenderSystem.assertOnRenderThread();
            try {
                if (this.texture == null || this.width != width || this.height != height) {
                    resize(width, height);
                }

                try (var blackFrame = VideoFrame.createBlack(width, height)) {
                    upload(blackFrame);
                }

                this.isInitialized = true;

                if (onReadyCallback != null) {
                    onReadyCallback.run();
                }
            } catch (Exception e) {
                logger.error("在渲染线程上准备纹理失败", e);
            }
        });
    }

    private void resize(int width, int height) {
        RenderSystem.assertOnRenderThread();
        logger.info("正在为视频纹理分配/重新分配显存: {}x{}", width, height);

        this.closeInternal();

        GpuDevice gpuDevice = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;

        this.texture = gpuDevice.createTexture(this.resourceLocation.toString(), usage, TextureFormat.RGBA8, width, height, 1, 1);
        this.textureView = gpuDevice.createTextureView(this.texture);
        this.width = width;
        this.height = height;

        this.setClamp(true);
        this.setFilter(true, false);
    }

    @Override
    public void upload(VideoFrame frame) {
        RenderSystem.assertOnRenderThread();
        if (!this.isInitialized || this.texture == null || frame.buffer == null || this.width != frame.width || this.height != frame.height) {
            return;
        }

        GpuDevice gpuDevice = RenderSystem.getDevice();
        frame.buffer.rewind();

        CommandEncoder commandEncoder = gpuDevice.createCommandEncoder();
        commandEncoder.writeToTexture(
                this.texture,
                frame.buffer,
                NativeImage.Format.RGBA,
                0,
                0,
                0,
                0,
                frame.width,
                frame.height
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
        if (this.gpuTextureView != null) {
            this.gpuTextureView.close();
            this.gpuTextureView = null;
        }
        if (this.gpuTexture != null) {
            this.gpuTexture.close();
            this.gpuTexture = null;
        }

        this.isInitialized = false;
        this.width = -1;
        this.height = -1;
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }
}
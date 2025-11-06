package top.tobyprime.mcedia;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.VideoFrame;
import top.tobyprime.mcedia.interfaces.ITexture;

public class VideoTexture extends AbstractTexture implements ITexture {
    private final Logger logger = LoggerFactory.getLogger(VideoTexture.class);
    ResourceLocation resourceLocation;

    public VideoTexture(ResourceLocation id) {
        super();
        Minecraft.getInstance().getTextureManager().register(id, this);
        this.resourceLocation = id;
        setSize(100,100);
    }

    public void setSize(int width,int height) {
        if (texture==null || texture.getWidth(0) != width || texture.getHeight(0) != height) {
            resize(width, height);
        }
    }

    public void resize(int width, int height) {
        logger.info("修改尺寸 {}x{}",width,height);
        GpuDevice gpuDevice = RenderSystem.getDevice();

        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;
        this.texture = gpuDevice.createTexture(this.resourceLocation::toString, usage, TextureFormat.RGBA8, width, height, 1, 1);
        this.textureView = gpuDevice.createTextureView(this.texture);
        this.setClamp(true);
        this.setFilter(true, false);
    }

    public void upload(VideoFrame frame) {
        if (this.texture == null) {
            return;
        }
        setSize(frame.width, frame.height);
        GpuDevice gpuDevice = RenderSystem.getDevice();
        frame.buffer.rewind();
        gpuDevice.createCommandEncoder().writeToTexture(texture, frame.buffer.asIntBuffer(), NativeImage.Format.RGBA, 0, 0, 0, 0, this.texture.getWidth(0), this.texture.getHeight(0));
        frame.close();

    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }
}
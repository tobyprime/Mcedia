package top.tobyprime.mcedia.core;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;

public class VideoTexture extends AbstractTexture {
    private final Logger logger = LoggerFactory.getLogger(VideoTexture.class);
    ResourceLocation id;

    public VideoTexture(ResourceLocation id) {
        super();
        this.id = id;
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

        this.texture = gpuDevice.createTexture(this.id::toString, TextureFormat.RGBA8, width, height, 1);
        gpuDevice.createCommandEncoder().clearColorTexture(this.getTexture(), -1);
        this.setClamp(true);
        this.setFilter(true, false);
    }

    public void uploadData(ByteBuffer buffer){
        if (this.texture == null) {
            return;
        }
        buffer.rewind();
        buffer.order(java.nio.ByteOrder.nativeOrder());
        GpuDevice gpuDevice = RenderSystem.getDevice();

        gpuDevice.createCommandEncoder().writeToTexture(texture, buffer.asIntBuffer(), NativeImage.Format.RGBA, 0,0,0, this.texture.getWidth(0), this.texture.getHeight(0));
    }

}

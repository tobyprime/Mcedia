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
    }

    /**
     * [最终修复] 新的统一入口方法，负责准备和预热纹理。
     * 它将所有 GPU 操作安全地调度到渲染线程，并保证执行顺序。
     */
    /**
     * [最终修复] 新的统一入口方法，接受一个回调函数。
     */
    public void prepareAndPrewarm(int width, int height, Runnable onReadyCallback) {
        Minecraft.getInstance().execute(() -> {
            // 这整个 lambda 表达式现在都在渲染线程上安全执行

            // 调整纹理大小
            if (texture == null || texture.getWidth(0) != width || texture.getHeight(0) != height) {
                resize(width, height);
            }

            // 上传“黑屏帧”以预热
            try (var blackFrame = VideoFrame.createBlack(width, height)) {
                uploadInternal(blackFrame);
            } catch (Exception e) {
                logger.error("在渲染线程上预热帧上传失败", e);
            }

            // 所有GPU工作已完成，执行回调，通知 PlayerAgent 可以开始渲染了
            if (onReadyCallback != null) {
                onReadyCallback.run();
            }
        });
    }

    private void resize(int width, int height) {
        logger.info("正在为视频纹理分配显存: {}x{}", width, height);

        if (this.textureView != null) this.textureView.close();
        if (this.texture != null) this.texture.close();

        GpuDevice gpuDevice = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;

        this.texture = gpuDevice.createTexture(this.resourceLocation.toString(), usage, TextureFormat.RGBA8, width, height, 1, 1);
        this.textureView = gpuDevice.createTextureView(this.texture);
        this.setClamp(true);
        this.setFilter(true, false);
    }

    /**
     * 公共的 upload 方法，供外部（PlayerAgent.render）调用。
     * 它假定自己总是在渲染线程上被调用。
     */
    @Override
    public void upload(VideoFrame frame) {
        RenderSystem.assertOnRenderThread();
        uploadInternal(frame);
    }

    /**
     * 内部的上传实现，不进行线程检查，因为它只被已在渲染线程上的代码调用。
     */
    private void uploadInternal(VideoFrame frame) {
        if (this.texture == null || this.texture.getWidth(0) != frame.width || this.texture.getHeight(0) != frame.height) {
            return; // 纹理未准备好或尺寸不匹配，安全退出
        }

        GpuDevice gpuDevice = RenderSystem.getDevice();
        frame.buffer.rewind();

        gpuDevice.createCommandEncoder().writeToTexture(
                texture, frame.buffer.asIntBuffer(), NativeImage.Format.RGBA,
                0, 0, 0, 0,
                this.texture.getWidth(0), this.texture.getHeight(0)
        );
    }

    @Override
    public void close() {
        // 确保在主线程上关闭GPU资源
        Minecraft.getInstance().execute(super::close);
    }

    public void release() {
        // AbstractTexture.release() 已经被 close() 调用
        // 如果需要额外清理，也应在主线程
        Minecraft.getInstance().execute(() -> {
            if (this.textureView != null) this.textureView.close();
            if (this.texture != null) this.texture.close();
        });
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }
}
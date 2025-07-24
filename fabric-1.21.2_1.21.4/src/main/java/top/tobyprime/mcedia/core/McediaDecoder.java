package top.tobyprime.mcedia.core;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.internal.AudioFrame;
import top.tobyprime.mcedia.internal.VideoFrame;

public class McediaDecoder extends MediaDecoder {
    public static final Logger LOGGER = LoggerFactory.getLogger(McediaDecoder.class);

    ResourceLocation videoTextureLocation;
    VideoTexture videoTexture;
    AudioSource audioSource;

    public McediaDecoder() {
        super();
        videoTextureLocation = ResourceLocation.fromNamespaceAndPath("mcedia", "player" + this.hashCode());
        videoTexture = new VideoTexture(videoTextureLocation);
        audioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);

        Minecraft.getInstance().getTextureManager().register(videoTextureLocation, videoTexture);

        audioSource.setVolume(1);
        audioSource.setMaxDistance(1000);
        audioSource.setPos(0, 0, 0);
    }

    public ResourceLocation getVideoTextureLocation() {
        return videoTextureLocation;
    }

    public void setAudioPos(float x, float y, float z) {
        this.audioSource.setPos(x, y, z);
    }

    public void setAudioVolume(float volume) {
        audioSource.setVolume(volume);
    }

    public void setAudioMaxDistance(float maxDistance) {
        audioSource.setMaxDistance(maxDistance);
    }

    @Override
    protected void processAudioFrame(AudioFrame frame) {
        this.audioSource.uploadAudioFrame(frame);
    }

    public void uploadVideoFrame(VideoFrame frame) {
        try (frame) {
            frame.buffer.rewind();
            videoTexture.setSize(frame.width, frame.height);
            videoTexture.uploadData(frame.buffer);
        } catch (Exception e) {
            LOGGER.error("Failed to upload video frame", e);
        }
    }

    public VertexConsumer createBuffer(MultiBufferSource bufferSource) {
        var frame = getBestVideoFrame();
        if (frame != null) {
            uploadVideoFrame(frame);
        }
        return bufferSource.getBuffer(RenderType.entityCutoutNoCull(this.videoTextureLocation));
    }
}

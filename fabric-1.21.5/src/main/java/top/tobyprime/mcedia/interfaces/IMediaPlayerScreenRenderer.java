package top.tobyprime.mcedia.interfaces;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import top.tobyprime.mcedia.VideoTexture;
import top.tobyprime.mcedia.core.MediaPlayer;

public interface IMediaPlayerScreenRenderer {
    void render(VideoTexture texture, MediaPlayer player, PoseStack poseStack, MultiBufferSource bufferSource, int i);
}

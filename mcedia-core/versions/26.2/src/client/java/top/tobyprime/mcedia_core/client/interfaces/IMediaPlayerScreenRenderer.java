package top.tobyprime.mcedia_core.client.interfaces;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import top.tobyprime.mcedia.api.player.MediaPlayer;
import top.tobyprime.mcedia.api.video.MediaTexture;

public interface IMediaPlayerScreenRenderer {
    void render(MediaTexture texture, MediaPlayer player, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i);
}

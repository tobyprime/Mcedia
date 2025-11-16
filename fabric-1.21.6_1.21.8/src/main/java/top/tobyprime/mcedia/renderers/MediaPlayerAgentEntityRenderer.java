package top.tobyprime.mcedia.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.entities.MediaPlayerAgentEntity;

public class MediaPlayerAgentEntityRenderer extends EntityRenderer<MediaPlayerAgentEntity, MediaPlayerScreenEntityRendererStatus> {

    public MediaPlayerAgentEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MediaPlayerScreenEntityRendererStatus status, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
        super.render(status, poseStack, multiBufferSource, i);

        status.player.uploadVideo();
        poseStack.pushPose();
        poseStack.mulPose(status.rotation);

        status.screens.forEach(screen -> screen.render(status.texture, status.player, poseStack, multiBufferSource, i));
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(MediaPlayerAgentEntity entity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public void extractRenderState(MediaPlayerAgentEntity entity, MediaPlayerScreenEntityRendererStatus status, float f) {
        super.extractRenderState(entity, status, f);
        status.player = entity.getPlayer();
        status.screens = entity.screens;
        status.texture = entity.texture;
        status.rotation = entity.rotation;
    }

    @Override
    public @NotNull MediaPlayerScreenEntityRendererStatus createRenderState() {
        return new MediaPlayerScreenEntityRendererStatus();
    }
}


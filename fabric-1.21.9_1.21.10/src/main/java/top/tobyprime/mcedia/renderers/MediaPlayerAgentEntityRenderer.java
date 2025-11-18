package top.tobyprime.mcedia.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.entities.MediaPlayerAgentEntity;

public class MediaPlayerAgentEntityRenderer extends EntityRenderer<MediaPlayerAgentEntity, MediaPlayerScreenEntityRendererStatus> {

    public MediaPlayerAgentEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }


    @Override
    public void submit(MediaPlayerScreenEntityRendererStatus status, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        super.submit(status, poseStack, submitNodeCollector, cameraRenderState);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        status.player.uploadVideo();
        poseStack.pushPose();
        poseStack.mulPose(status.rotation);

        status.screens.forEach(screen -> screen.render(status.texture, status.player, poseStack, bufferSource, status.lightCoords));
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






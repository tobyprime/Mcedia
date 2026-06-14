package top.tobyprime.mcedia_core.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import top.tobyprime.mcedia_core.client.audio.OpenAlAudioSource;

public final class DebugRendererAudioHitbox {
    private static final ResourceLocation SPEAKER_ICON = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/speaker.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(SPEAKER_ICON);
    private static final float ICON_SCALE = 0.4F;
    private static final double ICON_Y_OFFSET = 0.35D;

    private DebugRendererAudioHitbox() {
    }

    public static void submit(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera, Frustum frustum) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            return;
        }

        var cameraPos = camera.getPosition();
        for (var source : OpenAlAudioSource.getActiveSources()) {
            var pos = source.getPos();
            if (pos == null) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(iconBounds(pos.x, pos.y, pos.z))) {
                continue;
            }

            int light = LevelRenderer.getLightColor(level, BlockPos.containing(pos));
            poseStack.pushPose();
            poseStack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y + ICON_Y_OFFSET, pos.z - cameraPos.z);
            poseStack.scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);
            poseStack.mulPose(camera.rotation());
            VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
            var pose = poseStack.last();
            vertex(consumer, pose, light, 0.0F, 0, 0, 1);
            vertex(consumer, pose, light, 1.0F, 0, 1, 1);
            vertex(consumer, pose, light, 1.0F, 1, 1, 0);
            vertex(consumer, pose, light, 0.0F, 1, 0, 0);
            poseStack.popPose();
        }
    }

    private static AABB iconBounds(double x, double y, double z) {
        return new AABB(x - 0.25D, y, z - 0.25D, x + 0.25D, y + 0.5D, z + 0.25D);
    }

    private static void vertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
        vertexConsumer.addVertex(pose, x - 0.5F, y - 0.25F, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}

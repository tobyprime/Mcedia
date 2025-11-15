package top.tobyprime.mcedia.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import top.tobyprime.mcedia.VideoTexture;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.interfaces.IMediaPlayerScreenRenderer;

public class MediaPlayerScreen implements IMediaPlayerScreenRenderer {
    private static final ResourceLocation idleScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    public float Height;
    public Vector3f offset = new Vector3f(0.0f, 0.0f, 0.0f);
    private float halfW = 1.777f;

    private void renderScreen(VideoTexture texture, PoseStack poseStack, MultiBufferSource bufferSource, int i, MediaPlayer player) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull((player.getMedia() != null) ? texture.getResourceLocation() : idleScreen));

        var matrix = poseStack.last().pose();

        consumer.addVertex(matrix, -halfW, -1, 0).setLight(i).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, -1, 0).setLight(i).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, 1, 0).setLight(i).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, -halfW, 1, 0).setLight(i).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
    }

    private void renderProgressBar(PoseStack poseStack, MultiBufferSource bufferSource, float progress, int i) {
        // 绘制进度条，黑色为底，白色为进度
        // 进度条参数
        float barHeight = (float) 1 / 50;
        float barY = -1;
        float barLeft = -halfW;
        float barRight = halfW;
        float barBottom = barY - barHeight;

        // 画底色（黑色）
        VertexConsumer black = bufferSource.getBuffer(RenderType.debugQuads());
        int blackColor = 0xFF000000;
        black.addVertex(poseStack.last().pose(), barLeft, barBottom, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barRight, barBottom, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barRight, barY, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barLeft, barY, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);

        // 画进度（白色）
        float progressRight = barLeft + (barRight - barLeft) * Math.max(0, Math.min(progress, 1));
        int whiteColor = 0xFFFFFFFF;
        if (progress > 0) {
            VertexConsumer white = bufferSource.getBuffer(RenderType.debugQuads());
            white.addVertex(poseStack.last().pose(), barLeft, barBottom, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), progressRight, barBottom, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), progressRight, barY, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), barLeft, barY, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
        }
    }

    @Override
    public void render(VideoTexture texture, MediaPlayer player, PoseStack poseStack, MultiBufferSource bufferSource, int i) {
        halfW = player.getAspectRatio();
        if (halfW == 0) halfW = 1.777f;

        poseStack.pushPose();

        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.scale(Height, Height, Height);

        renderScreen(texture, poseStack, bufferSource, i, player);
        renderProgressBar(poseStack, bufferSource, player.getProgress(), i);

        poseStack.popPose();
    }
}

package top.tobyprime.mcedia.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import top.tobyprime.mcedia.Configs;
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


    private void renderDanmaku(PoseStack poseStack, MultiBufferSource bufferSource, int i, MediaPlayer player) {
        poseStack.pushPose();

        poseStack.translate(0, 0, 0.004f);
        Font font = Minecraft.getInstance().font;
        float actualLineHeight = 2F / Configs.DANMAKU_TRACKS;
        float scale = actualLineHeight / font.lineHeight;
        var actualLineWidth = halfW * 2;

        player.setDanmakuWidthPredictor(x -> {
            var actualWidth = (float) font.width(x.text) * actualLineHeight / font.lineHeight;
            return actualWidth / actualLineWidth;
        });

        var danmakus = player.updateAndGetDanmakus();

        if (danmakus == null) {

            poseStack.popPose();
            return;

        }


        int alpha = (int) (Mth.clamp(Configs.DANMAKU_OPACITY, 0, 1) * 255);
        int alphaMask = alpha << 24;


        for (var danmaku : danmakus) {
            poseStack.pushPose();
            poseStack.translate((-halfW - danmaku.width * actualLineHeight) + 2 * danmaku.position * (halfW + danmaku.width * actualLineHeight), 1F - danmaku.trackId * (2F / Configs.DANMAKU_TRACKS), 0);
            poseStack.scale(scale, -scale, scale);

            int color = alphaMask | (danmaku.danmaku.color & 0x00FFFFFF);

            font.drawInBatch(danmaku.danmaku.text, 0, 0, color, true, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, i);
            poseStack.popPose();
        }

        poseStack.popPose();
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
        renderDanmaku(poseStack, bufferSource, i, player);
        renderProgressBar(poseStack, bufferSource, player.getProgress(), i);

        poseStack.popPose();
    }
}

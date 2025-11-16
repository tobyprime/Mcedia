package top.tobyprime.mcedia.agent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.PlayerAgent;
import top.tobyprime.mcedia.client.McediaRenderTypes;
import top.tobyprime.mcedia.core.Media;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.core.Danmaku;
import top.tobyprime.mcedia.manager.DanmakuManager;

public class PlayerRenderer {
    private static final ResourceLocation idleScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    private static final ResourceLocation errorScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/error.png");
    private static final ResourceLocation loadingScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/loading.png");

    private float halfW = 1.777f;
    private long lastRenderTime = 0;

    // --- 主渲染方法 ---
    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i, PlayerAgent agent) {
        // --- 准备工作 ---
        MediaPlayer player = agent.getPlayer();
        PlayerConfigManager config = agent.getConfigManager();
        Media media = player.getMedia();

        if (media != null) {
            if (!agent.isTextureInitialized() && agent.getTexture() != null && media.getWidth() > 0 && media.getHeight() > 0) {
                agent.getTexture().prepareAndPrewarm(media.getWidth(), media.getHeight(), () -> agent.setTextureReady(true));
                agent.setTextureInitialized(true);
            }
            boolean shouldBePaused = !state.showBasePlate;
            if (shouldBePaused && !media.isPaused()) {
                player.pause();
                agent.setPausedByBasePlate(true);
            } else if (!shouldBePaused && media.isPaused() && agent.isPausedByBasePlate()) {
                player.play();
                agent.setPausedByBasePlate(false);
            }
        }
        var size = state.scale * config.scale;
        var volumeFactor = 1 - (state.leftArmPose.x() < 0 ? -state.leftArmPose.x() / 360f : (360.0f - state.leftArmPose.x()) / 360f);
        var speedFactor = state.leftArmPose.y() < 0 ? -state.leftArmPose.y() / 360f : (360.0f - state.leftArmPose.y()) / 360f;
        float speed = speedFactor < 0.1f ? 1f : (speedFactor > 0.5f ? 1f - (1f - speedFactor) * 2f : (speedFactor - 0.1f) / 0.4f * 8f);
        player.setSpeed(speed);

        float yRotRadians = (float) -Math.toRadians(state.yRot);
        var primaryAudioOffsetRotated = new Vector3f(config.audioOffsetX, config.audioOffsetY, config.audioOffsetZ).rotateY(yRotRadians);
        agent.getPrimaryAudioSource().setVolume(config.audioMaxVolume * volumeFactor);
        agent.getPrimaryAudioSource().setRange(config.audioRangeMin, config.audioRangeMax);
        agent.getPrimaryAudioSource().setPos(((float) state.x + primaryAudioOffsetRotated.x), ((float) state.y + primaryAudioOffsetRotated.y), ((float) state.z + primaryAudioOffsetRotated.z));

        if (config.isSecondarySourceActive) {
            var secondaryAudioOffsetRotated = new Vector3f(config.audioOffsetX2, config.audioOffsetY2, config.audioOffsetZ2).rotateY(yRotRadians);
            agent.getSecondaryAudioSource().setVolume(config.audioMaxVolume2 * volumeFactor);
            agent.getSecondaryAudioSource().setRange(config.audioRangeMin2, config.audioRangeMax2);
            agent.getSecondaryAudioSource().setPos(((float) state.x + secondaryAudioOffsetRotated.x), ((float) state.y + secondaryAudioOffsetRotated.y), ((float) state.z + secondaryAudioOffsetRotated.z));
        }

        long now = System.nanoTime();
        if (lastRenderTime == 0) lastRenderTime = now;
        float deltaTime = (now - lastRenderTime) / 1_000_000_000.0f;
        lastRenderTime = now;

        if (media != null && media.isPlaying() && config.danmakuEnable) {
            agent.getDanmakuManager().update(deltaTime, media.getDurationUs(), speed,
                    this.halfW, config.danmakuFontScale, config.danmakuSpeedScale,
                    config.showScrollingDanmaku, config.showTopDanmaku, config.showBottomDanmaku);
        }

        synchronized (player) {
            if (media != null) {
                media.uploadVideo();
                if (media.getHeight() > 0) halfW = media.getAspectRatio();
            } else halfW = 1.777f;
        }

        int finalLightValue;
        if (config.customLightLevel != -1) {
            finalLightValue = LightTexture.pack(config.customLightLevel, config.customLightLevel);
        } else {
            finalLightValue = i;
        }

        // --- 渲染变换与绘制 ---
        poseStack.pushPose();
        try {
            poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.yRot), 0, 0));
            poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.headPose.x()), (float) Math.toRadians(-state.headPose.y()), (float) Math.toRadians(-state.headPose.z())));
            poseStack.translate(config.offsetX, config.offsetY + 1.02 * state.scale, config.offsetZ + 0.6 * state.scale);
            poseStack.scale(size, size, size);

            renderScreen(poseStack, bufferSource, finalLightValue, agent);

            if (player.getMedia() != null && agent.isTextureReady()) {
                if(config.danmakuEnable) {
                    renderDanmakuWithClipping(poseStack, bufferSource, finalLightValue, agent);
                }
                renderProgressBar(poseStack, bufferSource, player.getProgress(), finalLightValue);
            }
        } finally {
            poseStack.popPose();
        }
    }

    // --- 辅助渲染方法 ---

    private void renderScreen(PoseStack poseStack, MultiBufferSource bufferSource, int i, PlayerAgent agent) {
        if (agent.getTexture() == null) return;
        ResourceLocation screenTexture;
        switch (agent.getStatus()) {
            case LOADING:
                screenTexture = loadingScreen;
                break;
            case FAILED:
                screenTexture = errorScreen;
                break;
            case PLAYING:
                if (agent.getPlayer().isBuffering()) {
                    screenTexture = loadingScreen;
                } else if (agent.getPlayer().getMedia() != null && agent.isTextureReady()) {
                    screenTexture = agent.getTexture().getResourceLocation();
                } else {
                    screenTexture = idleScreen;
                }
                break;
            case IDLE:
            default:
                screenTexture = idleScreen;
                break;
        }
        RenderType renderType = RenderType.entityCutoutNoCull(screenTexture);

        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        var matrix = poseStack.last().pose();

        consumer.addVertex(matrix, -halfW, -1, 0).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, halfW, -1, 0).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, halfW, 1, 0).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, -halfW, 1, 0).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
    }

    private void renderProgressBar(PoseStack poseStack, MultiBufferSource bufferSource, float progress, int i) {
        float barHeight = 1f / 50f;
        float barY = -1f;
        float barLeft = -halfW;
        float barRight = halfW;
        float zOffsetBg = 0.002f;
        float zOffsetFg = 0.003f;

        VertexConsumer consumer = bufferSource.getBuffer(McediaRenderTypes.PROGRESS_BAR);

        consumer.addVertex(poseStack.last().pose(), barLeft, barY - barHeight, zOffsetBg).setColor(0.0f, 0.0f, 0.0f, 0.5f).setLight(i);
        consumer.addVertex(poseStack.last().pose(), barRight, barY - barHeight, zOffsetBg).setColor(0.0f, 0.0f, 0.0f, 0.5f).setLight(i);
        consumer.addVertex(poseStack.last().pose(), barRight, barY, zOffsetBg).setColor(0.0f, 0.0f, 0.0f, 0.5f).setLight(i);
        consumer.addVertex(poseStack.last().pose(), barLeft, barY, zOffsetBg).setColor(0.0f, 0.0f, 0.0f, 0.5f).setLight(i);

        if (progress > 0) {
            float progressRight = barLeft + (barRight - barLeft) * Math.max(0, Math.min(progress, 1));
            consumer.addVertex(poseStack.last().pose(), barLeft, barY - barHeight, zOffsetFg).setColor(1.0f, 1.0f, 1.0f, 1.0f).setLight(i);
            consumer.addVertex(poseStack.last().pose(), progressRight, barY - barHeight, zOffsetFg).setColor(1.0f, 1.0f, 1.0f, 1.0f).setLight(i);
            consumer.addVertex(poseStack.last().pose(), progressRight, barY, zOffsetFg).setColor(1.0f, 1.0f, 1.0f, 1.0f).setLight(i);
            consumer.addVertex(poseStack.last().pose(), barLeft, barY, zOffsetFg).setColor(1.0f, 1.0f, 1.0f, 1.0f).setLight(i);
        }
    }

    private void renderDanmakuWithClipping(PoseStack poseStack, MultiBufferSource bufferSource, int light, PlayerAgent agent) {
        renderDanmakuText(poseStack, bufferSource, light, agent);
    }

    private void renderDanmakuText(PoseStack poseStack, MultiBufferSource bufferSource, int light, PlayerAgent agent) {
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.004f);

        Font font = Minecraft.getInstance().font;
        PlayerConfigManager config = agent.getConfigManager();
        DanmakuManager danmakuManager = agent.getDanmakuManager();
        float videoHeightUnits = 2.0f;

        int dynamicTrackCount = (int) (McediaConfig.getDanmakuBaseTrackCount() / config.danmakuFontScale);
        if (dynamicTrackCount < 1) dynamicTrackCount = 1;

        float desiredDanmakuHeight = videoHeightUnits / dynamicTrackCount;
        float scale = desiredDanmakuHeight / font.lineHeight;

        poseStack.scale(scale, -scale, scale);

        float scaledScreenWidth = (halfW * 2) / scale;
        float scaledScreenHeight = videoHeightUnits / scale;
        float screenLeftEdge = -scaledScreenWidth / 2;
        float screenRightEdge = scaledScreenWidth / 2;

        int alpha = (int) (Mth.clamp(config.danmakuOpacity, 0, 1) * 255);
        int alphaMask = alpha << 24;

        for (Danmaku danmaku : danmakuManager.getActiveDanmaku()) {
            float theoreticalXPos = -scaledScreenWidth / 2 + danmaku.x * scaledScreenWidth;

            float yPos;
            if (danmaku.type == Danmaku.DanmakuType.BOTTOM) {
                yPos = scaledScreenHeight / 2 - (danmaku.y * config.danmakuDisplayArea * scaledScreenHeight) - font.lineHeight;
            } else {
                yPos = -scaledScreenHeight / 2 + (danmaku.y * config.danmakuDisplayArea * scaledScreenHeight);
            }

            int finalColor = alphaMask | (danmaku.color & 0x00FFFFFF);

            if (danmaku.type == Danmaku.DanmakuType.SCROLLING) {
                float danmakuWidthInPixels = font.width(danmaku.text);
                if (theoreticalXPos > screenRightEdge || theoreticalXPos + danmakuWidthInPixels < screenLeftEdge) {
                    continue;
                }

                String textToRender = danmaku.text;
                float finalXPos = theoreticalXPos;

                if (theoreticalXPos < screenLeftEdge) {
                    float overflowWidth = screenLeftEdge - theoreticalXPos;
                    int charsToClip = 0;
                    float clippedWidth = 0;
                    for (int i = 0; i < danmaku.text.length(); i++) {
                        clippedWidth += font.width(String.valueOf(danmaku.text.charAt(i)));
                        if (clippedWidth >= overflowWidth) {
                            charsToClip = i;
                            float prevCharsWidth = font.width(danmaku.text.substring(0, charsToClip));
                            finalXPos = theoreticalXPos + prevCharsWidth;
                            break;
                        }
                    }
                    if (charsToClip > 0 && charsToClip <= danmaku.text.length()) {
                        textToRender = danmaku.text.substring(charsToClip);
                    }
                }

                float rightEdgePos = finalXPos + font.width(textToRender);
                if (rightEdgePos > screenRightEdge) {
                    float overflowWidth = rightEdgePos - screenRightEdge;
                    int charsToClip = 0;
                    float clippedWidth = 0;
                    for (int i = textToRender.length() - 1; i >= 0; i--) {
                        clippedWidth += font.width(String.valueOf(textToRender.charAt(i)));
                        if (clippedWidth >= overflowWidth) {
                            charsToClip = textToRender.length() - (i + 1);
                            break;
                        }
                    }
                    if (charsToClip > 0 && charsToClip <= textToRender.length()) {
                        textToRender = textToRender.substring(0, textToRender.length() - charsToClip);
                    }
                }

                if (textToRender.isEmpty()) {
                    continue;
                }
                font.drawInBatch(textToRender, finalXPos, yPos, finalColor, true, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);

            } else {
                font.drawInBatch(danmaku.text, theoreticalXPos, yPos, finalColor, true, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
            }
        }
        poseStack.popPose();
    }
}
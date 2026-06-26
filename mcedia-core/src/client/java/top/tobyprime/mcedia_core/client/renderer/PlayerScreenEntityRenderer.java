package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import top.tobyprime.mcedia.api.danmaku.DanmakuItem;
import top.tobyprime.mcedia.api.player.MediaPlay;
import top.tobyprime.mcedia.api.player.PlaybackState;
import top.tobyprime.mcedia.player.config.Configs;
import top.tobyprime.mcedia_core.client.danmaku.render.DanmakuTextClipper;
import top.tobyprime.mcedia_core.client.danmaku.render.DanmakuWidthMeasurer;
import top.tobyprime.mcedia_core.client.player.ScreenPeripheral;
import top.tobyprime.mcedia_core.client.player.ScreenPeripheral.ScreenFillMode;

public final class PlayerScreenEntityRenderer {

    private PlayerScreenEntityRenderer() {
    }

    public static void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0F, state.height * 0.5F, 0.0F);
        poseStack.mulPose(state.worldRotation);

        Identifier foregroundTextureId = resolveForegroundTextureId(state);
        var foregroundQuad = getForegroundQuad(state);

        if (foregroundTextureId != null) {
            // Background strips in gaps alongside foreground, same Z
            if (state.fillMode == ScreenFillMode.KEEP_ASPECT_COVER && state.backgroundTextureId != null) {
                submitBackgroundStrips(state, submitNodeCollector, poseStack, foregroundQuad);
            }
            submitQuad(
                    submitNodeCollector,
                    poseStack,
                    state.lightCoords,
                    foregroundTextureId,
                    foregroundQuad,
                    0.0F
            );
        } else if (shouldRenderBackgroundLayer(state)) {
            // No foreground, full background at z=0.0F
            submitQuad(
                    submitNodeCollector,
                    poseStack,
                    state.lightCoords,
                    state.backgroundTextureId,
                    Quad.fromSize(state.width, state.height),
                    0.0F
            );
        }
        submitDanmaku(state, poseStack, submitNodeCollector);
        submitProgressBar(state, poseStack, submitNodeCollector);
        submitPlaybackState(state, poseStack, submitNodeCollector);

        poseStack.popPose();
    }

    private static void submitQuad(
            SubmitNodeCollector submitNodeCollector,
            PoseStack poseStack,
            int lightCoords,
            Identifier textureId,
            Quad quad,
            float z
    ) {
        submitQuad(submitNodeCollector, poseStack, lightCoords, textureId, quad, z, UvBounds.FULL);
    }

    private static void submitQuad(
            SubmitNodeCollector submitNodeCollector,
            PoseStack poseStack,
            int lightCoords,
            Identifier textureId,
            Quad quad,
            float z,
            UvBounds uvBounds
    ) {
        RenderType renderType = McediaRenderTypes.entityTranslucentUnlit(textureId);
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            vertex(buffer, pose, lightCoords, -quad.halfWidth(), -quad.halfHeight(), z, uvBounds.uMin(), uvBounds.vMax());
            vertex(buffer, pose, lightCoords, quad.halfWidth(), -quad.halfHeight(), z, uvBounds.uMax(), uvBounds.vMax());
            vertex(buffer, pose, lightCoords, quad.halfWidth(), quad.halfHeight(), z, uvBounds.uMax(), uvBounds.vMin());
            vertex(buffer, pose, lightCoords, -quad.halfWidth(), quad.halfHeight(), z, uvBounds.uMin(), uvBounds.vMin());
        });
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int lightCoords, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static @Nullable Identifier resolveForegroundTextureId(State state) {
        if (hasPlayableVideoFrame(state)) {
            return state.textureId;
        }
        if (shouldRenderBackgroundLayer(state)) {
            return null;
        }
        return null;
    }

    private static boolean shouldRenderBackgroundLayer(State state) {
        return state.backgroundTextureId != null
                && (state.fillMode == ScreenFillMode.FILL
                || state.fillMode == ScreenFillMode.KEEP_ASPECT_COVER
                || state.fillMode == ScreenFillMode.KEEP_ASPECT_FIT);
    }

    private static boolean hasPlayableVideoFrame(State state) {
        return state.textureId != null && state.textureWidth > 0 && state.textureHeight > 0;
    }

    private static void submitDanmaku(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        if (!Configs.DANMAKU_VISIBLE || !state.danmakuVisible || state.media == null || state.width <= 0.0F || state.height <= 0.0F) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int trackCount = Math.max(1, Configs.DANMAKU_TRACKS);
        float lineHeight = state.height / trackCount;
        float textHeight = lineHeight * 0.7F;
        float screenWidth = state.width;
        if (textHeight <= 0.0F || screenWidth <= 0.0F) {
            return;
        }

        var entries = state.danmakuSession.update(
                state.media,
                trackCount,
                (DanmakuItem item) -> DanmakuWidthMeasurer.measureNormalizedWidth(font, item.getText(), textHeight, screenWidth)
        );
        if (entries.isEmpty()) {
            return;
        }

        int alpha = (int) (Mth.clamp(Configs.DANMAKU_OPACITY, 0.0F, 1.0F) * 255.0F);
        float scale = textHeight / font.lineHeight;
        float halfWidth = state.width * 0.5F;
        float leftBound = -halfWidth;
        float topBound = state.height * 0.5F;

        float rightBound = halfWidth;
        for (var entry : entries) {
            float left = leftBound + entry.left() * screenWidth;
            float right = left + entry.width() * screenWidth;
            if (!isDanmakuVisibleWithinBounds(left, right, leftBound, rightBound)) {
                continue;
            }
            var clipped = DanmakuTextClipper.clip(font, entry.text(), textHeight, left, right, leftBound, rightBound);
            if (clipped == null || clipped.text().isEmpty()) {
                continue;
            }
            float y = computeDanmakuDrawBottom(topBound, entry.trackIndex(), lineHeight, textHeight);
            FormattedCharSequence text = FormattedCharSequence.forward(clipped.text(), net.minecraft.network.chat.Style.EMPTY);
            poseStack.pushPose();
            poseStack.translate(clipped.drawLeft(), y, 0.0025F);
            poseStack.scale(scale, -scale, scale);
            submitNodeCollector.order(1).submitText(
                    poseStack,
                    0.0F,
                    0.0F,
                    text,
                    true,
                    Font.DisplayMode.POLYGON_OFFSET,
                    state.lightCoords,
                    (alpha << 24) | (entry.argb() & 0x00FFFFFF),
                    0,
                    0
            );
            poseStack.popPose();
        }
    }

    private static void submitPlaybackState(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        // Custom status text from downstream mods takes priority over built-in state text
        if (state.statusText != null && !state.statusText.isBlank()) {
            renderStatusText(submitNodeCollector, poseStack, state, state.statusText, 0xCCFFFFFF);
            return;
        }

        var playbackState = state.playbackState;
        if (playbackState == null || playbackState == PlaybackState.IDLE || playbackState == PlaybackState.PLAYING) {
            return;
        }

        String text;
        int color;
        switch (playbackState) {
            case LOADING -> {
                text = "加载中...";
                color = -1;
            }
            case PAUSED -> {
                text = "已暂停";
                color = 0xCCFFFFFF;
            }
            case ENDED -> {
                text = "播放结束";
                color = 0xCCAAAAAA;
            }
            case ERROR -> {
                text = state.errorMessage != null && !state.errorMessage.isBlank()
                        ? state.errorMessage : "播放出错";
                color = 0xCCFF4444;
            }
            default -> {
                return;
            }
        }

        renderStatusText(submitNodeCollector, poseStack, state, text, color);
    }

    private static void renderStatusText(SubmitNodeCollector submitNodeCollector, PoseStack poseStack,
            State state, String text, int color) {

        var font = Minecraft.getInstance().font;
        float textHeight = state.height * 0.08F;
        float scale = textHeight / font.lineHeight;
        float marginX = state.width * 0.05F;
        float marginY = state.height * 0.05F;

        poseStack.pushPose();
        poseStack.translate(state.width * 0.5F - marginX, -state.height * 0.5F + marginY, 0.003F);
        poseStack.scale(scale, -scale, scale);
        submitNodeCollector.order(1).submitText(
                poseStack,
                -font.width(text),
                0.0F,
                FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY),
                true,
                Font.DisplayMode.POLYGON_OFFSET,
                state.lightCoords,
                color,
                0,
                0
        );
        poseStack.popPose();
    }

    private static void submitBackgroundStrips(State state, SubmitNodeCollector submitNodeCollector, PoseStack poseStack, Quad foregroundQuad) {
        float hw = state.width * 0.5F;
        float hh = state.height * 0.5F;
        float fw = foregroundQuad.halfWidth();
        float fh = foregroundQuad.halfHeight();
        int lightCoords = state.lightCoords;
        Identifier textureId = state.backgroundTextureId;

        // Left strip
        float horizontalGap = hw - fw;
        if (horizontalGap > 0.001F) {
            poseStack.pushPose();
            poseStack.translate(-(hw + fw) * 0.5F, 0.0F, 0.0F);
            float uMax = horizontalGap / state.width;
            submitQuad(submitNodeCollector, poseStack, lightCoords, textureId, Quad.fromSize(horizontalGap, state.height), 0.0F, new UvBounds(0.0F, uMax, 0.0F, 1.0F));
            poseStack.popPose();
        }
        if (horizontalGap > 0.001F) {
            poseStack.pushPose();
            poseStack.translate((hw + fw) * 0.5F, 0.0F, 0.0F);
            float uMin = (hw + fw) / state.width;
            submitQuad(submitNodeCollector, poseStack, lightCoords, textureId, Quad.fromSize(horizontalGap, state.height), 0.0F, new UvBounds(uMin, 1.0F, 0.0F, 1.0F));
            poseStack.popPose();
        }

        // Top strip (between left/right strips, above video)
        float verticalGap = hh - fh;
        if (verticalGap > 0.001F) {
            poseStack.pushPose();
            poseStack.translate(0.0F, (fh + hh) * 0.5F, 0.0F);
            float vMax = verticalGap / state.height;
            submitQuad(submitNodeCollector, poseStack, lightCoords, textureId, Quad.fromSize(fw * 2.0F, verticalGap), 0.0F, new UvBounds(0.0F, 1.0F, 0.0F, vMax));
            poseStack.popPose();
        }
        if (verticalGap > 0.001F) {
            poseStack.pushPose();
            poseStack.translate(0.0F, -(fh + hh) * 0.5F, 0.0F);
            float vMin = (hh + fh) / state.height;
            submitQuad(submitNodeCollector, poseStack, lightCoords, textureId, Quad.fromSize(fw * 2.0F, verticalGap), 0.0F, new UvBounds(0.0F, 1.0F, vMin, 1.0F));
            poseStack.popPose();
        }
    }

    private static void submitProgressBar(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        if (!state.progressBarVisible || state.playbackState == null || state.playbackState == PlaybackState.IDLE
                || state.playbackState == PlaybackState.ERROR) {
            return;
        }

        float halfHeight = state.height * 0.5F;
        float gap = state.height * 0.01F;
        float barHeight = state.height * 0.025F;
        float barLeft = -state.width * 0.5F;
        float barRight = state.width * 0.5F;
        float barBottom = -halfHeight - gap - barHeight;
        float barTop = -halfHeight - gap;
        float barWidth = barRight - barLeft;
        int lightCoords = state.lightCoords;

        switch (state.playbackState) {
            case LOADING -> {
                float blockWidth = barWidth * 0.25F;
                float cycleMs = 2000.0F;
                float animPhase = (System.currentTimeMillis() % (long) cycleMs) / cycleMs;
                float blockCenter = barLeft + animPhase * (barWidth + blockWidth);
                float blockLeft = Math.max(blockCenter - blockWidth, barLeft);
                float blockRight = Math.min(blockCenter, barRight);

                if (blockLeft > barLeft) {
                    renderColoredQuad(submitNodeCollector, poseStack, barLeft, barBottom, blockLeft, barTop, 0x40000000, lightCoords);
                }
                if (blockRight > blockLeft) {
                    renderColoredQuad(submitNodeCollector, poseStack, blockLeft, barBottom, blockRight, barTop, 0xFFFFFFFF, lightCoords);
                }
                if (blockRight < barRight) {
                    renderColoredQuad(submitNodeCollector, poseStack, blockRight, barBottom, barRight, barTop, 0x40000000, lightCoords);
                }
            }
            case PLAYING -> renderProgressFill(submitNodeCollector, poseStack, barLeft, barBottom, barRight, barTop, barWidth, state.progress, 0xCCFFFFFF, lightCoords);
            case PAUSED -> renderProgressFill(submitNodeCollector, poseStack, barLeft, barBottom, barRight, barTop, barWidth, state.progress, 0x80FFFFFF, lightCoords);
            case ENDED -> {
                renderColoredQuad(submitNodeCollector, poseStack, barLeft, barBottom, barRight, barTop, 0xCCAAAAAA, lightCoords);
            }
        }
    }

    private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath("mcedia", "textures/gui/white.png");

    private static void renderColoredQuad(SubmitNodeCollector submitNodeCollector, PoseStack poseStack,
            float left, float bottom, float right, float top, int color, int lightCoords) {
        var renderType = McediaRenderTypes.entityTranslucentUnlit(WHITE_TEXTURE);
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            buffer.addVertex(pose, left, bottom, 0.0015F)
                    .setColor(color)
                    .setUv(0.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(lightCoords)
                    .setNormal(pose, 0.0F, 0.0F, 1.0F);
            buffer.addVertex(pose, right, bottom, 0.0015F)
                    .setColor(color)
                    .setUv(1.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(lightCoords)
                    .setNormal(pose, 0.0F, 0.0F, 1.0F);
            buffer.addVertex(pose, right, top, 0.0015F)
                    .setColor(color)
                    .setUv(1.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(lightCoords)
                    .setNormal(pose, 0.0F, 0.0F, 1.0F);
            buffer.addVertex(pose, left, top, 0.0015F)
                    .setColor(color)
                    .setUv(0.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(lightCoords)
                    .setNormal(pose, 0.0F, 0.0F, 1.0F);
        });
    }

    private static void renderProgressFill(SubmitNodeCollector submitNodeCollector, PoseStack poseStack,
            float barLeft, float barBottom, float barRight, float barTop, float barWidth,
            float progress, int fillColor, int lightCoords) {
        if (progress > 0.0F) {
            float fillEnd = Math.min(barLeft + barWidth * progress, barRight);
            renderColoredQuad(submitNodeCollector, poseStack, barLeft, barBottom, fillEnd, barTop, fillColor, lightCoords);
            if (fillEnd < barRight) {
                renderColoredQuad(submitNodeCollector, poseStack, fillEnd, barBottom, barRight, barTop, 0x40000000, lightCoords);
            }
        }
    }

    private static Quad getForegroundQuad(State state) {
        float renderWidth = state.width;
        float renderHeight = state.height;

        if ((state.fillMode != ScreenFillMode.KEEP_ASPECT_COVER && state.fillMode != ScreenFillMode.KEEP_ASPECT_FIT)
                || state.textureWidth <= 0
                || state.textureHeight <= 0
                || state.width <= 0.0F
                || state.height <= 0.0F) {
            return Quad.fromSize(renderWidth, renderHeight);
        }

        float screenAspect = state.width / state.height;
        float textureAspect = (float) state.textureWidth / (float) state.textureHeight;

        if (screenAspect > textureAspect) {
            renderWidth = state.height * textureAspect;
        } else if (screenAspect < textureAspect) {
            renderHeight = state.width / textureAspect;
        }
        return Quad.fromSize(renderWidth, renderHeight);
    }

    public static State createRenderState() {
        return new State();
    }

    public static void extractRenderState(ScreenPeripheral peripheral, State state, int lightCoords) {
        state.worldRotation.set(peripheral.getWorldRotation());
        state.width = peripheral.getScreenWidth();
        state.height = peripheral.getScreenHeight();
        state.fillMode = peripheral.getFillMode();
        state.backgroundTextureId = peripheral.getBackgroundTextureId();
        state.lightCoords = applyMinimumBrightness(lightCoords, peripheral.getMinBrightness());
        state.media = peripheral.getMediaPlay();
        state.danmakuSession = peripheral.getDanmakuSession();
        state.danmakuVisible = peripheral.isDanmakuVisible();
        state.progressBarVisible = peripheral.isProgressBarVisible();
        state.statusText = peripheral.getStatusText();

        state.playbackState = peripheral.getPlaybackState();
        state.errorMessage = peripheral.getErrorMessage();
        if (state.media != null) {
            var duration = state.media.getDuration();
            state.progress = duration > 0L ? (float) state.media.getEstimatedTime() / (float) duration : 0.0F;
        } else {
            state.progress = 0.0F;
        }

        var texture = peripheral.getTexture();
        if (texture == null) {
            state.textureId = MissingTextureAtlasSprite.getLocation();
            state.textureWidth = 0;
            state.textureHeight = 0;
            return;
        }
        state.textureId = texture.getTextureId();
        state.textureWidth = texture.getTextureWidth();
        state.textureHeight = texture.getTextureHeight();
    }

    public static final class State {
        public final Quaternionf worldRotation = new Quaternionf();
        public int lightCoords;
        public float width;
        public float height;
        public int textureWidth;
        public int textureHeight;
        public ScreenFillMode fillMode = ScreenFillMode.FILL;
        public @Nullable Identifier textureId;
        public @Nullable Identifier backgroundTextureId;
        public @Nullable MediaPlay media;
        public @Nullable PlaybackState playbackState;
        public @Nullable String errorMessage;
        public float progress;
        public boolean danmakuVisible = true;
        public boolean progressBarVisible = true;
        public @Nullable String statusText;
        public top.tobyprime.mcedia_core.client.danmaku.runtime.PlayerScreenDanmakuSession danmakuSession = new top.tobyprime.mcedia_core.client.danmaku.runtime.PlayerScreenDanmakuSession();
    }

    static int applyMinimumBrightness(int lightCoords, int minBrightness) {
        return LightTexture.lightCoordsWithEmission(lightCoords, minBrightness);
    }

    static float computeDanmakuDrawBottom(float topBound, int trackIndex, float lineHeight, float textHeight) {
        float extraPadding = Math.max(0.0F, lineHeight - textHeight) * 0.5F;
        return topBound - trackIndex * lineHeight - extraPadding;
    }

    static boolean isDanmakuVisibleWithinBounds(float left, float right, float leftBound, float rightBound) {
        return right > leftBound && left < rightBound;
    }

    private record UvBounds(float uMin, float uMax, float vMin, float vMax) {
        private static final UvBounds FULL = new UvBounds(0.0F, 1.0F, 0.0F, 1.0F);
    }

    static record Quad(float halfWidth, float halfHeight) {
        static Quad fromSize(float width, float height) {
            return new Quad(width * 0.5F, height * 0.5F);
        }
    }
}

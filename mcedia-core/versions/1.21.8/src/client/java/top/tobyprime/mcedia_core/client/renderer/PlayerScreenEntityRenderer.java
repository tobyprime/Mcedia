package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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

/**
 * MC 1.21.8 renderer for player-attached screens.
 * Renders into an active MultiBufferSource.BufferSource provided by the entity
 * rendering pipeline, so camera projection/view transforms are applied correctly.
 * Has no SubmitNodeCollector dependency; uses BufferSource + Font.drawInBatch.
 */
public final class PlayerScreenEntityRenderer {

    private PlayerScreenEntityRenderer() {
    }

    public static void submit(State state, Vec3 screenPos, Vec3 cameraPos,
            MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(
                screenPos.x - cameraPos.x,
                screenPos.y - cameraPos.y + state.height * 0.5F,
                screenPos.z - cameraPos.z
        );
        poseStack.mulPose(state.worldRotation);

        ResourceLocation foregroundTextureId = resolveForegroundTextureId(state);
        var foregroundQuad = getForegroundQuad(state);

        if (foregroundTextureId != null) {
            // Background strips in gaps alongside foreground, same Z
            if (state.fillMode == ScreenFillMode.KEEP_ASPECT_COVER && state.backgroundTextureId != null) {
                submitBackgroundStrips(state, bufferSource, poseStack, foregroundQuad);
            }
            renderTexturedQuad(poseStack, bufferSource, state.lightCoords, foregroundTextureId, foregroundQuad, 0.0F);
        } else if (shouldRenderBackgroundLayer(state)) {
            renderTexturedQuad(poseStack, bufferSource, state.lightCoords, state.backgroundTextureId,
                    Quad.fromSize(state.width, state.height), 0.0F);
        }
        submitDanmaku(state, bufferSource, poseStack);
        submitProgressBar(state, bufferSource, poseStack);
        submitPlaybackState(state, bufferSource, poseStack);
    }

    // ── Core quad rendering ──────────────────────────────────────────────

    private static void renderTexturedQuad(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            int lightCoords, ResourceLocation textureId, Quad quad, float z) {
        RenderType renderType = RenderType.entityTranslucent(textureId);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        var pose = poseStack.last();
        vertex(consumer, pose, lightCoords, -quad.halfWidth(), -quad.halfHeight(), z, 0.0F, 1.0F);
        vertex(consumer, pose, lightCoords, quad.halfWidth(), -quad.halfHeight(), z, 1.0F, 1.0F);
        vertex(consumer, pose, lightCoords, quad.halfWidth(), quad.halfHeight(), z, 1.0F, 0.0F);
        vertex(consumer, pose, lightCoords, -quad.halfWidth(), quad.halfHeight(), z, 0.0F, 0.0F);
    }

    private static void renderTexturedQuad(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            int lightCoords, ResourceLocation textureId, Quad quad, float z, UvBounds uvBounds) {
        RenderType renderType = RenderType.entityTranslucent(textureId);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        var pose = poseStack.last();
        vertex(consumer, pose, lightCoords, -quad.halfWidth(), -quad.halfHeight(), z, uvBounds.uMin(), uvBounds.vMax());
        vertex(consumer, pose, lightCoords, quad.halfWidth(), -quad.halfHeight(), z, uvBounds.uMax(), uvBounds.vMax());
        vertex(consumer, pose, lightCoords, quad.halfWidth(), quad.halfHeight(), z, uvBounds.uMax(), uvBounds.vMin());
        vertex(consumer, pose, lightCoords, -quad.halfWidth(), quad.halfHeight(), z, uvBounds.uMin(), uvBounds.vMin());
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int lightCoords,
            float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    // ── Foreground / background helpers ─────────────────────────────────

    private static @Nullable ResourceLocation resolveForegroundTextureId(State state) {
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
                || state.fillMode == ScreenFillMode.KEEP_ASPECT_COVER);
    }

    private static boolean hasPlayableVideoFrame(State state) {
        return state.textureId != null && state.textureWidth > 0 && state.textureHeight > 0;
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

    // ── Background strips (KEEP_ASPECT_COVER) ──────────────────────────

    private static void submitBackgroundStrips(State state, MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack, Quad foregroundQuad) {
        float hw = state.width * 0.5F;
        float hh = state.height * 0.5F;
        float fw = foregroundQuad.halfWidth();
        float fh = foregroundQuad.halfHeight();
        int lightCoords = state.lightCoords;
        ResourceLocation textureId = state.backgroundTextureId;

        // Left strip
        float horizontalGap = hw - fw;
        if (horizontalGap > 0.001F) {
            poseStack.pushPose();
            poseStack.translate(-(hw + fw) * 0.5F, 0.0F, 0.0F);
            float uMax = horizontalGap / state.width;
            renderTexturedQuad(poseStack, bufferSource, lightCoords, textureId, Quad.fromSize(horizontalGap, state.height), 0.0F,
                    new UvBounds(0.0F, uMax, 0.0F, 1.0F));
            poseStack.popPose();
        }
        if (horizontalGap > 0.001F) {
            poseStack.pushPose();
            poseStack.translate((hw + fw) * 0.5F, 0.0F, 0.0F);
            float uMin = (hw + fw) / state.width;
            renderTexturedQuad(poseStack, bufferSource, lightCoords, textureId, Quad.fromSize(horizontalGap, state.height), 0.0F,
                    new UvBounds(uMin, 1.0F, 0.0F, 1.0F));
            poseStack.popPose();
        }

        // Top/bottom strips
        float verticalGap = hh - fh;
        if (verticalGap > 0.001F) {
            poseStack.pushPose();
            poseStack.translate(0.0F, (fh + hh) * 0.5F, 0.0F);
            float vMax = verticalGap / state.height;
            renderTexturedQuad(poseStack, bufferSource, lightCoords, textureId, Quad.fromSize(fw * 2.0F, verticalGap), 0.0F,
                    new UvBounds(0.0F, 1.0F, 0.0F, vMax));
            poseStack.popPose();
        }
        if (verticalGap > 0.001F) {
            poseStack.pushPose();
            poseStack.translate(0.0F, -(fh + hh) * 0.5F, 0.0F);
            float vMin = (hh + fh) / state.height;
            renderTexturedQuad(poseStack, bufferSource, lightCoords, textureId, Quad.fromSize(fw * 2.0F, verticalGap), 0.0F,
                    new UvBounds(0.0F, 1.0F, vMin, 1.0F));
            poseStack.popPose();
        }
    }

    // ── Danmaku ────────────────────────────────────────────────────────

    private static void submitDanmaku(State state, MultiBufferSource.BufferSource bufferSource, PoseStack poseStack) {
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
            font.drawInBatch(
                    text,
                    0.0F, 0.0F,
                    (alpha << 24) | (entry.argb() & 0x00FFFFFF),
                    true,
                    poseStack.last().pose(),
                    bufferSource,
                    Font.DisplayMode.POLYGON_OFFSET,
                    0,
                    state.lightCoords
            );
            poseStack.popPose();
        }
    }

    // ── Progress bar ───────────────────────────────────────────────────

    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/white.png");

    private static void submitProgressBar(State state, MultiBufferSource.BufferSource bufferSource, PoseStack poseStack) {
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
                    renderColoredQuad(poseStack, bufferSource, barLeft, barBottom, blockLeft, barTop, 0x40000000, lightCoords);
                }
                if (blockRight > blockLeft) {
                    renderColoredQuad(poseStack, bufferSource, blockLeft, barBottom, blockRight, barTop, 0xFFFFFFFF, lightCoords);
                }
                if (blockRight < barRight) {
                    renderColoredQuad(poseStack, bufferSource, blockRight, barBottom, barRight, barTop, 0x40000000, lightCoords);
                }
            }
            case PLAYING -> {
                if (state.progress > 0.0F) {
                    float fillEnd = Math.min(barLeft + barWidth * state.progress, barRight);
                    renderColoredQuad(poseStack, bufferSource, barLeft, barBottom, fillEnd, barTop, 0xCCFFFFFF, lightCoords);
                    if (fillEnd < barRight) {
                        renderColoredQuad(poseStack, bufferSource, fillEnd, barBottom, barRight, barTop, 0x40000000, lightCoords);
                    }
                }
            }
            case PAUSED -> {
                if (state.progress > 0.0F) {
                    float fillEnd = Math.min(barLeft + barWidth * state.progress, barRight);
                    renderColoredQuad(poseStack, bufferSource, barLeft, barBottom, fillEnd, barTop, 0x80FFFFFF, lightCoords);
                    if (fillEnd < barRight) {
                        renderColoredQuad(poseStack, bufferSource, fillEnd, barBottom, barRight, barTop, 0x40000000, lightCoords);
                    }
                }
            }
            case ENDED -> {
                renderColoredQuad(poseStack, bufferSource, barLeft, barBottom, barRight, barTop, 0xCCAAAAAA, lightCoords);
            }
        }
    }

    private static void renderColoredQuad(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            float left, float bottom, float right, float top, int color, int lightCoords) {
        RenderType renderType = RenderType.entityTranslucent(WHITE_TEXTURE);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        var pose = poseStack.last();
        consumer.addVertex(pose, left, bottom, 0.0015F)
                .setColor(color)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, right, bottom, 0.0015F)
                .setColor(color)
                .setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, right, top, 0.0015F)
                .setColor(color)
                .setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(pose, left, top, 0.0015F)
                .setColor(color)
                .setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    // ── Playback state text ────────────────────────────────────────────

    private static void submitPlaybackState(State state, MultiBufferSource.BufferSource bufferSource, PoseStack poseStack) {
        // Custom status text from downstream mods takes priority over built-in state text
        if (state.statusText != null && !state.statusText.isBlank()) {
            renderStatusText(bufferSource, poseStack, state, state.statusText, 0xCCFFFFFF);
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

        renderStatusText(bufferSource, poseStack, state, text, color);
    }

    private static void renderStatusText(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack,
            State state, String text, int color) {
        var font = Minecraft.getInstance().font;
        float textHeight = state.height * 0.08F;
        float scale = textHeight / font.lineHeight;
        float marginX = state.width * 0.05F;
        float marginY = state.height * 0.05F;

        poseStack.pushPose();
        poseStack.translate(state.width * 0.5F - marginX, -state.height * 0.5F + marginY, 0.003F);
        poseStack.scale(scale, -scale, scale);

        FormattedCharSequence seq = FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY);
        font.drawInBatch(
                seq,
                -font.width(text),
                0.0F,
                color,
                true,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.POLYGON_OFFSET,
                0,
                state.lightCoords
        );
        poseStack.popPose();
    }

    // ── Shared state type ──────────────────────────────────────────────

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
        public @Nullable ResourceLocation textureId;
        public @Nullable ResourceLocation backgroundTextureId;
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

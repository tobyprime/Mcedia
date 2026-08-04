package top.tobyprime.mcedia_core.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import top.tobyprime.mcedia.api.player.MediaPlay;
import top.tobyprime.mcedia.api.player.PlaybackState;
import top.tobyprime.mcedia_core.client.player.HudScreenPeripheral;
import top.tobyprime.mcedia_core.client.player.ScreenPeripheral.ScreenFillMode;

public final class HudScreenRenderer {
    private HudScreenRenderer() {}

    public static void render(HudScreenPeripheral peripheral, GuiGraphicsExtractor guiGraphics) {
        var state = loadState(peripheral);
        if (state.areaWidth <= 0 || state.areaHeight <= 0) return;

        int x = state.x;
        int y = state.y;
        int w = state.areaWidth;
        int h = state.areaHeight;

        Identifier foreground = resolveForegroundTexture(state);
        Bounds fgBounds = null;

        if (foreground != null) {
            fgBounds = getForegroundBounds(state, x, y, w, h);
            if (state.fillMode == ScreenFillMode.KEEP_ASPECT_COVER && state.backgroundTextureId != null) {
                drawBackgroundStrips(guiGraphics, x, y, w, h, state, fgBounds);
            }
        } else if (state.backgroundTextureId != null && state.shouldRenderBackground) {
            blitFull(guiGraphics, state.backgroundTextureId, x, y, w, h);
        }

        if (foreground != null) {
            blitTexture(guiGraphics, foreground, fgBounds.x, fgBounds.y, fgBounds.w, fgBounds.h,
                    state.textureWidth, state.textureHeight);
        }

        drawProgressBar(guiGraphics, x, y, w, h, state);
        drawStatusText(guiGraphics, x, y, w, h, state);
    }

    private static void blitFull(GuiGraphicsExtractor guiGraphics, Identifier tex, int x, int y, int w, int h) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F, w, h, w, h, w, h, -1);
    }

    private static void blitTexture(GuiGraphicsExtractor guiGraphics, Identifier tex, int x, int y, int w, int h,
            int texW, int texH) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0F, 0.0F, w, h, texW, texH, texW, texH, -1);
    }

    private static void blitRegion(GuiGraphicsExtractor guiGraphics, Identifier tex,
            int x, int y, int w, int h,
            int regionX, int regionY, int regionW, int regionH,
            int texW, int texH) {
        if (texW <= 0 || texH <= 0) { texW = w; texH = h; }
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, regionX, regionY, w, h, regionW, regionH, texW, texH, -1);
    }

    private static RenderState loadState(HudScreenPeripheral peripheral) {
        int x = peripheral.getX();
        int y = peripheral.getY();
        int areaWidth = peripheral.getWidth();
        int areaHeight = peripheral.getHeight();
        ScreenFillMode fillMode = peripheral.getFillMode();
        Identifier backgroundTextureId = peripheral.getBackgroundTextureId();
        boolean shouldRenderBackground = backgroundTextureId != null
                && (fillMode == ScreenFillMode.FILL
                || fillMode == ScreenFillMode.KEEP_ASPECT_COVER
                || fillMode == ScreenFillMode.KEEP_ASPECT_FIT);
        MediaPlay media = peripheral.getMediaPlay();
        PlaybackState playbackState = peripheral.getPlaybackState();
        String errorMessage = peripheral.getErrorMessage();
        float progress = 0.0F;
        if (media != null) {
            long duration = media.getDuration();
            progress = duration > 0L ? (float) media.getEstimatedTime() / (float) duration : 0.0F;
        }
        boolean progressBarVisible = peripheral.isProgressBarVisible();
        String statusText = peripheral.getStatusText();

        int textureWidth = 0;
        int textureHeight = 0;
        @Nullable Identifier textureId = null;
        var texture = peripheral.getTexture();
        if (texture != null) {
            textureId = texture.getTextureId();
            textureWidth = texture.getTextureWidth();
            textureHeight = texture.getTextureHeight();
        }

        return new RenderState(x, y, areaWidth, areaHeight, fillMode,
                textureWidth, textureHeight, textureId, backgroundTextureId,
                shouldRenderBackground, media, playbackState, errorMessage,
                progress, progressBarVisible, statusText);
    }

    private static @Nullable Identifier resolveForegroundTexture(RenderState state) {
        if (state.textureId != null && state.textureWidth > 0 && state.textureHeight > 0) {
            return state.textureId;
        }
        return null;
    }

    private static void drawBackgroundStrips(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, RenderState state, Bounds fg) {
        int bw = state.textureWidth > 0 ? state.textureWidth : w;
        int bh = state.textureHeight > 0 ? state.textureHeight : h;
        Identifier tex = state.backgroundTextureId;

        if (fg.x > x) {
            int stripW = fg.x - x;
            blitRegion(guiGraphics, tex, x, y, stripW, h, 0, 0, stripW, h, bw, bh);
        }
        int rightEnd = x + w;
        int fgRight = fg.x + fg.w;
        if (fgRight < rightEnd) {
            int stripW = rightEnd - fgRight;
            blitRegion(guiGraphics, tex, fgRight, y, stripW, h, rightEnd - stripW, 0, stripW, h, bw, bh);
        }
        if (fg.y > y) {
            int stripH = fg.y - y;
            blitRegion(guiGraphics, tex, fg.x, y, fg.w, stripH, 0, 0, fg.w, stripH, bw, bh);
        }
        int bottomEnd = y + h;
        int fgBottom = fg.y + fg.h;
        if (fgBottom < bottomEnd) {
            int stripH = bottomEnd - fgBottom;
            blitRegion(guiGraphics, tex, fg.x, fgBottom, fg.w, stripH, 0, bottomEnd - fgBottom, fg.w, stripH, bw, bh);
        }
    }

    private static Bounds getForegroundBounds(RenderState state, int x, int y, int w, int h) {
        if (state.fillMode != ScreenFillMode.KEEP_ASPECT_COVER && state.fillMode != ScreenFillMode.KEEP_ASPECT_FIT) {
            return new Bounds(x, y, w, h);
        }
        if (state.textureWidth <= 0 || state.textureHeight <= 0 || w <= 0 || h <= 0) {
            return new Bounds(x, y, w, h);
        }

        float screenAspect = (float) w / (float) h;
        float textureAspect = (float) state.textureWidth / (float) state.textureHeight;

        int fw = w;
        int fh = h;
        if (screenAspect > textureAspect) {
            fw = (int) (h * textureAspect);
        } else if (screenAspect < textureAspect) {
            fh = (int) (w / textureAspect);
        }
        return new Bounds(x + (w - fw) / 2, y + (h - fh) / 2, fw, fh);
    }

    private static void drawProgressBar(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, RenderState state) {
        if (!state.progressBarVisible || state.playbackState == null
                || state.playbackState == PlaybackState.IDLE || state.playbackState == PlaybackState.ERROR) {
            return;
        }

        int barHeight = Math.max(2, h / 40);
        int gap = Math.max(1, h / 100);
        int barY = y + h - gap - barHeight;

        switch (state.playbackState) {
            case LOADING -> {
                int blockWidth = w / 4;
                long cycleMs = 2000;
                float animPhase = (System.currentTimeMillis() % cycleMs) / (float) cycleMs;
                int blockCenter = x + (int) (animPhase * (w + blockWidth));
                int blockLeft = Math.max(blockCenter - blockWidth, x);
                int blockRight = Math.min(blockCenter, x + w);

                if (blockLeft > x) drawColoredRect(guiGraphics, x, barY, blockLeft - x, barHeight, 0x40000000);
                if (blockRight > blockLeft) drawColoredRect(guiGraphics, blockLeft, barY, blockRight - blockLeft, barHeight, 0xCCFFFFFF);
                if (blockRight < x + w) drawColoredRect(guiGraphics, blockRight, barY, x + w - blockRight, barHeight, 0x40000000);
            }
            case PLAYING -> drawProgressFill(guiGraphics, x, barY, w, barHeight, state.progress, 0xCCFFFFFF);
            case PAUSED -> drawProgressFill(guiGraphics, x, barY, w, barHeight, state.progress, 0x80FFFFFF);
            case ENDED -> drawColoredRect(guiGraphics, x, barY, w, barHeight, 0xCCAAAAAA);
        }
    }

    private static void drawProgressFill(GuiGraphicsExtractor guiGraphics, int x, int barY, int w, int barH, float progress, int color) {
        if (progress > 0.0F) {
            int fillW = (int) (w * Math.min(progress, 1.0F));
            drawColoredRect(guiGraphics, x, barY, fillW, barH, color);
            if (fillW < w) {
                drawColoredRect(guiGraphics, x + fillW, barY, w - fillW, barH, 0x40000000);
            }
        }
    }

    private static void drawColoredRect(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, int color) {
        guiGraphics.fill(x, y, x + w, y + h, color);
    }

    private static void drawStatusText(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, RenderState state) {
        String text;
        int color;

        if (state.statusText != null && !state.statusText.isBlank()) {
            text = state.statusText;
            color = 0xCCFFFFFF;
        } else if (state.playbackState == null || state.playbackState == PlaybackState.IDLE || state.playbackState == PlaybackState.PLAYING) {
            return;
        } else {
            switch (state.playbackState) {
                case LOADING -> { text = "加载中..."; color = 0xFFFFFFFF; }
                case PAUSED -> { text = "已暂停"; color = 0xCCFFFFFF; }
                case ENDED -> { text = "播放结束"; color = 0xCCAAAAAA; }
                case ERROR -> {
                    text = state.errorMessage != null && !state.errorMessage.isBlank() ? state.errorMessage : "播放出错";
                    color = 0xCCFF4444;
                }
                default -> { return; }
            }
        }

        var font = Minecraft.getInstance().font;
        int textHeight = Math.max(8, h / 12);
        float scale = (float) textHeight / font.lineHeight;
        int drawX = x + (int) (w * 0.95F) - (int) (font.width(text) * scale);
        int drawY = y + (int) (h * 0.05F);
        guiGraphics.text(font, text, drawX, drawY, color, true);
    }

    private record RenderState(
            int x, int y, int areaWidth, int areaHeight,
            ScreenFillMode fillMode,
            int textureWidth, int textureHeight,
            @Nullable Identifier textureId,
            @Nullable Identifier backgroundTextureId,
            boolean shouldRenderBackground,
            @Nullable MediaPlay media,
            @Nullable PlaybackState playbackState,
            @Nullable String errorMessage,
            float progress,
            boolean progressBarVisible,
            @Nullable String statusText
    ) {}

    private record Bounds(int x, int y, int w, int h) {}
}

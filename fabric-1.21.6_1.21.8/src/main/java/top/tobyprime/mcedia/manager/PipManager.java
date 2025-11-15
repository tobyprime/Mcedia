package top.tobyprime.mcedia.manager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.VideoTexture;
import top.tobyprime.mcedia.core.AudioSource;
import top.tobyprime.mcedia.core.Media;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.provider.*;
import top.tobyprime.mcedia.video_fetcher.BilibiliBangumiFetcher;
import top.tobyprime.mcedia.video_fetcher.UrlExpander;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PipManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipManager.class);
    private static final PipManager INSTANCE = new PipManager();

    // --- State & Config ---
    private final MediaPlayer player;
    private final AudioSource audioSource;
    private VideoTexture texture;
    private boolean active = false;
    private boolean isInteracting = false;
    private int interactionMode = 0;
    private int resizeEdge = 0;
    private double anchorMouseX, anchorMouseY;
    private int initialX, initialY, initialWidth, initialHeight;
    private float seekProgress = -1.0f;
    private long mouseDownTime = 0;
    private static final long LONG_PRESS_THRESHOLD_MS = 200;
    private static final int RESIZE_BORDER_WIDTH = 5;
    private static final int PROGRESS_BAR_HEIGHT = 8;
    public int x = 10, y = 10, width = 320, height = 180;
    public float opacity = 0.9f;
    public float volume = 5.0f;
    public boolean danmakuEnabled = false;

    // --- Cursors ---
    private final Map<Integer, Long> cursorCache = new HashMap<>();
    private int currentCursor = 0;

    // --- Resources ---
    private static final Component PLAY_ICON = Component.literal("▶").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    private static final Component PAUSE_ICON = Component.literal("⏸").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    private static final ResourceLocation CLOSE_ICON = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/close_icon.png");
    private static final ResourceLocation DANMAKU_ON_ICON = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/danmaku_on_icon.png");
    private static final ResourceLocation DANMAKU_OFF_ICON = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/danmaku_off_icon.png");


    private PipManager() {
        this.player = new MediaPlayer();
        this.audioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);
        if (Minecraft.getInstance() != null) {
            GLFW.glfwPostEmptyEvent();
            cursorCache.put(1, GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR));
            cursorCache.put(2, GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR));
            cursorCache.put(3, GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR));
            cursorCache.put(4, GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NS_CURSOR));
            cursorCache.put(5, GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
        }
    }

    public static PipManager getInstance() { return INSTANCE; }
    public boolean isActive() { return active; }
    public void open(String url) { if (url == null || url.isBlank()) return; close(); active = true; LOGGER.info("[PiP] 开启画中画: {}", url); CompletableFuture.supplyAsync(() -> { try { String expandedUrl = UrlExpander.expand(url).join(); IMediaProvider provider = MediaProviderRegistry.getInstance().getProviderForUrl(expandedUrl); String cookie = (provider instanceof BilibiliVideoProvider || provider instanceof BilibiliBangumiProvider) ? McediaConfig.BILIBILI_COOKIE : null; if (provider instanceof BilibiliBangumiProvider) { return BilibiliBangumiFetcher.fetch(expandedUrl, cookie, "自动").getVideoInfo(); } if (provider != null) { return provider.resolve(expandedUrl, cookie, "自动"); } throw new UnsupportedOperationException("No provider found for URL: " + expandedUrl); } catch (Exception e) { throw new RuntimeException("无法解析链接: " + e.getMessage(), e); } }, Mcedia.getInstance().getBackgroundExecutor()).handle((videoInfo, throwable) -> { if (!this.active) return null; if (throwable != null) { LOGGER.error("[PiP] 加载失败", throwable); return null; } Minecraft.getInstance().execute(() -> { if (!this.active) return; player.bindAudioSource(audioSource); texture = new VideoTexture(ResourceLocation.fromNamespaceAndPath("mcedia", "pip_player")); player.bindTexture(texture); player.openSync(videoInfo, null); Media media = player.getMedia(); if (media != null && media.getWidth() > 0 && media.getHeight() > 0) { texture.prepareAndPrewarm(media.getWidth(), media.getHeight(), () -> { if (this.active) player.play(); }); } }); return null; }); }
    public void close() { if (!active) return; LOGGER.info("[PiP] 关闭画中画。"); active = false; player.unbindAudioSource(audioSource); player.closeAsync().thenRun(() -> { if (texture != null) { texture.close(); texture = null; } }); updateCursor(0); }
    public void tick() { if (!active) return; var clientPlayer = Minecraft.getInstance().player; if (clientPlayer != null) { audioSource.setPos((float)clientPlayer.getX(), (float)clientPlayer.getY(), (float)clientPlayer.getZ()); audioSource.setVolume(this.volume); } Media media = player.getMedia(); if (media != null) { if (media.isPlaying()) media.uploadVideo(); if (media.isEnded()) close(); } }
    public void onGameShutdown() { cursorCache.values().forEach(GLFW::glfwDestroyCursor); cursorCache.clear(); }

    public void render(GuiGraphics context, DeltaTracker deltaTracker) {
        if (!active || texture == null || !texture.isInitialized()) {
            if (currentCursor != 0) updateCursor(0);
            return;
        }

        Font font = Minecraft.getInstance().font;
        Matrix3x2fStack matrixStack = context.pose();
        boolean inChat = Minecraft.getInstance().screen instanceof ChatScreen;

        matrixStack.pushMatrix();
        matrixStack.translate((float)x, (float)y);

        int alpha = (int) (this.opacity * 255);
        int color = (alpha << 24) | 0xFFFFFF;
        // This blit is for our video texture, it's correct.
        context.blit(RenderPipelines.GUI_TEXTURED, texture.getResourceLocation(), 0, 0, 0.0F, 0.0F, width, height, width, height, width, height, color);

        float displayProgress = (interactionMode == 3 && seekProgress >= 0) ? seekProgress : player.getProgress();
        context.fill(0, height, width, height + PROGRESS_BAR_HEIGHT, 0x90000000);
        if (displayProgress > 0) context.fill(0, height, (int)(width * displayProgress), height + PROGRESS_BAR_HEIGHT, 0xCCFFFFFF);

        if (inChat) {
            double guiMouseX = Minecraft.getInstance().mouseHandler.xpos() / Minecraft.getInstance().getWindow().getGuiScale();
            double guiMouseY = Minecraft.getInstance().mouseHandler.ypos() / Minecraft.getInstance().getWindow().getGuiScale();
            if (isMouseOverWindow(guiMouseX, guiMouseY) || isMouseOverProgressBar(guiMouseX, guiMouseY)) {
                context.fill(0, 0, width, height, 0x40000000);
            }

            // [ULTIMATE FIX] Use the correct blit method for standalone textures
            blitIcon(context, CLOSE_ICON, width - 12, 4, 8, 8);

            ResourceLocation danmakuIcon = danmakuEnabled ? DANMAKU_ON_ICON : DANMAKU_OFF_ICON;
            blitIcon(context, danmakuIcon, 4, 14, 16, 16);

            // Revert play/pause to reliable text component
            Component playPauseComponent = player.getMedia() != null && player.getMedia().isPlaying() ? PAUSE_ICON : PLAY_ICON;
            context.fill(width / 2 - 10, height / 2 - 10, width / 2 + 10, height / 2 + 10, 0x50000000);
            context.drawCenteredString(font, playPauseComponent, width / 2, height / 2 - 4, 0xFFFFFF);

            String volText = String.format("Vol: %.0f%%", (volume / 10.0f) * 100);
            context.drawString(font, volText, 4, 4, 0xFFFFFF, true);
        }

        if (player.isBuffering()) {
            context.drawCenteredString(font, "缓冲中...", width / 2, height / 2 - 4, 0xFFFFFF);
        }

        matrixStack.popMatrix();

        if (inChat) {
            // ... (Cursor logic remains correct) ...
            double mouseX = Minecraft.getInstance().mouseHandler.xpos() / Minecraft.getInstance().getWindow().getGuiScale(); double mouseY = Minecraft.getInstance().mouseHandler.ypos() / Minecraft.getInstance().getWindow().getGuiScale(); int targetCursor = 0; if (isInteracting) { targetCursor = currentCursor; } else { int edge = getResizeEdge(mouseX, mouseY); if (edge != 0) { if ((edge == (1|8)) || (edge == (4|2))) targetCursor = 1; else if ((edge == (1|2)) || (edge == (4|8))) targetCursor = 2; else if ((edge & 2) != 0 || (edge & 8) != 0) targetCursor = 3; else if ((edge & 1) != 0 || (edge & 4) != 0) targetCursor = 4; } else if (isMouseOverProgressBar(mouseX, mouseY)) { targetCursor = 3; } else if (isMouseOverWindow(mouseX, mouseY)) { targetCursor = 5; } } updateCursor(targetCursor);
        } else {
            updateCursor(0);
        }
    }

    // [ULTIMATE FIX] This helper uses the correct blit method for standalone textures
    private void blitIcon(GuiGraphics context, ResourceLocation icon, int x, int y, int width, int height) {
        context.blit(icon, x, y, 0, 0, width, height, width, height);
    }

    // ... (All mouse and interaction methods remain correct) ...
    public boolean mouseClicked(double mouseX, double mouseY, int button) { if (!active || !(Minecraft.getInstance().screen instanceof ChatScreen)) return false; double guiMouseX = mouseX / Minecraft.getInstance().getWindow().getGuiScale(); double guiMouseY = mouseY / Minecraft.getInstance().getWindow().getGuiScale(); isInteracting = false; if (isMouseOverCloseButton(guiMouseX, guiMouseY)) { close(); return true; } if (isMouseOverDanmakuButton(guiMouseX, guiMouseY)) { danmakuEnabled = !danmakuEnabled; return true; } if (isMouseOverPlayPauseButton(guiMouseX, guiMouseY)) { togglePause(); return true; } if (isMouseOverProgressBar(guiMouseX, guiMouseY)) { interactionMode = 3; } else { resizeEdge = getResizeEdge(guiMouseX, guiMouseY); if (resizeEdge != 0) { interactionMode = 2; } else if (isMouseOverWindow(guiMouseX, guiMouseY)) { interactionMode = 1; } else { return false; } } isInteracting = true; mouseDownTime = System.currentTimeMillis(); anchorMouseX = guiMouseX; anchorMouseY = guiMouseY; initialX = x; initialY = y; initialWidth = width; initialHeight = height; if (interactionMode == 3) { seekOnProgressBar(guiMouseX); } return true; }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) { if (!active || !isInteracting) return false; double scale = Minecraft.getInstance().getWindow().getGuiScale(); double guiMouseX = mouseX / scale; double guiMouseY = mouseY / scale; if (interactionMode == 3) { this.seekProgress = Mth.clamp((float)(guiMouseX - x) / width, 0.0f, 1.0f); return true; } if (interactionMode == 2 || (interactionMode == 1 && System.currentTimeMillis() - mouseDownTime > LONG_PRESS_THRESHOLD_MS)) { double totalDeltaX = guiMouseX - anchorMouseX; double totalDeltaY = guiMouseY - anchorMouseY; if (interactionMode == 1) { x = (int) (initialX + totalDeltaX); y = (int) (initialY + totalDeltaY); } else { if ((resizeEdge & 1) != 0) { int newHeight = (int)(initialHeight - totalDeltaY); if (newHeight > 30) { y = (int)(initialY + totalDeltaY); height = newHeight; } } if ((resizeEdge & 4) != 0) { height = Mth.clamp((int)(initialHeight + totalDeltaY), 30, 10000); } if ((resizeEdge & 2) != 0) { width = Mth.clamp((int)(initialWidth + totalDeltaX), 50, 10000); } if ((resizeEdge & 8) != 0) { int newWidth = (int)(initialWidth - totalDeltaX); if (newWidth > 50) { x = (int)(initialX + totalDeltaX); width = newWidth; } } } } return true; }
    public void mouseReleased(double mouseX, double mouseY, int button) { if (isInteracting) { if (interactionMode == 3 && seekProgress >= 0) { Media media = player.getMedia(); if (media != null && !media.isLiveStream()) { player.seek((long)(media.getLengthUs() * seekProgress)); } } else if (interactionMode == 1 && System.currentTimeMillis() - mouseDownTime < LONG_PRESS_THRESHOLD_MS) { /* Now handled by button click */ } isInteracting = false; interactionMode = 0; resizeEdge = 0; seekProgress = -1.0f; } }
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) { if (!active || !(Minecraft.getInstance().screen instanceof ChatScreen)) return false; double guiMouseX = mouseX / Minecraft.getInstance().getWindow().getGuiScale(); double guiMouseY = mouseY / Minecraft.getInstance().getWindow().getGuiScale(); if (isMouseOverWindow(guiMouseX, guiMouseY)) { this.volume = Mth.clamp(this.volume + (float)(amount * 0.5), 0.0f, 10.0f); return true; } return false; }
    private boolean isMouseOverWindow(double guiMouseX, double guiMouseY) { return guiMouseX >= x && guiMouseX <= x + width && guiMouseY >= y && guiMouseY <= y + height; }
    private boolean isMouseOverProgressBar(double guiMouseX, double guiMouseY) { return guiMouseX >= x && guiMouseX <= x + width && guiMouseY >= y + height && guiMouseY <= y + height + PROGRESS_BAR_HEIGHT; }
    private boolean isMouseOverCloseButton(double guiMouseX, double guiMouseY) { return guiMouseX >= x + width - 12 && guiMouseX <= x + width - 4 && guiMouseY >= y + 4 && guiMouseY <= y + 12; }
    private boolean isMouseOverPlayPauseButton(double guiMouseX, double guiMouseY) { return guiMouseX >= x + width/2 - 8 && guiMouseX <= x + width/2 + 8 && guiMouseY >= y + height/2 - 8 && guiMouseY <= y + height/2 + 8; }
    private boolean isMouseOverDanmakuButton(double guiMouseX, double guiMouseY) { return guiMouseX >= x + 4 && guiMouseX <= x + 20 && guiMouseY >= y + 14 && guiMouseY <= y + 30; }
    private void seekOnProgressBar(double guiMouseX) { this.seekProgress = Mth.clamp((float)(guiMouseX - x) / width, 0.0f, 1.0f); }
    private int getResizeEdge(double guiMouseX, double guiMouseY) { int edge = 0; if (guiMouseY >= y - RESIZE_BORDER_WIDTH && guiMouseY <= y + RESIZE_BORDER_WIDTH) edge |= 1; if (guiMouseX >= x + width - RESIZE_BORDER_WIDTH && guiMouseX <= x + width + RESIZE_BORDER_WIDTH) edge |= 2; if (guiMouseY >= y + height - RESIZE_BORDER_WIDTH && guiMouseY <= y + height + PROGRESS_BAR_HEIGHT + RESIZE_BORDER_WIDTH) edge |= 4; if (guiMouseX >= x - RESIZE_BORDER_WIDTH && guiMouseX <= x + RESIZE_BORDER_WIDTH) edge |= 8; return edge; }
    public void togglePause() { if (!active || player.getMedia() == null) return; if (player.getMedia().isPaused()) { player.play(); } else { player.pause(); } }
    private void updateCursor(int newCursorType) { if (this.currentCursor != newCursorType) { this.currentCursor = newCursorType; long cursorHandle = cursorCache.getOrDefault(newCursorType, 0L); GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), cursorHandle); } }
}
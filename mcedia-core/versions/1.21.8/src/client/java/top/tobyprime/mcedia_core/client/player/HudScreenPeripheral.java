package top.tobyprime.mcedia_core.client.player;

import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import top.tobyprime.mcedia.api.player.MediaPlay;
import top.tobyprime.mcedia.api.player.PlaybackState;
import top.tobyprime.mcedia_core.client.renderer.MediaTextureImpl;

import static top.tobyprime.mcedia_core.client.player.ScreenPeripheral.ScreenFillMode;

public final class HudScreenPeripheral implements MediaPlayerPeripheral, AutoCloseable {
    private static final int MIN_SIZE = 1;

    private int x;
    private int y;
    private int width = 320;
    private int height = 180;
    private ScreenFillMode fillMode = ScreenFillMode.KEEP_ASPECT_COVER;
    private @Nullable ResourceLocation backgroundTextureId = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    private @Nullable PlayerHost host;
    private boolean progressBarVisible = true;
    private @Nullable String statusText;
    private boolean closed;

    public int getX() { return x; }
    public int getY() { return y; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setScreenSize(int width, int height) {
        this.width = Math.max(width, MIN_SIZE);
        this.height = Math.max(height, MIN_SIZE);
    }

    public ScreenFillMode getFillMode() { return fillMode; }
    public void setFillMode(@Nullable ScreenFillMode fillMode) {
        this.fillMode = fillMode == null ? ScreenFillMode.FILL : fillMode;
    }

    public @Nullable ResourceLocation getBackgroundTextureId() { return backgroundTextureId; }
    public void setBackgroundTextureId(@Nullable ResourceLocation backgroundTextureId) {
        this.backgroundTextureId = backgroundTextureId;
    }

    public @Nullable MediaTextureImpl getTexture() {
        if (host == null) return null;
        var texture = host.getTexture();
        return texture instanceof MediaTextureImpl impl ? impl : null;
    }

    public boolean isProgressBarVisible() { return progressBarVisible; }
    public void setProgressBarVisible(boolean progressBarVisible) {
        this.progressBarVisible = progressBarVisible;
    }

    public @Nullable String getStatusText() { return statusText; }
    public void setStatusText(@Nullable String statusText) {
        this.statusText = statusText;
    }

    public @Nullable MediaPlay getMediaPlay() {
        return host == null ? null : host.getPlayer().getMedia();
    }

    public PlaybackState getPlaybackState() {
        return host == null ? PlaybackState.IDLE : host.getPlaybackState();
    }

    public @Nullable String getErrorMessage() {
        return host == null ? null : host.getErrorMessage();
    }

    @Override
    public void setPlayerHost(@Nullable PlayerHost host) {
        this.host = host;
    }

    @Override
    public boolean isActive() {
        return !closed;
    }

    @Override
    public boolean isAlive() {
        return isActive();
    }

    @Override
    public double getDistance() {
        return 0.0;
    }

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.Screen;
    }

    @Override
    public void close() {
        closed = true;
        host = null;
    }
}

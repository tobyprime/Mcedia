package top.tobyprime.mcedia_core.client.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import top.tobyprime.mcedia.api.player.MediaPlay;
import top.tobyprime.mcedia.api.player.PlaybackState;
import top.tobyprime.mcedia_core.client.danmaku.runtime.PlayerScreenDanmakuSession;
import top.tobyprime.mcedia_core.client.renderer.MediaTextureImpl;

public final class ScreenPeripheral implements MediaPlayerPeripheral, AutoCloseable {
    private static final float MIN_SIZE = 0.01F;

    private final Level level;
    private final Quaternionf worldRotation = new Quaternionf();
    private final PlayerScreenDanmakuSession danmakuSession = new PlayerScreenDanmakuSession();

    private Vec3 position = Vec3.ZERO;
    private float screenWidth = 1.0F;
    private float screenHeight = 1.0F;
    private int minBrightness = 8;
    private ScreenFillMode fillMode = ScreenFillMode.KEEP_ASPECT_COVER;
    private @Nullable ResourceLocation backgroundTextureId = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    private @Nullable PlayerHost host;
    private boolean danmakuVisible = true;
    private boolean progressBarVisible = true;
    private @Nullable String statusText;
    private boolean closed;

    public ScreenPeripheral(Level level) {
        this.level = level;
    }

    public Level getLevel() {
        return level;
    }

    public Vec3 getPosition() {
        return position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public void setPos(double x, double y, double z) {
        this.position = new Vec3(x, y, z);
    }

    public Quaternionf getWorldRotation() {
        return new Quaternionf(worldRotation);
    }

    public void setWorldRotation(Quaternionf rotation) {
        worldRotation.set(rotation);
    }

    public void setWorldRotation(float x, float y, float z, float w) {
        worldRotation.set(x, y, z, w);
    }

    public float getScreenWidth() {
        return screenWidth;
    }

    public float getScreenHeight() {
        return screenHeight;
    }

    public void setScreenSize(float width, float height) {
        screenWidth = Math.max(width, MIN_SIZE);
        screenHeight = Math.max(height, MIN_SIZE);
    }

    public ScreenFillMode getFillMode() {
        return fillMode;
    }

    public void setFillMode(@Nullable ScreenFillMode fillMode) {
        this.fillMode = fillMode == null ? ScreenFillMode.FILL : fillMode;
    }

    public void setMinBrightness(int minBrightness) {
        this.minBrightness = Mth.clamp(minBrightness, 0, 15);
    }

    public int getMinBrightness() {
        return minBrightness;
    }

    public @Nullable ResourceLocation getBackgroundTextureId() {
        return backgroundTextureId;
    }

    public void setBackgroundTextureId(@Nullable ResourceLocation backgroundTextureId) {
        this.backgroundTextureId = backgroundTextureId;
    }

    public @Nullable MediaTextureImpl getTexture() {
        if (host == null) {
            return null;
        }

        var texture = host.getTexture();
        return texture instanceof MediaTextureImpl impl ? impl : null;
    }

    public PlayerScreenDanmakuSession getDanmakuSession() {
        return danmakuSession;
    }

    public boolean isDanmakuVisible() {
        return danmakuVisible;
    }

    public void setDanmakuVisible(boolean danmakuVisible) {
        this.danmakuVisible = danmakuVisible;
        if (!danmakuVisible) {
            danmakuSession.clear();
        }
    }

    public boolean isProgressBarVisible() {
        return progressBarVisible;
    }

    public void setProgressBarVisible(boolean progressBarVisible) {
        this.progressBarVisible = progressBarVisible;
    }

    public @Nullable String getStatusText() {
        return statusText;
    }

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

    public AABB getBoundingBox() {
        double halfWidth = screenWidth * 0.5F;
        return new AABB(
                position.x - halfWidth,
                position.y,
                position.z - halfWidth,
                position.x + halfWidth,
                position.y + screenHeight,
                position.z + halfWidth
        );
    }

    @Override
    public void setPlayerHost(@Nullable PlayerHost host) {
        this.host = host;
        if (host == null) {
            danmakuSession.clear();
        }
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isAlive() {
        return !closed && Minecraft.getInstance().level == level;
    }

    @Override
    public double getDistance() {
        var camera = Minecraft.getInstance().getCameraEntity();
        return camera == null ? Double.MAX_VALUE : camera.position().distanceToSqr(position);
    }

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.Screen;
    }

    @Override
    public boolean isVisible(@Nullable Frustum frustum) {
        return frustum == null || frustum.isVisible(getBoundingBox());
    }

    @Override
    public void close() {
        closed = true;
        host = null;
        danmakuSession.clear();
    }

    public enum ScreenFillMode {
        FILL,
        KEEP_ASPECT_COVER
    }
}

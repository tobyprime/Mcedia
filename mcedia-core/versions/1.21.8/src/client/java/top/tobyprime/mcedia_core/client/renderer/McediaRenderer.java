package top.tobyprime.mcedia_core.client.renderer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import top.tobyprime.mcedia_core.client.player.HudScreenPeripheral;
import top.tobyprime.mcedia_core.client.player.MediaPlayerPeripheral;
import top.tobyprime.mcedia_core.client.player.PlayerHost;
import top.tobyprime.mcedia_core.client.player.ScreenPeripheral;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MC 1.21.8 variant: renders screens without SubmitNodeCollector/CameraRenderState/LevelRenderState.
 * Uses Tesselator + RenderType.draw() for self-contained quad rendering.
 */
public final class McediaRenderer {
    private static final McediaRenderer INSTANCE = new McediaRenderer();

    private final Object lock = new Object();
    private final Set<ScreenPeripheral> screens = new LinkedHashSet<>();
    private final Set<MediaPlayerPeripheral> peripherals = new LinkedHashSet<>();
    private volatile @Nullable Frustum currentFrustum;

    private McediaRenderer() {
    }

    public static McediaRenderer get() {
        return INSTANCE;
    }

    public void registerPeripheral(PlayerHost host, MediaPlayerPeripheral peripheral) {
        host.addPeripheral(peripheral);
        if (!host.getPeripherals().contains(peripheral)) {
            return;
        }
        synchronized (lock) {
            peripherals.add(peripheral);
            if (peripheral instanceof ScreenPeripheral screen) {
                screens.add(screen);
            }
        }
    }

    public void unregisterPeripheral(MediaPlayerPeripheral peripheral) {
        synchronized (lock) {
            peripherals.remove(peripheral);
            if (peripheral instanceof ScreenPeripheral screen) {
                screens.remove(screen);
            }
        }
    }

    public Set<MediaPlayerPeripheral> getPeripherals() {
        synchronized (lock) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(peripherals));
        }
    }

    public Set<MediaPlayerPeripheral> getPeripherals(PlayerHost host) {
        return host.getPeripherals();
    }

    public boolean hasActivePeripheral(PlayerHost host, top.tobyprime.mcedia_core.client.player.PeripheralType type) {
        for (var peripheral : host.getPeripherals()) {
            if (peripheral.getPeripheralType() == type && peripheral.isActive()) {
                return true;
            }
        }
        return false;
    }

    public void cleanup() {
        synchronized (lock) {
            peripherals.removeIf(peripheral -> !peripheral.isAlive());
            screens.removeIf(screen -> !screen.isAlive());
        }
    }

    public void setCurrentFrustum(@Nullable Frustum frustum) {
        this.currentFrustum = frustum;
    }

    public @Nullable Frustum getCurrentFrustum() {
        return currentFrustum;
    }

    public void submitScreens(MultiBufferSource.BufferSource bufferSource, Camera camera) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        var frustum = currentFrustum;
        var cameraPos = camera.getPosition();

        Set<ScreenPeripheral> snapshot;
        synchronized (lock) {
            snapshot = new LinkedHashSet<>(screens);
        }

        for (var screen : snapshot) {
            if (!screen.isAlive()) continue;
            if (frustum != null && !screen.isVisible(frustum)) continue;

            var state = PlayerScreenEntityRenderer.createRenderState();
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(screen.getPosition()));
            PlayerScreenEntityRenderer.extractRenderState(screen, state, light);
            PlayerScreenEntityRenderer.submit(state, screen.getPosition(), cameraPos, bufferSource);
        }
    }

    public void submitHudScreens(GuiGraphics guiGraphics) {
        Set<MediaPlayerPeripheral> activePeripherals;
        synchronized (lock) {
            activePeripherals = new LinkedHashSet<>(peripherals);
        }

        for (var peripheral : activePeripherals) {
            if (peripheral instanceof HudScreenPeripheral hudScreen && hudScreen.isAlive()) {
                HudScreenRenderer.render(hudScreen, guiGraphics);
            }
        }
    }
}

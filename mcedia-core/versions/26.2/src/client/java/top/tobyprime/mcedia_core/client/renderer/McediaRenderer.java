package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
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

    public void submitScreens(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector) {
        Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        var frustum = getCurrentFrustum();
        for (var screen : snapshotScreens()) {
            if (!screen.isAlive()) {
                continue;
            }
            if (frustum != null && !screen.isVisible(frustum)) {
                continue;
            }

            var screenPos = screen.getPosition();
            var state = PlayerScreenEntityRenderer.createRenderState();
            int light = LevelRenderer.getLightCoords(level, BlockPos.containing(screenPos));
            PlayerScreenEntityRenderer.extractRenderState(screen, state, light);
            poseStack.pushPose();
            poseStack.translate(
                    screenPos.x - cameraPos.x,
                    screenPos.y - cameraPos.y,
                    screenPos.z - cameraPos.z
            );
            PlayerScreenEntityRenderer.submit(state, poseStack, submitNodeCollector, levelRenderState.cameraRenderState);
            poseStack.popPose();
        }
    }

    public void submitHudScreens(GuiGraphicsExtractor guiGraphics) {
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

    private Set<ScreenPeripheral> snapshotScreens() {
        synchronized (lock) {
            return new LinkedHashSet<>(screens);
        }
    }
}

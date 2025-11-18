package top.tobyprime.mcedia;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.network.chat.Component;
import top.tobyprime.mcedia.mixin_bridge.ISoundEngineBridge;
import top.tobyprime.mcedia.mixin_bridge.ISoundManagerBridge;

public class Utils {
    public static SoundEngineExecutor getAudioExecutor() {
        var manager = (ISoundManagerBridge) Minecraft.getInstance().getSoundManager();
        var engine = (ISoundEngineBridge) manager.mcedia$getSoundManager();
        return engine.mcedia$getExecutor();
    }

    public static void msgToPlayer(String msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            msgToPlayer(Component.literal("§e[Mcedia] §f" + msg));
        }
    }

    public static void msgToPlayer(Component mutableComponent) {
        // 新版 不能在 非渲染线程调用 displayClientMessage
        if (RenderSystem.isOnRenderThread()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(mutableComponent, false);
            }
        } else {
            Minecraft.getInstance().execute(() -> {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(mutableComponent, false);
                }
            });
        }

    }
}

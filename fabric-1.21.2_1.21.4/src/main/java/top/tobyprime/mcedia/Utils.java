package top.tobyprime.mcedia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import top.tobyprime.mcedia.mixin_bridge.ISoundEngineBridge;
import top.tobyprime.mcedia.mixin_bridge.ISoundManagerBridge;

public class Utils {
    public static SoundEngineExecutor getAudioExecutor() {
        var manager = (ISoundManagerBridge) Minecraft.getInstance().getSoundManager();
        var engine = (ISoundEngineBridge) manager.mcdia$getSoundManager();
        return engine.mcdia$getExecutor();
    }

    public static void msgToPlayer(String msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(msg), false);
        }
    }

    public static void msgToPlayer(MutableComponent mutableComponent) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(mutableComponent, false);
        }

    }
}

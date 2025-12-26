package top.tobyprime.mcedia.mixin_bridge;

import net.minecraft.client.sounds.SoundEngineExecutor;

public interface ISoundEngineBridge {
    SoundEngineExecutor mcedia$getExecutor();
}

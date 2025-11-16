package top.tobyprime.mcedia.mixin_bridge;

import net.minecraft.client.sounds.SoundEngineExecutor;

public interface ISoundEngineBridge {
    public SoundEngineExecutor mcdia$getExecutor();
}

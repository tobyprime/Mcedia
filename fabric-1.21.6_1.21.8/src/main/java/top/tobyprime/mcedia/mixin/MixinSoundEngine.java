package top.tobyprime.mcedia.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import top.tobyprime.mcedia.mixin_bridge.ISoundEngineBridge;

@Mixin(SoundEngine.class)
public class MixinSoundEngine implements ISoundEngineBridge {
    @Final
    @Shadow
    private SoundEngineExecutor executor;

    @Unique
    @Override
    public SoundEngineExecutor mcedia$getExecutor() {
        return executor;
    }
}

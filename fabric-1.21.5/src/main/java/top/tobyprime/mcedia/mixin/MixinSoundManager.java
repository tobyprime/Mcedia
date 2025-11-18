package top.tobyprime.mcedia.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import top.tobyprime.mcedia.mixin_bridge.ISoundManagerBridge;

@Mixin(SoundManager.class)
public class MixinSoundManager implements ISoundManagerBridge {
    @Final
    @Shadow
    private SoundEngine soundEngine;

    @Override
    public SoundEngine mcdia$getSoundManager() {
        return soundEngine;
    }
}

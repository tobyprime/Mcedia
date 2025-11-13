package top.tobyprime.mcedia.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("oldFovModifier")
    float getOldFovModifier();

    @Accessor("fovModifier")
    float getFovModifier();
}
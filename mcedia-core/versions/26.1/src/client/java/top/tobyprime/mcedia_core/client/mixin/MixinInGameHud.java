package top.tobyprime.mcedia_core.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia_core.client.renderer.McediaRenderer;

@Mixin(Gui.class)
public class MixinInGameHud {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mcedia$renderHudScreens(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        McediaRenderer.get().submitHudScreens(graphics);
    }
}

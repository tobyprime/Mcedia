package top.tobyprime.mcedia_core.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia_core.client.debug.DebugRendererAudioHitbox;
import top.tobyprime.mcedia_core.client.player.MediaPlayerHostManager;
import top.tobyprime.mcedia_core.client.renderer.McediaRenderer;

import java.util.List;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void mcedia$afterRenderEntities(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, DeltaTracker deltaTracker, List<Entity> entities, CallbackInfo ci) {
        var frustum = McediaRenderer.get().getCurrentFrustum();
        MediaPlayerHostManager.get().tickVideo(frustum);
        McediaRenderer.get().submitScreens(bufferSource, camera);
        DebugRendererAudioHitbox.submit(poseStack, bufferSource, camera, frustum);
    }
}

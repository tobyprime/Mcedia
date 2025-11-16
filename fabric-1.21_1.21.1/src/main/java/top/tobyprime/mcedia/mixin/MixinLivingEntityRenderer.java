package top.tobyprime.mcedia.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity; // [最终修正] 引入 Entity
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.PlayerAgent;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void onRender(LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (entity instanceof ArmorStand armorStand) {
            PlayerAgent agent = Mcedia.getInstance().getEntityToPlayerMap().get(armorStand);
            if (agent != null) {
                agent.render(armorStand, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            }
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V")
    )
    private void onRenderLayer(RenderLayer instance, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, Entity entity, float f, float g, float h, float j, float k, float l) {
        if (entity instanceof ArmorStand && Mcedia.getInstance().getEntityToPlayerMap().containsKey(entity)) {
            return;
        }

        // [最终修正] 直接传递 entity，不进行任何强制类型转换
        instance.render(poseStack, multiBufferSource, i, entity, f, g, h, j, k, l);
    }
}
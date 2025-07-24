package top.tobyprime.mcedia.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmorStandArmorModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.mixin_bridge.IArmorStandRenderStateBridge;


@Mixin(ArmorStandRenderer.class)
public abstract class MixinArmorStandRenderer extends LivingEntityRenderer<ArmorStand, ArmorStandRenderState, ArmorStandArmorModel> {
    public MixinArmorStandRenderer(EntityRendererProvider.Context context, ArmorStandArmorModel entityModel, float f) {
        super(context, entityModel, f);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V", at=@At("RETURN"))
    public void extractRenderState(ArmorStand armorStand, ArmorStandRenderState armorStandRenderState, float f, CallbackInfo ci){
        ((IArmorStandRenderStateBridge) armorStandRenderState).mcdia$setAgent(Mcedia.getInstance().getEntityToPlayerMap().getOrDefault(armorStand, null));
    }
    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at=@At("HEAD"))
    public void render(ArmorStandRenderState armorStandRenderState, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci){
        var player = ((IArmorStandRenderStateBridge) armorStandRenderState).mcdia$getAgent();

        if (player == null){
            return;
        }

        player.render(armorStandRenderState, multiBufferSource ,poseStack, i);
    }

    @Override
    public boolean shouldRender(ArmorStand entity, Frustum frustum, double d, double e, double f) {
        if (Mcedia.getInstance().getEntityToPlayerMap().containsKey(entity)) {
            return true;
        }
        return super.shouldRender(entity, frustum, d, e, f);
    }
}

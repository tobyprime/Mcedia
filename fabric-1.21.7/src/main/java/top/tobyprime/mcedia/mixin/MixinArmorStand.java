package top.tobyprime.mcedia.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia.Mcedia;

@Mixin(LivingEntity.class)
public class MixinArmorStand  {
    @Inject(method = "Lnet/minecraft/world/entity/LivingEntity;tick()V", at=@At("RETURN"))
    public void tick(CallbackInfo ci) {
        Object to = this;
        if(to instanceof ArmorStand armorStand) {
            Mcedia.getInstance().HandleMcdiaPlayerEntity(armorStand);
        }
    }
}

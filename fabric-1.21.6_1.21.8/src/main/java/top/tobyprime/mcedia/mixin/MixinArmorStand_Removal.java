// mixin/MixinArmorStand_Removal.java (修正版)
package top.tobyprime.mcedia.mixin;

import net.minecraft.world.entity.Entity; // [重要] 导入 Entity 类
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia.Mcedia;

// [修正 #1] 将 Mixin 的目标从 ArmorStand.class 改为 Entity.class
@Mixin(Entity.class)
public class MixinArmorStand_Removal {

    @Inject(method = "discard", at = @At("HEAD"))
    private void onDiscard(CallbackInfo ci) {
        // 'this' 现在是任何即将被移除的 Entity 实例
        Entity self = (Entity) (Object) this;

        // [修正 #2] 必须检查这个实体是不是一个 ArmorStand
        if (self instanceof ArmorStand armorStand) {
            // 如果是，我们才执行清理逻辑
            if (Mcedia.getInstance() != null) {
                Mcedia.getInstance().removePlayerAgent(armorStand);
            }
        }
    }
}
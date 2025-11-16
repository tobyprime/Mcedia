package top.tobyprime.mcedia.mixin;

import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.tobyprime.mcedia.PlayerAgent;
import top.tobyprime.mcedia.mixin_bridge.IArmorStandRenderStateBridge;


@Mixin(ArmorStandRenderState.class)
public class MixinArmorStandRenderState implements IArmorStandRenderStateBridge {
    @Unique
    PlayerAgent mcdia$playerAgent;


    @Override
    public void mcdia$setAgent(PlayerAgent playerAgent) {
        this.mcdia$playerAgent = playerAgent;
    }

    @Override
    public PlayerAgent mcdia$getAgent() {
        return mcdia$playerAgent;
    }
}

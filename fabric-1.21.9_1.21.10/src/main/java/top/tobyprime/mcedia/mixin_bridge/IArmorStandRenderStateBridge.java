package top.tobyprime.mcedia.mixin_bridge;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.PlayerAgent;

public interface IArmorStandRenderStateBridge {
    void mcdia$setAgent(@Nullable PlayerAgent agent);

    @Nullable
    PlayerAgent mcdia$getAgent();
}
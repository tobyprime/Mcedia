package top.tobyprime.mcedia.mixin_bridge;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.client.PlayerAgent;

public interface IArmorStandRenderStateBridge {
    public void mcdia$setAgent(@Nullable PlayerAgent playerAgent);
    public @Nullable PlayerAgent mcdia$getAgent();
}

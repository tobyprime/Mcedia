package top.tobyprime.mcedia.mixin_bridge;

import org.spongepowered.asm.mixin.Unique;

public interface FFmpegFrameGrabberMixinBridge {
    @Unique
    void mcedia$setProcessImage(boolean mcedia$ProcessImage);
    boolean mcedia$getProcessImage();
}

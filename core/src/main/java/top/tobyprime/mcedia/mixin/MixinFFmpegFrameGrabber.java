package top.tobyprime.mcedia.mixin;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia.mixin_bridge.FFmpegFrameGrabberMixinBridge;

@Mixin(FFmpegFrameGrabber.class)
public class MixinFFmpegFrameGrabber implements FFmpegFrameGrabberMixinBridge {
    @Unique
    private boolean mcedia$ProcessImage = true;

    @Unique
    @Override
    public void mcedia$setProcessImage(boolean mcedia$ProcessImage) {
        this.mcedia$ProcessImage = mcedia$ProcessImage;
    }

    @Unique
    @Override
    public boolean mcedia$getProcessImage() {
        return mcedia$ProcessImage;
    }

    @Inject(method = "processImage()V", at = @At("HEAD"), cancellable = true, remap = false)
    public void onProcessImage(CallbackInfo ci) {
        if (!mcedia$getProcessImage()) {
            ci.cancel();
        }
    }
}

package top.tobyprime.mcedia.mixin;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.tobyprime.mcedia.decoders.ffmpeg.FfmpegProcessImageFlags;

@Mixin(FFmpegFrameGrabber.class)
public class MixinFFmpegFrameGrabber {
    // 不要新增任何字段、方法，否则会导致 debug 卡死
    @Inject(method = "processImage()V", at = @At("HEAD"), cancellable = true, remap = false)
    public void onProcessImage(CallbackInfo ci) {
        if (!FfmpegProcessImageFlags.isEnableProcessImage((FFmpegFrameGrabber)(Object) this)) {
            ci.cancel();
        }
    }
}

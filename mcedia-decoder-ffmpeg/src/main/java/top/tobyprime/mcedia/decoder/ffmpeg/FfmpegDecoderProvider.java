package top.tobyprime.mcedia.decoder.ffmpeg;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.api.config.DecoderConfiguration;
import top.tobyprime.mcedia.api.decoder.DecoderFactory;
import top.tobyprime.mcedia.api.decoder.DecoderProvider;
import top.tobyprime.mcedia.api.media.MediaPlayInfo;
import top.tobyprime.mcedia.runtime.NativeRuntime;

public class FfmpegDecoderProvider implements DecoderProvider {
    @Override
    public boolean supports(@NotNull MediaPlayInfo playInfo, @NotNull DecoderConfiguration configuration) {
        return NativeRuntime.isAvailable() || NativeRuntime.ensureLoaded();
    }

    @Override
    public @NotNull DecoderFactory getFactory() {
        return FfmpegDecoder::new;
    }
}

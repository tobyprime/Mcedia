package top.tobyprime.mcedia.interfaces;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.decoders.VideoFrame;

public interface ITexture {
    void upload(@Nullable VideoFrame frame);
}

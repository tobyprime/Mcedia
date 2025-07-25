package top.tobyprime.mcedia.interfaces;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.core.AudioBufferData;

public interface IAudioSource {
    void upload(@Nullable AudioBufferData buffer);
    void clearBuffer();
}

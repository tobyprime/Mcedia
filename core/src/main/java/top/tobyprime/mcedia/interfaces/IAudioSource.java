package top.tobyprime.mcedia.interfaces;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.decoders.AudioBufferData;

public interface IAudioSource {
    float getLastX();

    float getLastY();

    float getLastZ();

    void upload(@Nullable AudioBufferData buffer);

    void setPitch(float pitch);

    void clearBuffer();

    int getId();
}

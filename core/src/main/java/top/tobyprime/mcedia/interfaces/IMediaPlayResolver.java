package top.tobyprime.mcedia.interfaces;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.core.IMediaPlay;

public interface IMediaPlayResolver {
    public boolean isSupported(@NotNull String url);

    @NotNull
    public IMediaPlay resolve(@NotNull String playUrl);
}

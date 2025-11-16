package top.tobyprime.mcedia.interfaces;

import org.jetbrains.annotations.NotNull;

public interface IMediaPlayResolver {
    public boolean isSupported(@NotNull String url);

    @NotNull
    public IMediaPlay resolve(@NotNull String playUrl);
}

package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.core.DirectLinkMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;

public class DirectLinkMediaPlayResolver implements IMediaPlayResolver {
    @Override
    public boolean isSupported(@NotNull String url) {
        return Configs.ALLOW_DIRECT_LINK;
    }

    @Override
    public @NotNull IMediaPlay resolve(@NotNull String playUrl) {
        return new DirectLinkMediaPlay(playUrl);
    }
}

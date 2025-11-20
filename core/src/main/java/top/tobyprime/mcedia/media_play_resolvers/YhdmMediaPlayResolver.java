package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.interfaces.IMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;
import top.tobyprime.mcedia.yhdm.YhdmMediaPlay;

public class YhdmMediaPlayResolver implements IMediaPlayResolver {

    @Override
    public boolean isSupported(String url) {
        return url != null && url.contains("yhdm.one/vod-play/");
    }

    @Override
    public @NotNull IMediaPlay resolve(@NotNull String playUrl) {
        return new YhdmMediaPlay(playUrl);
    }
}

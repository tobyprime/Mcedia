package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.bilibili.BilibiliLiveMediaPlay;
import top.tobyprime.mcedia.core.IMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;

public class BilibiliLiveMediaPlayResolver implements IMediaPlayResolver {

    @Override
    public boolean isSupported(@NotNull String url) {
        return url.startsWith("https://live.bilibili.com/");
    }

    @Override
    public @NotNull IMediaPlay resolve(@NotNull String playUrl) {
        return new BilibiliLiveMediaPlay(playUrl);
    }
}

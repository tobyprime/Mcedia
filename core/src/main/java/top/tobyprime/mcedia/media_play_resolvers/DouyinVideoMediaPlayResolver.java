package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.core.IMediaPlay;
import top.tobyprime.mcedia.douyin.DouyinVideoMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;

public class DouyinVideoMediaPlayResolver implements IMediaPlayResolver {

    @Override
    public boolean isSupported(@NotNull String url) {
        return url.contains("https://v.douyin.com/");
    }

    @Override
    public @NotNull IMediaPlay resolve(@NotNull String playUrl) {
        return new DouyinVideoMediaPlay(playUrl);
    }
}

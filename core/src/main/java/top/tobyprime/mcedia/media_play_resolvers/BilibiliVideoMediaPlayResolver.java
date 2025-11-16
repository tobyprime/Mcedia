package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.bilibili.BilibiliVideoMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;

public class BilibiliVideoMediaPlayResolver implements IMediaPlayResolver {

    public boolean isSupported(@NotNull String url) {
        return url.contains("bilibili.com/video/");
    }

    @Override
    public @NotNull IMediaPlay resolve(@NotNull String playUrl) {
        return new BilibiliVideoMediaPlay(playUrl);
    }
}

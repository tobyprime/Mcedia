package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.bilibili.BilibiliBangumiMediaPlay;
import top.tobyprime.mcedia.core.IMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;

public class BilibiliBangumiMediaPlayResolver implements IMediaPlayResolver {


    @Override
    public boolean isSupported(@NotNull String url) {
        return  url.contains("bilibili.com/bangumi/play/");
    }

    @Override
    public @NotNull IMediaPlay resolve(@NotNull String playUrl) {
        return  new BilibiliBangumiMediaPlay(playUrl);
    }
}

package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.bilibili.BilibiliVideoMediaPlay;
import top.tobyprime.mcedia.core.IMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BilibiliVideoMediaPlayResolver implements IMediaPlayResolver {

    public boolean isSupported(@NotNull String url) {
        return url.contains("bilibili.com/video/");
    }

    @Override
    public @NotNull IMediaPlay resolve(@NotNull String playUrl) {
        if (!isSupported(playUrl)) {
            return null;
        }
        return new BilibiliVideoMediaPlay(playUrl);
    }
}

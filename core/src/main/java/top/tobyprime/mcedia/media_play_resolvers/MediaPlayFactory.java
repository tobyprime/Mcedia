package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.EmptyMediaPlay;
import top.tobyprime.mcedia.core.IMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPlayFactory {
    public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MediaInfoLoader-Async");
        t.setDaemon(true);
        return t;
    });
    private static Logger logger = LoggerFactory.getLogger(MediaPlayFactory.class);
    static public List<IMediaPlayResolver> resolvers = List.of(new BilibiliVideoMediaPlayResolver(), new BilibiliBangumiMediaPlayResolver());

    public static @NotNull IMediaPlay createMediaPlay(String url) {
        if (url == null) throw new NullPointerException("url is null");
        try {
            for (IMediaPlayResolver resolver : resolvers) {
                if (resolver.isSupported(url)) {
                    return resolver.resolve(url);
                }
            }
        }catch (Exception e) {
            logger.error("获取 MediaPlay 失败", e);
            return new EmptyMediaPlay("无法解析视频: " +url);
        }

        return new EmptyMediaPlay("无法解析视频: " + url);
    }

    public static boolean isSupported(String url) {
        for (IMediaPlayResolver resolver : resolvers) {
            if (resolver.isSupported(url)) {
                return true;
            }
        }
        return false;
    }
}

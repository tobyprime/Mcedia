package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.video_fetcher.BiliBiliVideoFetcher;

public class BilibiliVideoProvider implements IMediaProvider {
    @Override
    public boolean isSupported(String url) {
        return url != null && url.contains("bilibili.com/video/");
    }

    @Override
    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        return BiliBiliVideoFetcher.fetch(url, cookie, desiredQuality);
    }
}
package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.video_fetcher.BilibiliBangumiFetcher;

public class BilibiliBangumiProvider implements IMediaProvider {
    @Override
    public boolean isSupported(String url) {
        // 精确匹配番剧链接
        return url != null && url.contains("bilibili.com/bangumi/play/");
    }

    @Override
    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        return BilibiliBangumiFetcher.fetch(url, cookie, desiredQuality);
    }
}
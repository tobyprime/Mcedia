package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.interfaces.IMediaProvider;
import top.tobyprime.mcedia.video_fetcher.BiliBiliLiveFetcher;

import java.nio.file.Path;

/**
 * 负责处理 Bilibili 直播 (live.bilibili.com) 的 Provider。
 */
public class BilibiliLiveProvider implements IMediaProvider {

    @Override
    public boolean isSupported(String url) {
        // 精确匹配 Bilibili 直播链接
        return url != null && url.startsWith("https://live.bilibili.com/");
    }

    @Override
    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        // 现在 fetch 直接返回 VideoInfo，我们直接返回它的结果即可
        return BiliBiliLiveFetcher.fetch(url, desiredQuality);
    }
}
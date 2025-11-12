package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.video_fetcher.DouyinVideoFetcher;
import java.net.http.HttpClient;
import java.util.Map;

public class DouyinVideoProvider implements IMediaProvider {
    private static final String DOUYIN_MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1";

    public DouyinVideoProvider() {
    }

    @Override
    public boolean isSupported(String url) {
        if (url == null) return false;
        return url.contains("v.douyin.com/")
                || url.contains("www.douyin.com/video/")
                || url.contains("iesdouyin.com/share/video/");
    }

    @Override
    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        // 接收 DouyinVideoDetails 对象
        DouyinVideoFetcher.DouyinVideoDetails details = DouyinVideoFetcher.fetch(url);

        if (details == null) {
            throw new Exception("解析抖音视频失败，未能获取到视频详情: " + url);
        }

        Map<String, String> customHeaders = Map.of(
                "User-Agent", DOUYIN_MOBILE_USER_AGENT,
                "Referer", url
        );

        // 使用从 details 对象中获取的真实标题和作者
        return new VideoInfo(details.getVideoUrl(), null, details.getTitle(), details.getAuthor(), customHeaders);
    }

    @Override
    public boolean isSeekSupported() {
        return true;
    }

    @Override
    public String getSafetyWarning() {
        return null;
    }
}
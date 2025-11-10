package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.video_fetcher.DouyinVideoFetcher;
import java.net.http.HttpClient;
import java.util.Map;

public class DouyinVideoProvider implements IMediaProvider {
    private static final String DOUYIN_MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1";

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
        // 'url' 在这里是展开后的长链接, e.g., "https://www.iesdouyin.com/share/video/..."
        String directVideoUrl = DouyinVideoFetcher.fetch(url);
        if (directVideoUrl == null) {
            throw new Exception("解析抖音视频失败，未能获取到直接播放链接: " + url);
        }

        // [关键修改] 将完整的网页URL作为Referer
        Map<String, String> customHeaders = Map.of(
                "User-Agent", DOUYIN_MOBILE_USER_AGENT,
                "Referer", url // 使用完整的、展开后的URL作为Referer
        );

        return new VideoInfo(directVideoUrl, null, "抖音视频", "未知作者", customHeaders);
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
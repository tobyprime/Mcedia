package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.interfaces.IMediaProvider;
import top.tobyprime.mcedia.video_fetcher.YhdmFetcher;

import java.nio.file.Path;
import java.util.Map;

public class YhdmProvider implements IMediaProvider {

    @Override
    public boolean isSupported(String url) {
        return url != null && url.contains("yhdm.one/vod-play/");
    }

    @Override
    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        VideoInfo basicInfo = YhdmFetcher.fetch(url);
        if (basicInfo == null) {
            throw new Exception("无法从 Yhdm 解析视频信息: " + url);
        }

        Map<String, String> customHeaders = Map.of("Referer", "https://yhdm.one/");

        return new VideoInfo(
                basicInfo.getVideoUrl(),
                basicInfo.getAudioUrl(),
                basicInfo.getTitle(),
                basicInfo.getAuthor(),
                customHeaders
        );
    }

    @Override
    public boolean isSeekSupported() {
        return false;
    }

    @Override
    @Nullable
    public String getSafetyWarning() {
        return "§a[Mcedia] §e提示: 不要轻信视频中的广告，注意甄别，防止上当受骗。";
    }
}
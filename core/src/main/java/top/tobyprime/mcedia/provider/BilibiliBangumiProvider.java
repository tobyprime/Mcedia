// BilibiliBangumiProvider.java
package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.provider.BilibiliBangumiInfo;
import top.tobyprime.mcedia.video_fetcher.BilibiliBangumiFetcher;

public class BilibiliBangumiProvider implements IMediaProvider {
    @Override
    public boolean isSupported(String url) {
        return url != null && url.contains("bilibili.com/bangumi/play/");
    }

    @Override
    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        BilibiliBangumiInfo bangumiInfo = BilibiliBangumiFetcher.fetch(url, cookie, desiredQuality);
        return bangumiInfo.getVideoInfo();
    }
}
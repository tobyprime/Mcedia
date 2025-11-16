package top.tobyprime.mcedia.provider; //包声明，表示这个类属于哪个包，包名用来组织代码，类似文件夹路径

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.interfaces.IMediaProvider;
import top.tobyprime.mcedia.video_fetcher.BiliBiliVideoFetcher;

import java.nio.file.Path;

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
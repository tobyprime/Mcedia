package top.tobyprime.mcedia.provider;

import top.tobyprime.mcedia.video_fetcher.YhdmFetcher;

public class YhdmProvider implements IMediaProvider {

    /**
     * 判断给定的URL是否属于 yhdm.one 的播放页
     * 方法名从 canHandle 修改为 isSupported 以匹配接口
     */
    @Override
    public boolean isSupported(String url) {
        return url != null && url.contains("yhdm.one/vod-play/");
    }

    /**
     * 如果 isSupported 返回 true，此方法将被调用
     * @param url 输入的原始URL
     * @param cookie Cookie，此提供者不需要，忽略
     * @param desiredQuality 清晰度，此提供者不需要，忽略
     * @return 包含 .m3u8 链接的 VideoInfo 对象
     */
    @Override
    public VideoInfo resolve(String url, String cookie, String desiredQuality) throws Exception {
        // 直接调用我们的Fetcher来完成工作
        return YhdmFetcher.fetch(url);
    }

    @Override
    public boolean isSeekSupported() {
        return false;
    }
}
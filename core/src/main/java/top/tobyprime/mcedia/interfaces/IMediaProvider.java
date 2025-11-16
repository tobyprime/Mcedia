package top.tobyprime.mcedia.interfaces;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.nio.file.Path;

/**
 * 媒体提供者接口，定义了一个视频源提供者应该具备的基本功能。
 */
public interface IMediaProvider {
    /**
     * 判断此Provider是否能处理给定的URL。
     * @param url 输入的视频页URL
     * @return 如果能处理，返回true，否则false
     */
    boolean isSupported(String url);

    /**
     * 异步解析URL并返回包含真实视频流地址的VideoInfo对象。
     * @param url 视频页URL
     * @param cookie (可选) 用于身份验证的Cookie
     * @return 包含可播放URL的VideoInfo对象
     * @throws Exception 解析失败时抛出异常
     */
    VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception; // [修改]

    default boolean isSeekSupported() {
        return  true;
    }

    @Nullable
    default String getSafetyWarning() {
        return null;
    }
}
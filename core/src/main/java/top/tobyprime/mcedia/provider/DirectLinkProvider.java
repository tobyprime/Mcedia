package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;

/**
 * 默认的媒体提供者。
 * 它不再检查任何特定格式，而是作为处理所有其他链接的“最终防线”。
 */
public class DirectLinkProvider implements IMediaProvider {

    /**
     * 这个方法现在永远返回 true，因为它被设计为接管所有其他提供者不处理的链接。
     * 但实际上，在新的 MediaProviderRegistry 逻辑中，这个方法甚至不会被调用。
     */
    @Override
    public boolean isSupported(String url) {
        return true;
    }

    /**
     * 对于直链，直接将原始URL封装到VideoInfo中。
     */
    @Override
    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        return new VideoInfo(url, null);
    }

    /**
     * 假设大部分直链都支持跳转。如果某个特定流不支持，FFmpeg的处理通常也比我们的代码更周全
     */
    @Override
    public boolean isSeekSupported() {
        return true;
    }

    @Override
    public String getSafetyWarning() {
        return null;
    }
}
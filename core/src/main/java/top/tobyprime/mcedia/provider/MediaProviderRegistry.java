package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 媒体提供者注册表，统一管理和查询所有的 IMediaProvider。
 */
public class MediaProviderRegistry {
    private static final MediaProviderRegistry INSTANCE = new MediaProviderRegistry();
    private final List<IMediaProvider> providers = new ArrayList<>();

    private MediaProviderRegistry() {
        // 私有构造函数，确保单例
    }

    public static MediaProviderRegistry getInstance() {
        return INSTANCE;
    }

    public void register(IMediaProvider provider) {
        providers.add(provider);
    }

    public Optional<IMediaProvider> findProvider(String url) {
        return providers.stream()
                .filter(p -> p.isSupported(url))
                .findFirst();
    }

    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        IMediaProvider provider = findProvider(url)
                .orElseThrow(() -> new RuntimeException("No suitable provider found for URL: " + url));
        return provider.resolve(url, cookie, desiredQuality);
    }
}
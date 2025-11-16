package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.interfaces.IMediaProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 媒体提供者注册表，统一管理和查询所有的 IMediaProvider。
 */
public class MediaProviderRegistry {
    private static final MediaProviderRegistry INSTANCE = new MediaProviderRegistry();
    private final List<IMediaProvider> providers = new ArrayList<>();

    private final DirectLinkProvider defaultProvider = new DirectLinkProvider();

    private MediaProviderRegistry() {
    }

    public static MediaProviderRegistry getInstance() {
        return INSTANCE;
    }

    public void register(IMediaProvider provider) {
        if (provider != null && !providers.contains(provider)) {
            providers.add(provider);
        }
    }

    public Optional<IMediaProvider> findProvider(String url) {
        return providers.stream()
                .filter(p -> p.isSupported(url))
                .findFirst();
    }

    public VideoInfo resolve(String url, String cookie, String desiredQuality) throws Exception {
        for (IMediaProvider provider : providers) {
            if (provider.isSupported(url)) {
                return provider.resolve(url, cookie, desiredQuality);
            }
        }
        return defaultProvider.resolve(url, cookie, desiredQuality);
    }

    @Nullable
    public IMediaProvider getProviderForUrl(String url) {
        for (IMediaProvider provider : providers) {
            if (provider.isSupported(url)) {
                return provider;
            }
        }
        return defaultProvider;
    }
}
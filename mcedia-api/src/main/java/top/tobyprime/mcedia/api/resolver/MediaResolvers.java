package top.tobyprime.mcedia.api.resolver;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.media.Media;
import top.tobyprime.mcedia.api.media.MediaCollection;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class MediaResolvers {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaResolvers.class);

    private static final Map<String, MediaResolver> RESOLVERS = new ConcurrentHashMap<>();
    private static final List<PrioritizedParser> PARSERS = new CopyOnWriteArrayList<>();
    private static final Map<String, MediaCollectionResolver> COLLECTION_RESOLVERS = new ConcurrentHashMap<>();
    private static final AtomicLong STATE_REVISION = new AtomicLong();

    private record PrioritizedParser(MediaUrlParser parser, int priority) {
    }

    private MediaResolvers() {
    }

    /**
     * 解析器依赖的外部状态（如账号登录态）变化时递增的版本号。
     * 调用方可用它判断是否需要重新解析已缓存/失败的媒体。
     */
    public static long getStateRevision() {
        return STATE_REVISION.get();
    }

    /**
     * 通知解析器外部状态发生变化（例如 Bilibili 登录/登出后，可重新解析以获取更高清晰度）。
     */
    public static void notifyStateChanged() {
        STATE_REVISION.incrementAndGet();
    }

    // -- platform resolver registry --

    public static void register(@NotNull String platform, @NotNull MediaResolver resolver) {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("Resolver platform cannot be blank");
        }

        var normalizedPlatform = platform.trim().toLowerCase(Locale.ROOT);
        var previous = RESOLVERS.put(normalizedPlatform, Objects.requireNonNull(resolver, "resolver"));
        if (previous != null) {
            LOGGER.warn("Resolver for platform '{}' is overwritten.", normalizedPlatform);
        }
    }

    /**
     * 按 platform 名称直接解析 target，供 {@link MediaUrlParser} 实现内部委托使用。
     */
    public static @NotNull Media resolveByPlatform(@NotNull String platform, @NotNull String target) {
        var resolver = RESOLVERS.get(platform.toLowerCase(Locale.ROOT));
        if (resolver == null) {
            throw new IllegalArgumentException("No media resolver registered for platform: " + platform);
        }
        return Objects.requireNonNull(resolver.resolve(target), "resolver returned null media");
    }

    // -- non-standard URL parser registry (priority-based) --

    /**
     * 注册非标准播放字符串解析器，使用默认优先级 100。
     */
    public static void registerParser(@NotNull MediaUrlParser parser) {
        registerParser(parser, 100);
    }

    /**
     * 注册非标准播放字符串解析器，指定优先级（值越小优先级越高）。
     */
    public static void registerParser(@NotNull MediaUrlParser parser, int priority) {
        Objects.requireNonNull(parser, "parser");
        PARSERS.add(new PrioritizedParser(parser, priority));
        PARSERS.sort(Comparator.comparingInt(PrioritizedParser::priority));
    }

    // -- collection (album) resolver registry --

    /**
     * 注册专辑/合集解析器。同一 platform 重复注册会覆盖旧实现。
     */
    public static void registerCollectionResolver(@NotNull String platform, @NotNull MediaCollectionResolver resolver) {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("Collection resolver platform cannot be blank");
        }
        var normalizedPlatform = platform.trim().toLowerCase(Locale.ROOT);
        var previous = COLLECTION_RESOLVERS.put(normalizedPlatform, Objects.requireNonNull(resolver, "resolver"));
        if (previous != null) {
            LOGGER.warn("Collection resolver for platform '{}' is overwritten.", normalizedPlatform);
        }
    }

    /**
     * 尝试将 target 解析为专辑/合集。若已有注册的合集解析器将其识别为合集则返回合集，否则 empty。
     */
    public static @NotNull Optional<MediaCollection> tryResolveCollection(@NotNull String target) {
        Objects.requireNonNull(target, "target");
        for (var resolver : COLLECTION_RESOLVERS.values()) {
            var collection = resolver.tryResolveCollection(target.trim());
            if (collection.isPresent()) {
                return collection;
            }
        }
        return Optional.empty();
    }

    // -- test support --

    static void reset() {
        RESOLVERS.clear();
        PARSERS.clear();
        COLLECTION_RESOLVERS.clear();
        notifyStateChanged();
    }

    // -- resolution --

    /**
     * 解析播放字符串为 Media。
     * <p>
     * 按优先级依次尝试已注册的 {@link MediaUrlParser}，
     * 首个匹配的解析结果被返回。如无一匹配则抛出异常。
     */
    public static @NotNull Media resolve(@NotNull String url) {
        Objects.requireNonNull(url, "mediaUrl");
        var normalizedUrl = stripWrappingQuotes(url.trim());

        for (var entry : PARSERS) {
            var result = entry.parser().tryParse(normalizedUrl);
            if (result.isPresent()) {
                return result.get();
            }
        }

        throw new IllegalArgumentException("Unsupported media url: " + normalizedUrl);
    }

    private static String stripWrappingQuotes(String input) {
        if (input.length() >= 2) {
            char first = input.charAt(0);
            char last = input.charAt(input.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return input.substring(1, input.length() - 1).trim();
            }
        }
        return input;
    }

}

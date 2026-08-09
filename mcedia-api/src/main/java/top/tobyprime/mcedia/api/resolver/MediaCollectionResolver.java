package top.tobyprime.mcedia.api.resolver;

import org.jetbrains.annotations.NotNull;
import top.tobyprime.mcedia.api.media.MediaCollection;

import java.util.Optional;

/**
 * 将专辑/合集链接解析为 {@link MediaCollection}。
 * <p>
 * 与 {@link MediaResolver} 解析单个媒体不同，合集解析器先识别目标是否为合集形态，
 * 再产出条目列表；非合集目标应返回 {@link Optional#empty()}。
 */
@FunctionalInterface
public interface MediaCollectionResolver {
    @NotNull Optional<MediaCollection> tryResolveCollection(@NotNull String target);
}

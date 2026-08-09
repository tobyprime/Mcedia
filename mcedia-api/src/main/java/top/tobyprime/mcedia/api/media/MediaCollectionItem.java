package top.tobyprime.mcedia.api.media;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link MediaCollection} 中的单个条目。
 * <p>
 * 条目是轻量描述（标题、封面），播放时通过 {@link #getResolutionTarget()} 交给
 * {@link top.tobyprime.mcedia.api.resolver.MediaResolvers#resolve(String)} 惰性解析为可播放的 {@link Media}。
 */
public interface MediaCollectionItem {
    @NotNull String getTitle();

    @Nullable String getCoverUrl();

    /** 可交给 {@link top.tobyprime.mcedia.api.resolver.MediaResolvers#resolve(String)} 解析出可播放媒体的目标。 */
    @NotNull String getResolutionTarget();
}

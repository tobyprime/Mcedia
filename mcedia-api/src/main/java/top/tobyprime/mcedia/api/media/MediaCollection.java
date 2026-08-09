package top.tobyprime.mcedia.api.media;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 一个可被整体解析的媒体专辑/合集（番剧季度、收藏夹、播放列表等）。
 * <p>
 * 专辑本身不可直接播放；其中的每个条目都带有一个可交给
 * {@link top.tobyprime.mcedia.api.resolver.MediaResolvers#resolve(String)} 解析为可播放媒体的解析目标。
 */
public interface MediaCollection {
    @NotNull String getTitle();

    @Nullable String getCoverUrl();

    @NotNull List<MediaCollectionItem> getItems();
}

package top.tobyprime.mcedia.interfaces;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.provider.QualityInfo;

import java.util.List;

/**
 * 一个通用的、与 provider 无关的媒体信息接口，定义在 core 模块中。
 * Core 模块只通过这个接口来获取它需要知道的信息。
 */
public interface IMediaInfo {
    String getVideoUrl();
    @Nullable String getAudioUrl();
    @Nullable String getCurrentQuality();
    @Nullable List<QualityInfo> getAvailableQualities();
    boolean isMultiPart();
    int getPartNumber();
    @Nullable String getPartName();
    String getTitle();
    String getAuthor();
}

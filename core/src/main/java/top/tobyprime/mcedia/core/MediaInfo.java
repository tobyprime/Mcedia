package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.danmaku.Danmaku;

import java.util.List;
import java.util.Map;

public class MediaInfo {
    public String streamUrl;
    @Nullable
    public String audioUrl;
    public String rawUrl;
    public String title = "Unknown";
    public String author = "Unknown";
    public String platform = "Unknown";
    @Nullable
    public String cookie;
    @Nullable
    public Map<String, String> headers = null;
    @Nullable
    public List<Danmaku> danmakus = null;
}

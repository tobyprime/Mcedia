package top.tobyprime.mcedia.video_fetcher;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
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
    public final Map<String, String> headers = new HashMap<>();

}

package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import java.util.Map;

public class VideoInfo {
    private final String videoUrl;
    @Nullable
    private final String audioUrl;
    private final String title;
    private final String author;
    private final Map<String, String> headers;
    @Nullable
    private final String partName;
    private final int partNumber;

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author, @Nullable Map<String, String> headers, @Nullable String partName, int partNumber) {
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.title = title != null ? title : "未知视频";
        this.author = author != null ? author : "未知作者";
        this.headers = headers;
        this.partName = partName;
        this.partNumber = partNumber;
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author, @Nullable Map<String, String> headers) {
        this(videoUrl, audioUrl, title, author, headers, null, 0);
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author) {
        this(videoUrl, audioUrl, title, author, null, null, 0);
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl) {
        this(videoUrl, audioUrl, null, null, null, null, 0);
    }

    public String getVideoUrl() { return videoUrl; }
    @Nullable public String getAudioUrl() { return audioUrl; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    @Nullable public Map<String, String> getHeaders() { return headers; }
    @Nullable public String getPartName() { return partName; }
    public int getPartNumber() { return partNumber; }
    public boolean isMultiPart() {
        return partNumber > 0;
    }
}
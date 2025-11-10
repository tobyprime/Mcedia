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

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author, @Nullable Map<String, String> headers) {
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.title = title != null ? title : "未知视频";
        this.author = author != null ? author : "未知作者";
        this.headers = headers;
    }
    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author) {
        this(videoUrl, audioUrl, title, author, null);
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl) {
        this(videoUrl, audioUrl, null, null, null);
    }

    public String getVideoUrl() { return videoUrl; }
    public String getAudioUrl() { return audioUrl; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    @Nullable
    public Map<String, String> getHeaders() { return headers; }
}
package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;

public class VideoInfo {
    private final String videoUrl;
    @Nullable
    private final String audioUrl;
    private final String title;
    private final String author;

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author) {
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.title = title;
        this.author = author;
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl) {
        this(videoUrl, audioUrl, "未知标题", "未知作者");
    }

    public String getVideoUrl() { return videoUrl; }
    @Nullable
    public String getAudioUrl() { return audioUrl; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
}
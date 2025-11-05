package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;

/**
 * 封装从视频源解析出的最终可播放URL。
 * 支持音视频分离的格式。
 */
public class VideoInfo {
    private final String videoUrl;
    private final String audioUrl; // 可以为 null，表示音视频合并在 videoUrl 中

    public VideoInfo(String videoUrl, @Nullable String audioUrl) {
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    @Nullable
    public String getAudioUrl() {
        return audioUrl;
    }
}
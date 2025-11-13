package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.interfaces.IMediaInfo;
import top.tobyprime.mcedia.provider.QualityInfo;

import java.util.List;
import java.util.Map;

public class VideoInfo implements IMediaInfo {
    private final String videoUrl;
    @Nullable
    private final String audioUrl;
    private final String title;
    private final String author;
    private final Map<String, String> headers;
    @Nullable
    private final String partName;
    private final int partNumber;
    @Nullable
    private final List<QualityInfo> availableQualities;
    @Nullable private final String currentQuality;
    private final boolean isMultiPart;
    private final long cid;

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author,
                     @Nullable Map<String, String> headers, @Nullable String partName, int partNumber,
                     @Nullable List<QualityInfo> availableQualities, @Nullable String currentQuality,
                     boolean isMultiPart, long cid) {
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.title = title != null ? title : "未知视频";
        this.author = author != null ? author : "未知作者";
        this.headers = headers;
        this.partName = partName;
        this.partNumber = partNumber;
        this.availableQualities = availableQualities;
        this.currentQuality = currentQuality;
        this.isMultiPart = isMultiPart;
        this.cid = cid;
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author,
                     @Nullable Map<String, String> headers, @Nullable String partName, int partNumber,
                     @Nullable List<QualityInfo> availableQualities, @Nullable String currentQuality) {
        this(videoUrl, audioUrl, title, author, headers, partName, partNumber, availableQualities, currentQuality, false, 0);
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author,
                     @Nullable Map<String, String> headers) {
        this(videoUrl, audioUrl, title, author, headers, null, 0, null, null, false, 0);
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl, String title, String author) {
        this(videoUrl, audioUrl, title, author, null, null, 0, null, null, false, 0);
    }

    public VideoInfo(String videoUrl, @Nullable String audioUrl) {
        this(videoUrl, audioUrl, null, null, null, null, 0, null, null, false, 0);
    }

    public VideoInfo(String videoUrl) {
        this(videoUrl, null, null, null, null, null, 0, null, null, false, 0);
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    @Nullable
    public String getAudioUrl() {
        return audioUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    @Nullable
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Nullable
    public String getPartName() {
        return partName;
    }

    public int getPartNumber() {
        return partNumber;
    }

    @Override
    public boolean isMultiPart() {
        return this.isMultiPart;
    }

    @Nullable
    public List<QualityInfo> getAvailableQualities() {
        return availableQualities;
    }

    @Nullable
    public String getCurrentQuality() {
        return currentQuality;
    }

    public long getCid() {
        return cid;
    }
}
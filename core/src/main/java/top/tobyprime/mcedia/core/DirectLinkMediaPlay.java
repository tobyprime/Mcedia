package top.tobyprime.mcedia.core;

public class DirectLinkMediaPlay extends BaseMediaPlay {
    public DirectLinkMediaPlay(String link) {
        var mediaInfo = new MediaInfo();
        mediaInfo.streamUrl = link;
        mediaInfo.platform = "直链";
        setMediaInfo(mediaInfo);
    }
}

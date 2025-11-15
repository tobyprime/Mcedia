package top.tobyprime.mcedia.interfaces;

import top.tobyprime.mcedia.video_fetcher.MediaInfo;

public interface IMediaFetcher {
    boolean isValidUrl(String url);
    MediaInfo getMedia(String url);
}

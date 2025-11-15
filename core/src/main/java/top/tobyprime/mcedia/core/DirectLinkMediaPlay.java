package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.video_fetcher.MediaInfo;

import java.util.function.Consumer;

public record DirectLinkMediaPlay(String url) implements IMediaPlay {
    @Override
    public void registerOnMediaInfoUpdatedEvent(Consumer<@Nullable MediaInfo> onUpdate) {

    }

    @Override
    public void registerOnStatusUpdatedEvent(Consumer<String> onMessage) {

    }

    @Override
    public @Nullable MediaInfo getMediaInfo() {
        var info = new MediaInfo();
        info.streamUrl = url;
        return info;
    }

    @Override
    public @Nullable String getStatus() {
        return "";
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public void close() {

    }
}

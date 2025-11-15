package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.Consumer;

public abstract class BaseMediaPlay implements IMediaPlay {
    private MediaInfo mediaInfo;
    private String status;



    private final ArrayList<Consumer<MediaInfo>> onUpdatedListeners = new ArrayList<>();
    private final ArrayList<Consumer<String>> onMessageListeners = new ArrayList<>();
    protected boolean loading;

    public void setMediaInfo(MediaInfo mediaInfo) {
        this.mediaInfo = mediaInfo;
        onUpdatedListeners.forEach(l -> l.accept(mediaInfo));
    }

    public void setStatus(String status) {
        this.status = status;
        onMessageListeners.forEach(l -> l.accept(status));
    }

    @Override
    public void registerOnMediaInfoUpdatedEvent(Consumer<@Nullable MediaInfo> onUpdate) {
        onUpdatedListeners.add(onUpdate);
    }

    @Override
    public void registerOnStatusUpdatedEvent(Consumer<@Nullable String> onMessage) {
        onMessageListeners.add(onMessage);
    }

    @Override
    public @Nullable MediaInfo getMediaInfo() {
        return mediaInfo;
    }

    @Override
    public @Nullable String getStatus() {
        return status;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public void close() {
        this.onMessageListeners.clear();
        this.onUpdatedListeners.clear();
    }
}

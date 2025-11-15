package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public record EmptyMediaPlay(String info) implements IMediaPlay {

    @Override
    public void registerOnMediaInfoUpdatedEvent(Consumer<@Nullable MediaInfo> onUpdate) {

    }

    @Override
    public void registerOnStatusUpdatedEvent(Consumer<String> onMessage) {

    }

    @Override
    public @Nullable MediaInfo getMediaInfo() {
        return null;
    }

    @Override
    public @Nullable String getStatus() {
        return info;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public void close() {

    }
}

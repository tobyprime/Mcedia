package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.function.Consumer;

public interface IMediaPlay extends Closeable {

    /**
     * 注册媒体信息更新事件，例如首次加载完成、登录、切换清晰度
     */
    void registerOnMediaInfoUpdatedEvent(Consumer<@Nullable MediaInfo> onUpdate);

    default void registerOnMediaInfoUpdatedEventAndCallOnce(Consumer<@Nullable MediaInfo> onUpdate){
        registerOnMediaInfoUpdatedEvent(onUpdate);
        onUpdate.accept(getMediaInfo());
    }
    /**
     * 注册加载状态消息，或是提示登录等消息
     */
    void registerOnStatusUpdatedEvent(Consumer<String> onMessage);
    default void registerOnStatusUpdatedEventAndCallOnce(Consumer<String> onMessage){
        registerOnStatusUpdatedEvent(onMessage);
        onMessage.accept(getStatus());
    }

    /**
     * 获取当前媒体信息
     */
    @Nullable MediaInfo getMediaInfo();

    @Nullable String getStatus();

    boolean isLoading();

    @Override
    void close() ;
}

package top.tobyprime.mcedia.interfaces;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Predicate;

public interface IMediaDecoder extends Closeable {

    boolean isLiveStream();


    IVideoData peekVideo();

    IVideoData pollVideo();

    @Nullable IVideoData pollVideoIf(Predicate<IVideoData> condition);

    LinkedBlockingDeque<? extends IAudioData> getAudioQueue();

    /**
     * 进入低开销模式
     * @param lowOverhead
     */
    void setLowOverhead(boolean lowOverhead);

    /**
     * 解码结束
     */
    boolean isEnded();

    /**
     * 视频长度
     */
    long getLength();

    /**
     * 当前解码时长
     */
    long getDuration();

    /**
     * 宽度
     */
    int getWidth();

    /**
     * 高度
     */
    int getHeight();

    /**
     * 音频采样率
     */
    int getSampleRate();

    /**
     * 音频通道
     */
    int getChannels();

    /**
     * 设置解码时间到
     * @param timestamp 单位 us
     */
    void seek(long timestamp);

    @Override
    void close();
}

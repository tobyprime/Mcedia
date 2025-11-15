package top.tobyprime.mcedia.interfaces;

import top.tobyprime.mcedia.decoders.ffmpeg.FfmpegAudioData;
import top.tobyprime.mcedia.decoders.ffmpeg.FfmpegVideoData;

import java.io.Closeable;
import java.util.concurrent.LinkedBlockingDeque;

public interface IMediaDecoder extends Closeable {

    boolean isLiveStream();

    LinkedBlockingDeque<? extends IVideoData> getVideoQueue();

    LinkedBlockingDeque<? extends IAudioData> getAudioQueue();

    boolean isEnded();

    boolean isDecodeEnded();

    long getDuration();

    int getWidth();

    int getHeight();

    int getSampleRate();

    int getChannels();

    void seek(long timestamp);

    @Override
    void close();
}

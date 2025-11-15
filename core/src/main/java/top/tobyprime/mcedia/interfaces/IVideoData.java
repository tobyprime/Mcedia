package top.tobyprime.mcedia.interfaces;

import top.tobyprime.mcedia.decoders.VideoFrame;

import java.io.Closeable;

public interface IVideoData extends Closeable {
    public long getTimestamp();
    VideoFrame toFrame();
    @Override
    public void close();
}

package top.tobyprime.mcedia.decoders.ffmpeg;

import org.bytedeco.javacv.Frame;
import top.tobyprime.mcedia.decoders.VideoFrame;
import top.tobyprime.mcedia.interfaces.IVideoData;

public class FfmpegVideoData  implements IVideoData {
    public Frame ffmpegFrame;

    public FfmpegVideoData(Frame ffmpegFrame) {
        this.ffmpegFrame = ffmpegFrame.clone();
    }

    @Override
    public long getTimestamp() {
        return ffmpegFrame.timestamp;
    }

    @Override
    public VideoFrame toFrame() {
        return FfmpegVideoDataConverter.convertToVideoFrame(ffmpegFrame);
    }

    @Override
    public void close()  {
        ffmpegFrame.close();
    }
}

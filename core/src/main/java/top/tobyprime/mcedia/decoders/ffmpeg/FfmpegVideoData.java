package top.tobyprime.mcedia.decoders.ffmpeg;

import org.bytedeco.javacv.Frame;
import org.opencv.video.Video;
import top.tobyprime.mcedia.decoders.VideoFrame;
import top.tobyprime.mcedia.interfaces.IVideoData;

public class FfmpegVideoData implements IVideoData {
    private final long timestamp;
    public Frame clonedFfmpegFrame ;
    public VideoFrame frame;

    public FfmpegVideoData(Frame ffmpegFrame) {
        clonedFfmpegFrame = ffmpegFrame.clone();
        this.frame = FfmpegVideoDataConverter.convertToVideoFrame(clonedFfmpegFrame);
        this.timestamp = ffmpegFrame.timestamp;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public VideoFrame toFrame() {
        return frame;
    }

    @Override
    public void close() {
        frame.close();
        clonedFfmpegFrame.close();
    }
}

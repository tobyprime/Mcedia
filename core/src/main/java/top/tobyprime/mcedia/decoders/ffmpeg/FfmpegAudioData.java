package top.tobyprime.mcedia.decoders.ffmpeg;

import org.bytedeco.javacv.Frame;
import top.tobyprime.mcedia.decoders.AudioBufferData;
import top.tobyprime.mcedia.interfaces.IAudioData;

public class FfmpegAudioData implements IAudioData {
    public Frame ffmpegFrame;

    public FfmpegAudioData(Frame ffmpegFrame) {
        this.ffmpegFrame = ffmpegFrame.clone();
    }

    @Override
    public AudioBufferData getMergedAudioData() {
        return FfmpegAudioBufferDataConverter.AsAudioData(ffmpegFrame, -1);
    }

    @Override
    public long getTimestamp() {
        return ffmpegFrame.timestamp;
    }

    @Override
    public int getChannels(){
        return ffmpegFrame.audioChannels;
    }

    @Override
    public AudioBufferData getChannelAudioData(int channel) {
        return FfmpegAudioBufferDataConverter.AsAudioData(ffmpegFrame, channel);
    }

    @Override
    public void close()  {

    }
}

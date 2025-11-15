package top.tobyprime.mcedia.interfaces;

import top.tobyprime.mcedia.decoders.AudioBufferData;

import java.io.Closeable;

public interface IAudioData extends Closeable {
    AudioBufferData getMergedAudioData();
    long getTimestamp();
    int getChannels();

    AudioBufferData getChannelAudioData(int channel);
    @Override
    public void close();
}

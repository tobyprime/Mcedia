package top.tobyprime.mcedia.decoders;

import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.nio.Buffer;

public class AudioBufferData implements Closeable {
    public final Buffer pcm;  // PCM 数据，16-bit signed, little-endian
    public final int sampleRate;
    public final int channels;
    private boolean released;

    public AudioBufferData(Buffer pcm, int sampleRate, int channels) {
        this.pcm = pcm;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.released = false;
    }

    public void close() {
        if (!released) {
            MemoryUtil.memFree(pcm);
            released = true;
        }
    }
}
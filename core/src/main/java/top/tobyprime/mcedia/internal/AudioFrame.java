package top.tobyprime.mcedia.internal;

import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.nio.ByteBuffer;

public class AudioFrame implements Closeable {
    public final ByteBuffer pcm;  // PCM 数据，16-bit signed, little-endian
    public final int sampleRate;
    public final int channels;
    public final long ptsUs;
    private boolean released;

    public AudioFrame(ByteBuffer pcm, int sampleRate, int channels, long ptsUs) {
        this.pcm = pcm;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.ptsUs = ptsUs;
        this.released = false;
    }

    public void close() {
        if (!released) {
            MemoryUtil.memFree(pcm);
            released = true;
        }
    }
}
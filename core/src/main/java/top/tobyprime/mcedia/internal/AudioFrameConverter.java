package top.tobyprime.mcedia.internal;

import org.bytedeco.javacv.Frame;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;

public class AudioFrameConverter {
    public AudioFrame AsAudioFrame(TimedFrame frame) {
        Frame raw = frame.getRawFrame();
        if (raw == null || raw.samples == null || raw.samples.length == 0) return null;
        int channels = raw.audioChannels > 0 ? raw.audioChannels : raw.samples.length;
        int sampleRate = raw.sampleRate;
        long ptsUs = frame.getPtsUs();
        int sampleCount = getSampleCount(raw.samples);

        ByteBuffer pcm = convertSamplesToPCM(raw.samples, channels, sampleCount);
        if (pcm == null) return null;

        return new AudioFrame(pcm, sampleRate, channels, ptsUs);
    }

    private int getSampleCount(Buffer[] samples) {
        if (samples[0] instanceof ShortBuffer) {
            return samples[0].remaining();
        } else if (samples[0] instanceof FloatBuffer) {
            return samples[0].remaining();
        } else {
            return 0;
        }
    }

    private ByteBuffer convertSamplesToPCM(Buffer[] samples, int channels, int sampleCount) {
        if (samples[0] instanceof ShortBuffer) {
            return interleaveShortSamples(samples, channels, sampleCount);
        } else if (samples[0] instanceof FloatBuffer) {
            return interleaveFloatSamples(samples, channels, sampleCount);
        } else {
            return null;
        }
    }

    private ByteBuffer interleaveShortSamples(Buffer[] samples, int channels, int sampleCount) {
        ByteBuffer pcm = ByteBuffer.allocateDirect(sampleCount * channels * 2); // 2 bytes per sample
        for (int i = 0; i < sampleCount; i++) {
            for (int ch = 0; ch < channels; ch++) {
                short val = ((ShortBuffer) samples[ch]).get(i);
                pcm.put((byte) (val & 0xFF));
                pcm.put((byte) ((val >> 8) & 0xFF));
            }
        }
        pcm.flip();
        return pcm;
    }

    private ByteBuffer interleaveFloatSamples(Buffer[] samples, int channels, int sampleCount) {
        ByteBuffer pcm = ByteBuffer.allocateDirect(sampleCount * channels * 2); // 2 bytes per sample
        for (int i = 0; i < sampleCount; i++) {
            for (int ch = 0; ch < channels; ch++) {
                float fval = ((FloatBuffer) samples[ch]).get(i);
                short sval = floatToPCM16(fval);
                pcm.put((byte) (sval & 0xFF));
                pcm.put((byte) ((sval >> 8) & 0xFF));
            }
        }
        pcm.flip();
        return pcm;
    }

    private short floatToPCM16(float f) {
        f = Math.max(-1.0f, Math.min(1.0f, f));
        return (short) (f * 32767);
    }
}

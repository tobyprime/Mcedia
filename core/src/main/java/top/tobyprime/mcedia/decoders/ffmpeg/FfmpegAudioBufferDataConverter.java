package top.tobyprime.mcedia.decoders.ffmpeg;

import org.bytedeco.javacv.Frame;
import org.lwjgl.system.MemoryUtil;
import top.tobyprime.mcedia.BufferHelper;
import top.tobyprime.mcedia.decoders.AudioBufferData;

import java.nio.*;

public class FfmpegAudioBufferDataConverter {

    public static AudioBufferData AsMixedAudioData(Frame frame) {
        if (frame.samples == null || frame.samples.length == 0) return null;
        if (frame.samples.length == 1)
            return AsAudioData(frame, 0);

        return new AudioBufferData(mergeToMono(frame.samples), frame.sampleRate, 1);
    }

    public static AudioBufferData AsAudioData(Frame frame, int channel) {
        if (channel < 0)
            return AsMixedAudioData(frame);
        if (frame.samples == null || channel >= frame.samples.length) return null;
        return new AudioBufferData(BufferHelper.cloneBuffer(frame.samples[channel]), frame.sampleRate, 1);
    }


    public static Buffer mergeToMono(Buffer[] channelsData) {
        System.out.println("mergeToMono");
        if (channelsData == null || channelsData.length == 0) {
            throw new IllegalArgumentException("No input channels");
        }

        Buffer first = channelsData[0];
        int channels = channelsData.length;

        return switch (first) {
            case ByteBuffer byteBuffer -> mergeByte((ByteBuffer[]) channelsData, channels);
            case ShortBuffer shortBuffer -> mergeShort((ShortBuffer[]) channelsData, channels);
            case FloatBuffer floatBuffer -> mergeFloat((FloatBuffer[]) channelsData, channels);
            case DoubleBuffer doubleBuffer -> mergeDouble((DoubleBuffer[]) channelsData, channels);
            default -> throw new IllegalArgumentException("Unsupported Buffer type: " + first.getClass());
        };
    }

    private static ByteBuffer mergeByte(ByteBuffer[] channels, int count) {
        int frames = channels[0].remaining();
        ByteBuffer dst = MemoryUtil.memAlloc(frames);

        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int ch = 0; ch < count; ch++) {
                sum += channels[ch].get(i);
            }
            byte mixed = (byte) (sum / count);
            dst.put(mixed);
        }
        dst.flip();
        return dst;
    }

    private static ShortBuffer mergeShort(ShortBuffer[] channels, int count) {
        int frames = channels[0].remaining();
        ShortBuffer dst = MemoryUtil.memAllocShort(frames);

        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int ch = 0; ch < count; ch++) {
                sum += channels[ch].get(i);
            }
            short mixed = (short) (sum / count);
            dst.put(mixed);
        }
        dst.flip();
        return dst;
    }

    private static FloatBuffer mergeFloat(FloatBuffer[] channels, int count) {
        int frames = channels[0].remaining();
        FloatBuffer dst = MemoryUtil.memAllocFloat(frames);

        for (int i = 0; i < frames; i++) {
            float sum = 0;
            for (int ch = 0; ch < count; ch++) {
                sum += channels[ch].get(i);
            }
            float mixed = sum / count;
            dst.put(mixed);
        }
        dst.flip();
        return dst;
    }

    private static DoubleBuffer mergeDouble(DoubleBuffer[] channels, int count) {
        int frames = channels[0].remaining();
        DoubleBuffer dst = MemoryUtil.memAllocDouble(frames);

        for (int i = 0; i < frames; i++) {
            double sum = 0;
            for (int ch = 0; ch < count; ch++) {
                sum += channels[ch].get(i);
            }
            double mixed = sum / count;
            dst.put(mixed);
        }
        dst.flip();
        return dst;
    }

}

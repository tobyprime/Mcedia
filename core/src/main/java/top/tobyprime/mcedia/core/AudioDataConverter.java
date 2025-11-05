package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.Frame;
import org.lwjgl.system.MemoryUtil;

import java.nio.Buffer;
import java.nio.ShortBuffer;

/**
 * 一个工具类，负责将 JavaCV 的 Frame 对象转换为 Mod 音频系统所需的 AudioBufferData 格式。
 */
public class AudioDataConverter {

    /**
     * 将音频 Frame 转换为 AudioBufferData。
     * 这个方法会为 PCM 数据分配新的堆外内存，AudioBufferData 的 close() 方法会负责释放它。
     *
     * @param frame      包含音频数据的 Frame 对象。
     * @param bufferSize (该参数已弃用，保留以兼容旧的调用签名)
     * @return 转换后的 AudioBufferData 对象，如果 frame 无效则返回 null。
     */
    public static AudioBufferData AsAudioData(Frame frame, int bufferSize) {
        // 检查 frame 和它的音频样本是否有效
        if (frame == null || frame.samples == null || frame.samples[0] == null) {
            return null;
        }

        // 从 Frame 中获取原始的音频数据缓冲区 (通常是 ShortBuffer)
        Buffer originalBuffer = frame.samples[0];

        // 我们必须创建一个此缓冲区的独立副本。
        // 这是因为这个 buffer 将被异步地上传到 OpenAL，而原始的 frame 对象
        // 及其内部的 buffer 可能会在下一个解码周期中被 FFmpeg 重用或覆盖。
        // 如果不创建副本，可能会导致音频数据损坏或程序崩溃。

        // 使用 LWJGL 的 MemoryUtil 来分配堆外内存，这在与本地库（如 OpenAL）交互时是最高效的方式。
        ShortBuffer pcmCopy = MemoryUtil.memAllocShort(originalBuffer.remaining());

        // 将原始缓冲区的数据复制到我们新分配的内存区域中。
        // 使用 duplicate() 可以确保我们不会改变原始缓冲区的位置（position）等状态。
        pcmCopy.put((ShortBuffer) originalBuffer.duplicate());

        // 将新缓冲区的位置重置到开头（position = 0），使其处于“可读”状态。
        pcmCopy.flip();

        // 使用 Frame 中的元数据（采样率、通道数）和我们创建的数据副本，构建并返回 AudioBufferData 对象。
        return new AudioBufferData(pcmCopy, frame.sampleRate, frame.audioChannels);
    }
}
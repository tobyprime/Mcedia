package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.Frame;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class VideoDataConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoDataConverter.class);

    public static VideoFrame convertToVideoFrame(Frame frame) {
        if (frame == null || frame.image == null || frame.image.length == 0) {
            return null; // 安全返回，避免上层崩溃
        }

        Buffer srcBuffer = frame.image[0];
        if (srcBuffer == null || srcBuffer.remaining() == 0) {
            return null; // 如果缓冲区为空，也安全返回
        }

        int width = frame.imageWidth;
        int height = frame.imageHeight;
        int stride = frame.imageStride;
        int channels = frame.imageChannels;

        // 调用已修复的 removeStride 方法
        ByteBuffer processedBuffer = removeStride((ByteBuffer) srcBuffer, height, width, stride, channels);

        return new VideoFrame(processedBuffer, width, height, frame.timestamp);
    }

    /**
     * 从带有填充(stride)的FFmpeg缓冲区中复制像素数据到一个紧凑的、无填充的ByteBuffer。
     * [FIX] 此版本解决了越界读取(newPosition > limit)的崩溃问题。
     */
    public static ByteBuffer removeStride(ByteBuffer src, int height, int width, int stride, int channels) {
        int rowBytes = width * channels;

        // 快速路径：如果数据已经是紧凑的 (stride 等于一行的实际字节数)
        if (stride == rowBytes) {
            // 我们必须复制数据，因为 src 缓冲区会被 FFmpeg 重用
            ByteBuffer copy = MemoryUtil.memAlloc(src.remaining());
            copy.put(src);
            copy.flip(); // 切换到读取模式
            return copy;
        }

        // 慢速路径：如果存在填充（stride > rowBytes）
        ByteBuffer dst = MemoryUtil.memAlloc(rowBytes * height);

        // 创建一个共享数据的视图，但拥有独立的 position/limit 状态，这样我们就不会修改原始的 src 缓冲区
        ByteBuffer view = src.asReadOnlyBuffer();
        view.position(0); // 确保从头开始

        for (int row = 0; row < height; row++) {
            int rowStartPos = row * stride;

            // 检查边界，防止崩溃
            if (rowStartPos + rowBytes > view.capacity()) {
                LOGGER.error("读取越界！尝试从位置 {} 读取 {} 字节，但缓冲区总容量只有 {}。", rowStartPos, rowBytes, view.capacity());
                // 返回一个空的有效缓冲区，避免后续逻辑崩溃
                dst.flip();
                return dst;
            }

            // 安全地设置“读取窗口”
            view.limit(rowStartPos + rowBytes).position(rowStartPos);

            // 将这个窗口的数据块写入目标缓冲区
            dst.put(view);
        }

        dst.flip(); // 完成所有写入后，切换到读取模式
        return dst;
    }
}
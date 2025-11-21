package top.tobyprime.mcedia.decoders.ffmpeg;

import org.bytedeco.javacv.Frame;
import org.lwjgl.system.MemoryUtil;
import top.tobyprime.mcedia.decoders.VideoFrame;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class FfmpegVideoDataConverter {

    public static VideoFrame convertToVideoFrame(Frame frame) {
        if (frame == null || frame.image == null || frame.image.length == 0) {
            throw new IllegalArgumentException("Frame has no image data");
        }

        Buffer srcBuffer = frame.image[0];
        int width = frame.imageWidth;
        int height = frame.imageHeight;
        int stride = frame.imageStride;

        return removeStride((ByteBuffer) srcBuffer, height, width, stride, frame.imageChannels, frame.timestamp);
    }


    public static VideoFrame removeStride(ByteBuffer src, int height, int width, int stride, int channels, long timestamp) {
        int rowElements = width * channels;

        if (stride == width * channels) { // 紧凑
//            ByteBuffer copy = MemoryUtil.memAlloc(src.remaining());
//            copy.put(src);
            return new VideoFrame(src.slice(), width, height);
        }

        if (src.capacity() < stride * height) {
            throw new IllegalArgumentException("Source buffer too small: capacity=" + src.capacity()
                    + ", expected at least " + (stride * height));
        }

        ByteBuffer dst = MemoryUtil.memAlloc(rowElements * height);
        byte[] rowData = new byte[rowElements];
        for (int row = 0; row < height; row++) {
            int srcPos = row * stride;
            int dstPos = row * rowElements;

            src.position(srcPos);
            src.get(rowData, 0, rowElements); // 读取有效像素
            dst.position(dstPos);
            dst.put(rowData);
        }
        dst.flip();
        return new VideoFrame(dst, width, height);
    }
}

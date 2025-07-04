package top.tobyprime.mcedia.internal;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VideoFrameConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoFrameConverter.class);
    
    public VideoFrame AsVideoFrame(TimedFrame frame) {
        Frame raw = frame.getRawFrame();
        if (raw == null || raw.image == null || raw.image.length == 0) return null;
        int width = raw.imageWidth;
        int height = raw.imageHeight;
        long ptsUs = frame.getPtsUs();
        ByteBuffer buffer = null;
        // 直接支持的格式（BYTE, 3/4通道）
        if (raw.image[0] instanceof ByteBuffer src && (raw.imageChannels == 4 || raw.imageChannels == 3)) {
            // OpenGL 需要 RGBA 格式，若为RGB需转换
            if (raw.imageChannels == 4) {
                // 处理4通道RGBA，检查是否有padding
                int rowBytes = width * 4;
                int stride = raw.imageStride;
                if (stride == rowBytes) {
                    // 没有padding，直接上传
                    buffer = MemoryUtil.memAlloc(src.remaining());
                    src.rewind();
                    buffer.put(src);
                    buffer.flip();
                } else {
                    // 有padding，逐行拷贝
                    buffer = MemoryUtil.memAlloc(rowBytes * height);
                    src.rewind();
                    byte[] row = new byte[rowBytes];
                    for (int y = 0; y < height; y++) {
                        int srcPos = y * stride;
                        src.position(srcPos);
                        src.get(row, 0, rowBytes);
                        buffer.put(row);
                    }
                    buffer.flip();
                }
            } else {
                // RGB -> RGBA，处理padding
                int rowBytes = width * 3;
                int stride = raw.imageStride;
                int pixelCount = width * height;
                buffer = MemoryUtil.memAlloc(pixelCount * 4);
                src.rewind();
                if (stride == rowBytes) {
                    for (int i = 0; i < pixelCount; i++) {
                        byte r = src.get();
                        byte g = src.get();
                        byte b = src.get();
                        buffer.put(r).put(g).put(b).put((byte)255);
                    }
                } else {
                    byte[] row = new byte[rowBytes];
                    for (int y = 0; y < height; y++) {
                        int srcPos = y * stride;
                        src.position(srcPos);
                        src.get(row, 0, rowBytes);
                        for (int x = 0; x < width; x++) {
                            int idx = x * 3;
                            byte r = row[idx];
                            byte g = row[idx + 1];
                            byte b = row[idx + 2];
                            buffer.put(r).put(g).put(b).put((byte)255);
                        }
                    }
                }
                buffer.flip();
            }
        } else {
            BufferedImage img;
            try (Java2DFrameConverter converter = new Java2DFrameConverter()) {
                img = converter.convert(raw);
            }
            if (img == null) return null;

            // 转换为RGBA格式
            if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                converted.getGraphics().drawImage(img, 0, 0, null);
                img = converted;
            }
            
            // 将ARGB转换为RGBA格式
            int[] pixels = new int[width * height];
            img.getRGB(0, 0, width, height, pixels, 0, width);
            
            buffer = MemoryUtil.memAlloc(width * height * 4);
            for (int pixel : pixels) {
                byte a = (byte) ((pixel >> 24) & 0xFF);
                byte r = (byte) ((pixel >> 16) & 0xFF);
                byte g = (byte) ((pixel >> 8) & 0xFF);
                byte b = (byte) (pixel & 0xFF);
                buffer.put(r).put(g).put(b).put(a);
            }
            buffer.flip();
        }
        return new VideoFrame(buffer, width, height, ptsUs);
    }
}

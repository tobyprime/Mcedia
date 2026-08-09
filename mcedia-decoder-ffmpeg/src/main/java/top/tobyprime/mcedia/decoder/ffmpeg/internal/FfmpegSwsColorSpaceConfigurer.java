package top.tobyprime.mcedia.decoder.ffmpeg.internal;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;

/**
 * 修正 FFmpeg swscale 的色彩空间与全/有限范围配置，解决视频画面发灰、亮度不准的问题。
 * <p>
 * 在线视频通常是 BT.601/BT.709 的 YUV 有限范围（TV range），而 swscale 默认按
 * BT.601/全范围假设转换，导致输出 RGB 亮度偏灰。本类按每帧 AVFrame 携带的
 * colorspace/color_range 信息，用 {@code sws_setColorspaceDetails} 校正转换上下文。
 */
public class FfmpegSwsColorSpaceConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegSwsColorSpaceConfigurer.class);
    private static final int FULL_RANGE = 1;
    private static final int LIMITED_RANGE = 0;
    private static final int FIXED_POINT_ONE = 65536;

    private static final Field IMAGE_CONVERT_CONTEXT_FIELD = findImageConvertContextField();

    private boolean warnedUnavailable;
    private boolean warnedFailure;
    private int lastSrcRange = Integer.MIN_VALUE;
    private int lastColorSpace = Integer.MIN_VALUE;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public void configure(FFmpegFrameGrabber grabber, Frame frame) {
        if (frame.image == null || !(frame.opaque instanceof AVFrame avFrame)) {
            return;
        }
        SwsContext context = imageConvertContext(grabber);
        if (context == null || context.isNull()) {
            return;
        }
        int width = Math.max(frame.imageWidth, avFrame.width());
        int height = Math.max(frame.imageHeight, avFrame.height());
        int srcRange = sourceRange(avFrame);
        int colorSpace = swsColorSpace(avFrame.colorspace(), width, height);
        if (srcRange == lastSrcRange && colorSpace == lastColorSpace && width == lastWidth && height == lastHeight) {
            return;
        }

        IntPointer coefficients = swscale.sws_getCoefficients(colorSpace);
        if (coefficients == null || coefficients.isNull()) {
            return;
        }
        int result = swscale.sws_setColorspaceDetails(context, coefficients, srcRange, coefficients, FULL_RANGE, 0, FIXED_POINT_ONE, FIXED_POINT_ONE);
        if (result < 0) {
            if (!warnedFailure) {
                warnedFailure = true;
                LOGGER.warn("Failed to configure FFmpeg swscale color space details, result={}", result);
            }
            return;
        }
        lastSrcRange = srcRange;
        lastColorSpace = colorSpace;
        lastWidth = width;
        lastHeight = height;
    }

    static int sourceRange(AVFrame frame) {
        // AVCOL_RANGE_JPEG=2 为全范围；colorspace==0 为 RGB，同样视为全范围
        if (frame.color_range() == 2 || frame.colorspace() == 0) {
            return FULL_RANGE;
        }
        return LIMITED_RANGE;
    }

    static int swsColorSpace(int avColorSpace, int width, int height) {
        return switch (avColorSpace) {
            case 1 -> 1; // AVCOL_SPC_BT709
            case 4 -> 4; // AVCOL_SPC_FCC
            case 5 -> 5; // AVCOL_SPC_BT470BG
            case 6 -> 5; // AVCOL_SPC_SMPTE170M → BT.470BG
            case 7 -> 7; // AVCOL_SPC_SMPTE240M
            case 9, 10 -> 9; // AVCOL_SPC_BT2020 / BT2020_CL
            case 0 -> 5; // AVCOL_SPC_RGB → BT.470BG（与全范围搭配）
            default -> isHighDefinition(width, height) ? 1 : 5; // 未指定：高清按 BT.709，标清按 BT.470BG
        };
    }

    private static boolean isHighDefinition(int width, int height) {
        return width >= 1280 || height > 576;
    }

    @Nullable
    private static Field findImageConvertContextField() {
        try {
            Field field = FFmpegFrameGrabber.class.getDeclaredField("img_convert_ctx");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | SecurityException | InaccessibleObjectException e) {
            LOGGER.warn("Unable to access FFmpegFrameGrabber image conversion context; color range correction is disabled", e);
            return null;
        }
    }

    @Nullable
    private SwsContext imageConvertContext(FFmpegFrameGrabber grabber) {
        if (IMAGE_CONVERT_CONTEXT_FIELD == null) {
            if (!warnedUnavailable) {
                warnedUnavailable = true;
                LOGGER.warn("FFmpeg swscale color range correction is unavailable");
            }
            return null;
        }
        try {
            Object value = IMAGE_CONVERT_CONTEXT_FIELD.get(grabber);
            if (value instanceof SwsContext context) {
                return context;
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            if (!warnedUnavailable) {
                warnedUnavailable = true;
                LOGGER.warn("Unable to read FFmpegFrameGrabber image conversion context; color range correction is disabled", e);
            }
        }
        return null;
    }
}

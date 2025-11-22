package top.tobyprime.mcedia.decoders.ffmpeg;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.util.concurrent.ConcurrentHashMap;

public class FfmpegProcessImageFlags {
    public static ConcurrentHashMap<FFmpegFrameGrabber, Boolean> FLAGS = new ConcurrentHashMap<>();

    public static void remove(FFmpegFrameGrabber grabber) {
        FLAGS.remove(grabber);
    }

    public static void setProcessImage(FFmpegFrameGrabber object, boolean value) {
        FLAGS.put(object, value);
    }


    public static boolean isEnableProcessImage(FFmpegFrameGrabber object){
        return FLAGS.getOrDefault(object, true);
    }
}

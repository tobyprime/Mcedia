package top.tobyprime.mcedia;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.interfaces.IAudioSource;

import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public class Configs {
    private static final Logger log = LoggerFactory.getLogger(Configs.class);
    public static boolean PHYSICS = true;
    public static boolean SHOW_LOAD_INFO = true;
    public static int MAX_PLAYER_COUNT = 5;
    public static int MAX_NON_LOW_OVERHEAD_PLAYER_COUNT = 1;
    public static List<String> ARMOR_STAND_PLAYER_NAME_PATTERNS = List.of("mcedia", "mcdia");
    // 3: 允许 8k
    // 2: 允许 4k
    // 1: 4k 以下最高
    // 0: 最低画质
    public static int QUALITY = 2;

    // 音量
    public static Float VOLUME_FACTOR = 1f;

    // 音频处理器，用于物理声效
    @Nullable
    public static Consumer<IAudioSource> AUDIO_SOURCE_CONSUMER = null;

    // 弹幕设置
    public static boolean DANMAKU_VISIBLE = false;
    public static Float DANMAKU_DURATION = 4.f;
    public static int DANMAKU_TRACKS = 12;
    public static Float DANMAKU_OPACITY = 0.5f;

    // 缓冲与解码配置
    public static int DECODER_MAX_AUDIO_FRAMES = 512;
    public static int DECODER_MAX_VIDEO_FRAMES = 120;
    public static int DECODER_LOW_OVERHEAD_VIDEO_FRAMES = 20;

    public static boolean ALLOW_DIRECT_LINK = false;
    public static boolean ALLOW_YHDM = false;

    public static void fromProperties(Properties props) {
        try {
            FFmpegFrameGrabber.tryLoad();
        } catch (FFmpegFrameGrabber.Exception e) {

        }

        Configs.MAX_PLAYER_COUNT = Integer.parseInt(props.getProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.MAX_PLAYER_COUNT)));
        Configs.MAX_NON_LOW_OVERHEAD_PLAYER_COUNT = Integer.parseInt(props.getProperty("MAX_NON_LOW_OVERHEAD_PLAYER_COUNT", String.valueOf(Configs.MAX_NON_LOW_OVERHEAD_PLAYER_COUNT)));
        Configs.SHOW_LOAD_INFO = Boolean.parseBoolean(props.getProperty("SHOW_LOAD_INFO", String.valueOf(Configs.SHOW_LOAD_INFO)));
        Configs.VOLUME_FACTOR = Float.parseFloat(props.getProperty("VOLUME_FACTOR", String.valueOf(Configs.VOLUME_FACTOR)));
        Configs.ARMOR_STAND_PLAYER_NAME_PATTERNS = List.of(props.getProperty("ARMOR_STAND_PLAYER_NAME_PATTERNS", String.join(";", ARMOR_STAND_PLAYER_NAME_PATTERNS)).split(";"));
        Configs.QUALITY = Integer.parseInt(props.getProperty("QUALITY", String.valueOf(Configs.QUALITY)));
        Configs.PHYSICS = Boolean.parseBoolean(props.getProperty("PHYSICS", String.valueOf(Configs.PHYSICS)));

        Configs.DANMAKU_VISIBLE = Boolean.parseBoolean(props.getProperty("DANMAKU_VISIBLE", String.valueOf(Configs.DANMAKU_VISIBLE)));
        Configs.DANMAKU_DURATION = Float.parseFloat(props.getProperty("DANMAKU_DURATION", String.valueOf(Configs.DANMAKU_DURATION)));
        Configs.DANMAKU_TRACKS = Integer.parseInt(props.getProperty("DANMAKU_TRACKS", String.valueOf(Configs.DANMAKU_TRACKS)));
        Configs.DANMAKU_OPACITY = Float.parseFloat(props.getProperty("DANMAKU_OPACITY", String.valueOf(Configs.DANMAKU_OPACITY)));

        Configs.ALLOW_DIRECT_LINK = Boolean.parseBoolean(props.getProperty("ALLOW_DIRECT_LINK", String.valueOf(Configs.ALLOW_DIRECT_LINK)));
        Configs.ALLOW_YHDM = Boolean.parseBoolean(props.getProperty("ALLOW_YHDM", String.valueOf(Configs.ALLOW_YHDM)));
    }

    public static void writeToProperties(Properties props) {
        props.setProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.MAX_PLAYER_COUNT));
        props.setProperty("MAX_NON_LOW_OVERHEAD_PLAYER_COUNT", String.valueOf(Configs.MAX_NON_LOW_OVERHEAD_PLAYER_COUNT));
        props.setProperty("SHOW_LOAD_INFO", String.valueOf(Configs.SHOW_LOAD_INFO));
        props.setProperty("VOLUME_FACTOR", String.valueOf(Configs.VOLUME_FACTOR));
        props.setProperty("ARMOR_STAND_PLAYER_NAME_PATTERNS", String.join(";", Configs.ARMOR_STAND_PLAYER_NAME_PATTERNS));
        props.setProperty("QUALITY", String.valueOf(Configs.QUALITY));
        props.setProperty("PHYSICS", String.valueOf(Configs.PHYSICS));

        props.setProperty("DANMAKU_VISIBLE", String.valueOf(Configs.DANMAKU_VISIBLE));
        props.setProperty("DANMAKU_DURATION", String.valueOf(Configs.DANMAKU_DURATION));
        props.setProperty("DANMAKU_TRACKS", String.valueOf(Configs.DANMAKU_TRACKS));
        props.setProperty("DANMAKU_OPACITY", String.valueOf(Configs.DANMAKU_OPACITY));

        props.setProperty("ALLOW_DIRECT_LINK", String.valueOf(Configs.ALLOW_DIRECT_LINK));
        props.setProperty("ALLOW_YHDM", String.valueOf(Configs.ALLOW_YHDM));
    }
}

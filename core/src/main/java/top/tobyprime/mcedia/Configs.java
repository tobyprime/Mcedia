package top.tobyprime.mcedia;

import java.util.Properties;

public class Configs {
    public static int MAX_PLAYER_COUNT = 5;
    public static int MAX_NON_LOW_OVERHEAD_PLAYER_COUNT = 1;

    // 3: 允许 8k
    // 2: 允许 4k
    // 1: 4k 以下最高
    // 0: 最低画质
    public static int QUALITY = 2;


    // 弹幕设置
    public static boolean DANMAKU_VISIBLE = false;
    public static Float DANMAKU_DURATION = 4.f;
    public static int DANMAKU_TRACKS = 12;
    public static Float DANMAKU_OPACITY = 0.5f;


    // 缓冲与解码配置
    public static int DECODER_MAX_AUDIO_FRAMES = 512;
    public static int DECODER_MAX_VIDEO_FRAMES = 120;
    public static int DECODER_LOW_OVERHEAD_VIDEO_FRAMES = 2;

    public static boolean ALLOW_DIRECT_LINK = false;
    public static boolean ALLOW_YHDM = false;

    public static void fromProperties(Properties props) {
        Configs.MAX_PLAYER_COUNT = Integer.parseInt(props.getProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.MAX_PLAYER_COUNT)));
        Configs.MAX_NON_LOW_OVERHEAD_PLAYER_COUNT = Integer.parseInt(props.getProperty("MAX_NON_LOW_OVERHEAD_PLAYER_COUNT", String.valueOf(Configs.MAX_NON_LOW_OVERHEAD_PLAYER_COUNT)));

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

        props.setProperty("DANMAKU_VISIBLE", String.valueOf(Configs.DANMAKU_VISIBLE));
        props.setProperty("DANMAKU_DURATION", String.valueOf(Configs.DANMAKU_DURATION));
        props.setProperty("DANMAKU_TRACKS", String.valueOf(Configs.DANMAKU_TRACKS));
        props.setProperty("DANMAKU_OPACITY", String.valueOf(Configs.DANMAKU_OPACITY));

        props.setProperty("ALLOW_DIRECT_LINK", String.valueOf(Configs.ALLOW_DIRECT_LINK));
        props.setProperty("ALLOW_YHDM", String.valueOf(Configs.ALLOW_YHDM));
    }
}

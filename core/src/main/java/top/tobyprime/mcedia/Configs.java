package top.tobyprime.mcedia;

import java.util.Properties;

public class Configs {
    public static int MAX_PLAYER_COUNT = 5;
    public static boolean ALLOW_DIRECT_LINK = false;
    // 弹幕设置
    public static Float DANMAKU_DURATION = 4.f;
    public static int DANMAKU_TRACKS = 12;
    public static Float DANMAKU_OPACITY = 0.5f;


    // 缓冲与解码配置
    public static int DECODER_MAX_AUDIO_FRAMES = 1024;
    public static int DECODER_MAX_VIDEO_FRAMES = 120;


    public static void fromProperties(Properties props){
        Configs.MAX_PLAYER_COUNT = Integer.parseInt(props.getProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.MAX_PLAYER_COUNT)));
        Configs.ALLOW_DIRECT_LINK = Boolean.parseBoolean(props.getProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.ALLOW_DIRECT_LINK)));
        Configs.DANMAKU_DURATION = Float.parseFloat(props.getProperty("DANMAKU_DURATION",String.valueOf(Configs.DANMAKU_DURATION)));
        Configs.DANMAKU_TRACKS = Integer.parseInt(props.getProperty("DANMAKU_TRACKS", String.valueOf(Configs.DANMAKU_TRACKS)));
        Configs.DANMAKU_OPACITY = Float.parseFloat(props.getProperty("DANMAKU_OPACITY",  String.valueOf(Configs.DANMAKU_OPACITY)));
    }

    public static void writeToProperties(Properties props){
        props.setProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.MAX_PLAYER_COUNT));
        props.setProperty("ALLOW_DIRECT_LINK", String.valueOf(Configs.ALLOW_DIRECT_LINK));
        props.setProperty("DANMAKU_DURATION", String.valueOf(Configs.DANMAKU_DURATION));
        props.setProperty("DANMAKU_TRACKS", String.valueOf(Configs.DANMAKU_TRACKS));
        props.setProperty("DANMAKU_OPACITY", String.valueOf(Configs.DANMAKU_OPACITY));
    }
}

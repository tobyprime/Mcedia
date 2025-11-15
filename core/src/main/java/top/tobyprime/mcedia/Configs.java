package top.tobyprime.mcedia;

import java.util.Properties;

public class Configs {
    public static int MAX_PLAYER_COUNT = 5;
    public static boolean ALLOW_DIRECT_LINK = false;


    // 缓冲与解码配置
    public static int DECODER_MAX_AUDIO_FRAMES = 1024;
    public static int DECODER_MAX_VIDEO_FRAMES = 120;

    public static void fromProperties(Properties props){
        Configs.MAX_PLAYER_COUNT = Integer.parseInt(props.getProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.MAX_PLAYER_COUNT)));
        Configs.ALLOW_DIRECT_LINK = Boolean.parseBoolean(props.getProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.ALLOW_DIRECT_LINK)));
    }

    public static void writeToProperties(Properties props){
        props.setProperty("MAX_PLAYER_COUNT", String.valueOf(Configs.MAX_PLAYER_COUNT));
        props.setProperty("ALLOW_DIRECT_LINK", String.valueOf(Configs.ALLOW_DIRECT_LINK));
    }
}

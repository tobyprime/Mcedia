package top.tobyprime.mcedia.bilibili;

import java.util.Properties;

public class BilibiliConfigs {
    private static String COOKIES;

    public static String getCookie() {
        return COOKIES;
    }
    public static void saveCookies(String cookies) {
        COOKIES = cookies;
    }

    public static void fromProperties(Properties props){
        COOKIES = props.getProperty("BILIBILI_COOKIES", COOKIES);
    }

    public static void writeToProperties(Properties props){
        props.setProperty("BILIBILI_COOKIES",COOKIES);
    }
}

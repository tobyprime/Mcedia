package top.tobyprime.mcedia_platforms.auth;

import org.jetbrains.annotations.Nullable;

import java.util.Properties;

public final class NeteaseCookie {
    @Nullable
    private static String COOKIES;

    private NeteaseCookie() {
    }

    public static @Nullable String getCookie() {
        return COOKIES;
    }

    public static void saveCookies(String cookies) {
        COOKIES = cookies;
    }

    public static void fromProperties(Properties props) {
        COOKIES = props.getProperty("NETEASE_COOKIES", COOKIES);
    }

    public static void writeToProperties(Properties props) {
        if (COOKIES != null) {
            props.setProperty("NETEASE_COOKIES", COOKIES);
        }
    }
}

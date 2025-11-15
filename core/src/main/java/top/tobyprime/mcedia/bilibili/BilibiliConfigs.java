package top.tobyprime.mcedia.bilibili;

public class BilibiliConfigs {
    private static String Cookies;

    public static String getCookie() {
        return Cookies;
    }
    public static void saveCookies(String cookies) {
        Cookies = cookies;
    }
}

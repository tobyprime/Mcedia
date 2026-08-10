package top.tobyprime.mcedia_platforms.auth;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;

public final class NeteaseCookie {
    /** 网易云登录态关键 cookie：登录成功后由服务端下发，缺失即视为未登录。 */
    private static final String MUSIC_U = "MUSIC_U";
    /** weapi 请求体所需的防跨站 token，来自 cookie 中的 __csrf 字段。 */
    private static final String CSRF = "__csrf";
    private static final String COOKIE_KEY = "NETEASE_COOKIES";
    private static final Set<String> COOKIE_ATTRIBUTES = Set.of(
            "domain", "path", "expires", "max-age", "secure", "httponly", "samesite", "priority", "partitioned");
    @Nullable
    private static String COOKIES;

    private NeteaseCookie() {
    }

    public static @Nullable String getCookie() {
        return COOKIES;
    }

    public static void saveCookies(String cookies) {
        COOKIES = normalizeForStorage(cookies);
    }

    /** 是否已持有网易云登录态 cookie（含 MUSIC_U）。 */
    public static boolean isLoggedIn() {
        return COOKIES != null
                && COOKIES.contains(MUSIC_U + "=")
                && !COOKIES.endsWith(MUSIC_U + "=");
    }

    /** 提取 weapi 请求所需的 csrf_token（cookie 中 __csrf 字段），缺失时返回空串。 */
    public static String csrfToken() {
        if (COOKIES == null || COOKIES.isBlank()) {
            return "";
        }
        String search = CSRF + "=";
        int index = COOKIES.indexOf(search);
        if (index < 0) {
            return "";
        }
        int start = index + search.length();
        int end = COOKIES.indexOf(';', start);
        if (end < 0) {
            end = COOKIES.length();
        }
        return COOKIES.substring(start, end).trim();
    }

    public static void fromProperties(Properties props) {
        COOKIES = normalizeForStorage(props.getProperty(COOKIE_KEY, COOKIES));
    }

    public static void writeToProperties(Properties props) {
        if (COOKIES != null && !COOKIES.isBlank()) {
            props.setProperty(COOKIE_KEY, COOKIES);
        }
    }

    /**
     * 将原始 Cookie 文本规范化为干净的 "k=v; k2=v2" 存储格式：
     * 去除 Cookie:/Set-Cookie: 前缀、换行，并剥离 Set-Cookie 的属性项（domain/path/expires 等）。
     */
    public static String normalizeForStorage(@Nullable String rawCookies) {
        if (rawCookies == null || rawCookies.isBlank()) {
            return "";
        }
        String text = rawCookies.replace("\r", ";").replace("\n", ";").trim();
        if (text.regionMatches(true, 0, "Cookie:", 0, "Cookie:".length())) {
            text = text.substring("Cookie:".length()).trim();
        }
        if (text.regionMatches(true, 0, "Set-Cookie:", 0, "Set-Cookie:".length())) {
            text = text.substring("Set-Cookie:".length()).trim();
        }

        var joiner = new StringJoiner("; ");
        for (var token : text.split(";")) {
            var part = token.trim();
            if (part.regionMatches(true, 0, "Cookie:", 0, "Cookie:".length())) {
                part = part.substring("Cookie:".length()).trim();
            }
            if (part.regionMatches(true, 0, "Set-Cookie:", 0, "Set-Cookie:".length())) {
                part = part.substring("Set-Cookie:".length()).trim();
            }
            if (part.isEmpty() || !part.contains("=")) {
                continue;
            }
            var name = part.substring(0, part.indexOf('=')).trim();
            var value = part.substring(part.indexOf('=') + 1).trim();
            if (name.isEmpty() || value.isEmpty() || COOKIE_ATTRIBUTES.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            joiner.add(name + "=" + value);
        }
        return joiner.toString();
    }
}

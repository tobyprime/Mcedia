package top.tobyprime.mcedia_platforms.auth;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;

public final class BilibiliCookie {
    private static final String COOKIE_KEY = "BILIBILI_COOKIES";
    private static final Set<String> COOKIE_ATTRIBUTES = Set.of(
            "domain", "path", "expires", "max-age", "secure", "httponly", "samesite", "priority", "partitioned");
    @Nullable
    private static String COOKIES;

    private BilibiliCookie() {
    }

    public static @Nullable String getCookie() {
        return COOKIES;
    }

    public static void saveCookies(String cookies) {
        COOKIES = normalizeForStorage(cookies);
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

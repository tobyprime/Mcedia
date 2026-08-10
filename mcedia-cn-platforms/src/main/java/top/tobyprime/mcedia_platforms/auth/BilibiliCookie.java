package top.tobyprime.mcedia_platforms.auth;

import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;

public final class BilibiliCookie {
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliCookie.class);
    private static final String COOKIE_KEY = "BILIBILI_COOKIES";
    private static final String BUVID3_KEY = "BILIBILI_BUVID3";
    private static final String BUVID4_KEY = "BILIBILI_BUVID4";
    private static final String FINGER_SPI_API = "https://api.bilibili.com/x/frontend/finger/spi";
    private static final Set<String> COOKIE_ATTRIBUTES = Set.of(
            "domain", "path", "expires", "max-age", "secure", "httponly", "samesite", "priority", "partitioned");
    @Nullable
    private static String COOKIES;
    @Nullable
    private static String BUVID3;
    @Nullable
    private static String BUVID4;

    private BilibiliCookie() {
    }

    public static @Nullable String getCookie() {
        return COOKIES;
    }

    public static void saveCookies(String cookies) {
        COOKIES = normalizeForStorage(cookies);
    }

    /**
     * 返回登录 Cookie 与 buvid 设备指纹合并后的完整 Cookie 头；未登录时仅返回 buvid 指纹。
     * buvid 是 B 站识别匿名客户端的关键指纹，缺失时高频调用 playurl 等接口极易触发 412 风控。
     */
    public static String combinedCookie() {
        var buvid = ensureBuvid();
        if (COOKIES == null || COOKIES.isBlank()) {
            return buvid;
        }
        return buvid.isBlank() ? COOKIES : COOKIES + "; " + buvid;
    }

    /** 获取 buvid3/buvid4 指纹 cookie 片段（形如 "buvid3=..; buvid4=.."）；失败时返回空串。 */
    public static synchronized String ensureBuvid() {
        if (BUVID3 != null && !BUVID3.isBlank() && BUVID4 != null && !BUVID4.isBlank()) {
            return "buvid3=" + BUVID3 + "; buvid4=" + BUVID4;
        }
        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(FINGER_SPI_API))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
            var buvid3 = data.get("b_3").getAsString();
            var buvid4 = data.get("b_4").getAsString();
            if (buvid3.isBlank() || buvid4.isBlank()) {
                throw new IllegalStateException("spi 返回空 buvid");
            }
            BUVID3 = buvid3;
            BUVID4 = buvid4;
            LOGGER.info("Obtained Bilibili buvid fingerprint");
            return "buvid3=" + buvid3 + "; buvid4=" + buvid4;
        } catch (Exception e) {
            LOGGER.warn("Failed to obtain Bilibili buvid fingerprint: {}", e.getMessage());
            return "";
        }
    }

    /** 清空并重新获取 buvid 指纹，用于命中 412 风控后的自愈重试。 */
    public static synchronized boolean refreshBuvid() {
        BUVID3 = null;
        BUVID4 = null;
        return !ensureBuvid().isBlank();
    }

    public static void fromProperties(Properties props) {
        COOKIES = normalizeForStorage(props.getProperty(COOKIE_KEY, COOKIES));
        BUVID3 = props.getProperty(BUVID3_KEY, BUVID3);
        BUVID4 = props.getProperty(BUVID4_KEY, BUVID4);
    }

    public static void writeToProperties(Properties props) {
        if (COOKIES != null && !COOKIES.isBlank()) {
            props.setProperty(COOKIE_KEY, COOKIES);
        }
        if (BUVID3 != null && !BUVID3.isBlank()) {
            props.setProperty(BUVID3_KEY, BUVID3);
        }
        if (BUVID4 != null && !BUVID4.isBlank()) {
            props.setProperty(BUVID4_KEY, BUVID4);
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

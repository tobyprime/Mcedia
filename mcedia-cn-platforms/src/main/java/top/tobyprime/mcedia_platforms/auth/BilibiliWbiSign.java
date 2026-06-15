package top.tobyprime.mcedia_platforms.auth;

import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BilibiliWbiSign {
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliWbiSign.class);
    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
    };

    private static volatile String cachedMixinKey;
    private static volatile long cachedDay;

    private BilibiliWbiSign() {
    }

    /**
     * Signs a parameter map with Wbi authentication (adds wts and w_rid).
     */
    public static Map<String, String> sign(Map<String, String> params) throws Exception {
        var mixinKey = getMixinKey();

        var wts = System.currentTimeMillis() / 1000;
        params.put("wts", String.valueOf(wts));

        var keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);

        var sb = new StringBuilder();
        for (var key : keys) {
            if (!sb.isEmpty()) sb.append('&');
            var value = params.get(key).replaceAll("[!'()*]", "");
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        sb.append(mixinKey);

        var md = MessageDigest.getInstance("MD5");
        var wRid = HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        params.put("w_rid", wRid);

        return params;
    }

    private static String getMixinKey() throws Exception {
        var today = System.currentTimeMillis() / 86400_000;
        if (cachedDay == today && cachedMixinKey != null) {
            return cachedMixinKey;
        }

        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.bilibili.com/x/web-interface/nav"))
                .header("User-Agent", "Mozilla/5.0")
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        var data = json.getAsJsonObject("data");
        var wbiImg = data.getAsJsonObject("wbi_img");
        var imgKey = extractFileName(wbiImg.get("img_url").getAsString());
        var subKey = extractFileName(wbiImg.get("sub_url").getAsString());

        cachedMixinKey = getMixinKeyFromKeys(imgKey + subKey);
        cachedDay = today;
        LOGGER.info("Wbi keys refreshed for today");
        return cachedMixinKey;
    }

    private static String extractFileName(String url) {
        var lastSlash = url.lastIndexOf('/');
        var dot = url.lastIndexOf('.');
        if (lastSlash >= 0 && dot > lastSlash) {
            return url.substring(lastSlash + 1, dot);
        }
        return url;
    }

    private static String getMixinKeyFromKeys(String orig) {
        var chars = new char[MIXIN_KEY_ENC_TAB.length];
        for (int i = 0; i < MIXIN_KEY_ENC_TAB.length; i++) {
            chars[i] = orig.charAt(MIXIN_KEY_ENC_TAB[i]);
        }
        return new String(chars);
    }

    public static Map<String, String> createBaseParams(String roomId, String qn) {
        var params = new LinkedHashMap<String, String>();
        params.put("room_id", roomId);
        params.put("no_playurl", "0");
        params.put("mask", "1");
        params.put("qn", qn);
        params.put("platform", "web");
        params.put("protocol", "0,1");
        params.put("format", "0,1,2");
        params.put("codec", "0,1,2");
        params.put("dolby", "5");
        params.put("panorama", "1");
        params.put("eotf", "0,1,2");
        params.put("supported_drms", "0,1,2,3");
        params.put("web_location", "444.8");
        return params;
    }
}

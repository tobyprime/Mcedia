package top.tobyprime.mcedia.bilibili;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.danmaku.Danmaku;
import top.tobyprime.mcedia.danmaku.Danmaku.DanmakuType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * 用于获取指定 cid 的弹幕列表
 */
public class BilibiliDanmakuFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliDanmakuFetcher.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
    private static final Pattern DANMAKU_PATTERN = Pattern.compile("<d p=\"([^\"]*)\">([^<]*)</d>");

    public static CompletableFuture<List<Danmaku>> fetchDanmakuAsync(long cid) {
        if (cid <= 0) {
            LOGGER.warn("无效的 cid: {}", cid);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Encoding", "deflate")
                .GET()
                .build();

        // 异步请求 + 手动解压
        return  HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .<List<Danmaku>>thenApply(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.warn("弹幕请求失败，HTTP {}", response.statusCode());
                        return Collections.emptyList();
                    }

                    byte[] compressedBytes = response.body();
                    String xmlContent;

                    try {
                        Inflater inflater = new Inflater(true);
                        try (InputStream decompressed =
                                     new InflaterInputStream(new ByteArrayInputStream(compressedBytes), inflater)) {
                            xmlContent = new String(decompressed.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    } catch (Exception e) {
                        LOGGER.error("[DANMAKU-DEBUG] 解压失败，cid {}", cid, e);
                        return Collections.emptyList();
                    }

                    LOGGER.info("[DANMAKU-DEBUG] XML 解压成功 (前500字符): {}",
                            xmlContent.substring(0, Math.min(xmlContent.length(), 500)));

                    return parseDanmaku(xmlContent);
                })
                .exceptionally(e -> {
                    LOGGER.error("异步获取弹幕时异常, cid {}", cid, e);
                    return Collections.emptyList();
                });
    }

    private static List<Danmaku> parseDanmaku(String xmlContent) {
        List<Danmaku> list = new ArrayList<>();
        Matcher matcher = DANMAKU_PATTERN.matcher(xmlContent);

        while (matcher.find()) {
            String[] pAttr = matcher.group(1).split(",");
            if (pAttr.length < 4) continue;

            float timestamp = Float.parseFloat(pAttr[0]);
            int mode = Integer.parseInt(pAttr[1]);
            int color = Integer.parseInt(pAttr[3]);
            int argb = 0xFF000000 | color;
            String text = matcher.group(2);

            DanmakuType type;
            if (mode >= 1 && mode <= 3) type = DanmakuType.SCROLLING;
            else if (mode == 4) type = DanmakuType.BOTTOM;
            else if (mode == 5) type = DanmakuType.TOP;
            else continue;

            list.add(new Danmaku(timestamp, text, argb, type));
        }

        Collections.sort(list);
        LOGGER.info("解析到 {} 条弹幕", list.size());
        return list;
    }
}

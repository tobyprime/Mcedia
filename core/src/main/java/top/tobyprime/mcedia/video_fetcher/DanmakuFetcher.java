package top.tobyprime.mcedia.video_fetcher;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.Danmaku;
import top.tobyprime.mcedia.core.Danmaku.DanmakuType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class DanmakuFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DanmakuFetcher.class);
    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient();
    private static final Pattern DANMAKU_PATTERN = Pattern.compile("<d p=\"([^\"]*)\">([^<]*)</d>");

    public static CompletableFuture<List<Danmaku>> fetchDanmaku(long cid) {
        if (cid <= 0) {
            LOGGER.warn("无效的 cid: {}, 无法获取弹幕。", cid);
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        Request request = new Request.Builder()
                .url("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Encoding", "deflate") // 明确请求这个特殊的deflate
                .build();

        CompletableFuture<List<Danmaku>> future = new CompletableFuture<>();

        OK_HTTP_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                LOGGER.error("使用 OkHttp 获取弹幕时发生网络错误, cid: {}", cid, e);
                future.complete(Collections.emptyList());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                List<Danmaku> danmakuList = new ArrayList<>();
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        LOGGER.warn("获取弹幕请求失败, HTTP Code: {}", response.code());
                        future.complete(Collections.emptyList());
                        return;
                    }

                    byte[] compressedBytes = body.bytes();
                    String xmlContent;

                    try {
                        Inflater inflater = new Inflater(true);
                        InputStream decompressedStream = new InflaterInputStream(new ByteArrayInputStream(compressedBytes), inflater);
                        xmlContent = new String(decompressedStream.readAllBytes(), "UTF-8");

                    } catch (Exception e) {
                        LOGGER.error("[DANMAKU-DEBUG] 最终尝试(raw DEFLATE)解压失败！这是一个未知的压缩格式。cid: {}", cid, e);
                        future.complete(Collections.emptyList());
                        return;
                    }

                    LOGGER.info("[DANMAKU-DEBUG] 成功解压弹幕XML (前500字符): {}", xmlContent.substring(0, Math.min(xmlContent.length(), 500)));

                    Matcher matcher = DANMAKU_PATTERN.matcher(xmlContent);
                    while (matcher.find()) {
                        String pAttribute = matcher.group(1);
                        String text = matcher.group(2);
                        String[] attributes = pAttribute.split(",");
                        if (attributes.length >= 4) {
                            float timestamp = Float.parseFloat(attributes[0]);
                            int mode = Integer.parseInt(attributes[1]);
                            int color = Integer.parseInt(attributes[3]);
                            int argbColor = 0xFF000000 | color;

                            DanmakuType type;
                            if (mode >= 1 && mode <= 3) {
                                type = DanmakuType.SCROLLING;
                            } else if (mode == 4) {
                                type = DanmakuType.BOTTOM;
                            } else if (mode == 5) {
                                type = DanmakuType.TOP;
                            } else {
                                continue; // 忽略其他特殊类型弹幕
                            }

                            danmakuList.add(new Danmaku(timestamp, text, argbColor, type));
                        }
                    }

                    Collections.sort(danmakuList);
                    if (!danmakuList.isEmpty()) {
                        LOGGER.info("成功获取并解析了 {} 条弹幕。", danmakuList.size());
                    } else {
                        LOGGER.warn("[DANMAKU-DEBUG] Parsed 0 danmaku from the XML, but decompression was successful.");
                    }
                    future.complete(danmakuList);

                } catch (Exception e) {
                    LOGGER.error("处理弹幕响应时发生严重错误, cid: {}", cid, e);
                    future.complete(Collections.emptyList());
                }
            }
        });

        return future;
    }
}
package top.tobyprime.mcedia.yhdm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.BaseMediaPlay;
import top.tobyprime.mcedia.core.MediaInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YhdmMediaPlay extends BaseMediaPlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(YhdmMediaPlay.class);
    private static final Pattern URL_PARTS_PATTERN = Pattern.compile("yhdm\\.one/vod-play/([^/]+)/([^.]+)\\.html");
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(.*?)</title>");
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    public String url;

    public YhdmMediaPlay(String url) {
        this.url = url;
        fetchAsync();
    }


    /** 异步版本 */
    public CompletableFuture<Void> fetchAsync() {
        LOGGER.info("正在从 YHDM 获取视频信息: {}", url);

        // 解析 URL 参数
        Matcher partsMatcher = URL_PARTS_PATTERN.matcher(url);
        if (!partsMatcher.find()) {
            setStatus("不支持的链接");
            return CompletableFuture.completedFuture(null);
        }

        String videoId = partsMatcher.group(1);
        String episodeId = partsMatcher.group(2);

        // --- 异步请求主页面标题 ---
        HttpRequest mainPageReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        CompletableFuture<String> titleFuture = client
                .sendAsync(mainPageReq, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    try {
                        Matcher titleMatcher = TITLE_PATTERN.matcher(resp.body());
                        if (titleMatcher.find()) {
                            return titleMatcher.group(1).split("-")[0].trim();
                        }
                    } catch (Exception e) {
                        LOGGER.warn("获取页面标题失败，将使用默认标题。", e);
                    }
                    return "未知视频";
                });

        // --- 请求 API ---
        String apiUrl = String.format("https://yhdm.one/_get_plays/%s/%s", videoId, episodeId);
        LOGGER.info("正在请求 YHDM API: {}", apiUrl);

        HttpRequest apiReq = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", url)
                .build();

        CompletableFuture<String> apiFuture = client
                .sendAsync(apiReq, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);

        // --- 组合两个异步结果 ---
        return titleFuture.thenCombine(apiFuture, (title, apiJson) -> {
            try {
                JsonObject responseJson = gson.fromJson(apiJson, JsonObject.class);
                JsonArray videoPlays = responseJson.getAsJsonArray("video_plays");

                if (videoPlays == null || videoPlays.isEmpty()) {
                    throw new RuntimeException("API响应中未找到 video_plays");
                }

                JsonObject firstPlaySource = videoPlays.get(0).getAsJsonObject();
                String m3u8Url = firstPlaySource.get("play_data").getAsString();

                LOGGER.info("成功从 API 提取到 M3U8 链接: {}", m3u8Url);
                LOGGER.info("成功提取到标题: {}", title);

                MediaInfo info = new MediaInfo();
                info.title = title;
                info.streamUrl = m3u8Url;
                info.platform = "樱花动漫";

                setMediaInfo(info);
            } catch (Exception e) {
                LOGGER.error("解析 API JSON 失败: {}", apiJson, e);
                setStatus("解析失败");
            }
            return null;
        });
    }
}

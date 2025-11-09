package top.tobyprime.mcedia.video_fetcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YhdmFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(YhdmFetcher.class);
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    // 正则1: 用于从原始URL中提取视频ID和剧集ID
    private static final Pattern URL_PARTS_PATTERN = Pattern.compile("yhdm\\.one/vod-play/([^/]+)/([^.]+)\\.html");
    // 正则2: 用于从主页面HTML中提取标题
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(.*?)</title>");

    public static VideoInfo fetch(String url) throws Exception {
        LOGGER.info("正在从 YHDM 获取视频信息: {}", url);

        // --- 阶段 1: 解析URL，获取视频ID和剧集ID ---
        Matcher partsMatcher = URL_PARTS_PATTERN.matcher(url);
        if (!partsMatcher.find()) {
            throw new Exception("无法从URL中解析视频ID和剧集ID: " + url);
        }
        String videoId = partsMatcher.group(1);
        String episodeId = partsMatcher.group(2);

        // --- 阶段 2: (可选) 请求主页面，仅为获取标题 ---
        String title = "未知视频";
        try {
            HttpRequest mainPageRequest = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> mainPageResponse = client.send(mainPageRequest, HttpResponse.BodyHandlers.ofString());
            Matcher titleMatcher = TITLE_PATTERN.matcher(mainPageResponse.body());
            if (titleMatcher.find()) {
                title = titleMatcher.group(1).split("-")[0].trim();
            }
        } catch (Exception e) {
            LOGGER.warn("获取页面标题失败，将使用默认标题。", e);
        }

        // --- 阶段 3: 模拟JavaScript API请求 ---
        String apiUrl = String.format("https://yhdm.one/_get_plays/%s/%s", videoId, episodeId);
        LOGGER.info("正在请求 YHDM API: {}", apiUrl);

        HttpRequest apiRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", url) // Referer很重要，模拟是从主页面发起的请求
                .build();

        HttpResponse<String> apiResponse = client.send(apiRequest, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = apiResponse.body();

        try {
            // --- 阶段 4: 解析API返回的JSON ---
            JsonObject responseJson = gson.fromJson(jsonResponse, JsonObject.class);
            JsonArray videoPlays = responseJson.getAsJsonArray("video_plays");

            if (videoPlays == null || videoPlays.isEmpty()) {
                throw new Exception("API响应中未找到 'video_plays' 数据");
            }

            // 获取第一个播放源的数据
            JsonObject firstPlaySource = videoPlays.get(0).getAsJsonObject();
            String m3u8Url = firstPlaySource.get("play_data").getAsString();

            LOGGER.info("成功从 API 提取到 M3U8 链接: {}", m3u8Url);
            LOGGER.info("成功提取到标题: {}", title);

            return new VideoInfo(m3u8Url, null, title, "樱花动漫");

        } catch (Exception e) {
            LOGGER.error("解析 YHDM API 响应JSON失败: {}", jsonResponse, e);
            throw new Exception("解析API响应失败: " + e.getMessage());
        }
    }
}
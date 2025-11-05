package top.tobyprime.mcedia.video_fetcher;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BilibiliBangumiFetcher {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliBangumiFetcher.class);

    public static VideoInfo fetch(String bangumiUrl, @Nullable String cookie, String desiredQuality) throws Exception {
        // 从 URL 中提取 ep 号
        Pattern epPattern = Pattern.compile("/ep(\\d+)");
        Matcher matcher = epPattern.matcher(bangumiUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("未找到ep号，请检查番剧链接");
        }
        String epId = matcher.group(1);

        // 获取番剧/电影信息
        String viewApi = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epId;
        HttpRequest viewRequest = HttpRequest.newBuilder().uri(URI.create(viewApi)).header("User-Agent", "Mozilla/5.0").build();
        HttpResponse<String> viewResponse = client.send(viewRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject viewJson = new JSONObject(viewResponse.body());
        String title = "未知标题";
        String author = "Bilibili动漫";
        if (viewJson.optInt("code") == 0) {
            JSONObject result = viewJson.getJSONObject("result");
            title = result.getString("title");
            // 找到当前集的标题
            for (Object ep : result.getJSONArray("episodes")) {
                if (String.valueOf(((JSONObject)ep).getInt("id")).equals(epId)) {
                    title = title + " - " + ((JSONObject)ep).getString("share_copy");
                    break;
                }
            }
        }

        // 调用 PGC 的播放地址 API
        String playApi = "https://api.bilibili.com/pgc/player/web/playurl?ep_id=" + epId +
                "&qn=112&type=&otype=json&platform=html5&high_quality=1&fnval=16";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(playApi))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/");

        if (cookie != null && !cookie.isEmpty()) {
            requestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        LOGGER.info("Bilibili Bangumi API Response: {}", responseBody);

        JSONObject responseJson = new JSONObject(responseBody);
        if (responseJson.getInt("code") != 0) {
            throw new RuntimeException("获取番剧播放地址失败: " + responseJson.optString("message") + " | Full Response: " + responseBody);
        }

        JSONObject result = responseJson.getJSONObject("result");

        // 优先尝试解析 DASH (高画质，音视频分离)
        if (result.has("dash")) {
            JSONObject dash = result.getJSONObject("dash");
            if (dash.has("video") && dash.has("audio") && dash.getJSONArray("video").length() > 0 && dash.getJSONArray("audio").length() > 0) {
                JSONObject selectedVideo = findBestStream(dash.getJSONArray("video"), result.optJSONArray("support_formats"), desiredQuality);
                JSONObject selectedAudio = findBestStream(dash.getJSONArray("audio"), null, "自动"); // 音频总是选最好的

                if (selectedVideo != null && selectedAudio != null) {
                    LOGGER.info("成功解析DASH格式视频流");
                    String videoBaseUrl = selectedVideo.getString("baseUrl");
                    String audioBaseUrl = selectedAudio.getString("baseUrl");
                    return new VideoInfo(videoBaseUrl, audioBaseUrl, title, author);
                }
            }
        }

        // 如果没有 DASH，尝试解析 DURL (低画质/预览，音视频合并)
        if (result.has("durl")) {
            JSONArray durlArray = result.getJSONArray("durl");
            if (durlArray.length() > 0) {
                LOGGER.info("解析DASH失败，降级到DURL格式");
                String playableUrl = durlArray.getJSONObject(0).getString("url");
                return new VideoInfo(playableUrl, null, title, author); // DURL 格式的 audioUrl 为 null
            }
        }

        throw new RuntimeException("未能从番剧API响应中找到可用的视频流。API Response: " + responseBody);
    }

    /**
     * 根据期望的清晰度从流列表中选择最佳的流。
     * @param streams JSON 数组，包含视频或音频流
     * @param formats JSON 数组，包含清晰度格式的描述信息
     * @param desiredQuality 用户期望的清晰度描述字符串
     * @return 匹配的最佳流的 JSONObject，找不到则返回最高质量的流
     */
    private static JSONObject findBestStream(JSONArray streams, @Nullable JSONArray formats, String desiredQuality) {
        if (streams == null || streams.length() == 0) {
            return null;
        }

        // 如果是"自动"、纯音频流（无formats）、或清晰度列表为空，直接返回最高码率的（API返回的第一个通常是最好的）
        if ("自动".equals(desiredQuality) || formats == null || formats.length() == 0) {
            return streams.getJSONObject(0);
        }

        // 构建清晰度名称到ID的映射表
        Map<String, Integer> qualityMap = new HashMap<>();
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.getJSONObject(i);
            // new_description 是 B 站网页上显示的文本, e.g., "1080P 高清"
            qualityMap.put(format.getString("new_description"), format.getInt("quality"));
        }

        Integer targetQualityId = qualityMap.get(desiredQuality);
        if (targetQualityId != null) {
            // 寻找 ID 完全匹配的流
            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                if (stream.getInt("id") == targetQualityId) {
                    LOGGER.info("找到匹配的清晰度: {} (ID: {})", desiredQuality, targetQualityId);
                    return stream;
                }
            }
        }

        // 如果找不到精确匹配的（比如用户输错了），则降级为返回最高画质
        LOGGER.warn("未找到清晰度 '{}'，将使用最高可用清晰度。", desiredQuality);
        return streams.getJSONObject(0);
    }
}
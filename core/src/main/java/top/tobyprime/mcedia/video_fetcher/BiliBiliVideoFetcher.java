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

public class BiliBiliVideoFetcher {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BiliBiliVideoFetcher.class);

    // --- 核心修复：添加第三个参数 desiredQuality ---
    public static VideoInfo fetch(String videoUrl, @Nullable String cookie, String desiredQuality) throws Exception {
        // 1. 提取 BV 号
        Pattern bvPattern = Pattern.compile("(BV[0-9A-Za-z]+)");
        Matcher matcher = bvPattern.matcher(videoUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("未找到BV号，请检查视频链接");
        }
        String bvid = matcher.group(1);

        Pattern pagePattern = Pattern.compile("[?&]p=(\\d+)");
        Matcher pageMatcher = pagePattern.matcher(videoUrl);
        int page = 1;
        if (pageMatcher.find()) {
            page = Integer.parseInt(pageMatcher.group(1));
        }

        // 2. 获取 CID
        String pagelistApi = "https://api.bilibili.com/x/player/pagelist?bvid=" + bvid + "&jsonp=jsonp";
        HttpRequest.Builder pagelistRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(pagelistApi))
                .header("User-Agent", "Mozilla/5.0");
        if (cookie != null && !cookie.isEmpty()) {
            pagelistRequestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> pagelistResponse = client.send(pagelistRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JSONObject pagelistJson = new JSONObject(pagelistResponse.body());
        if (pagelistJson.getInt("code") != 0) {
            throw new RuntimeException("获取 CID 失败: " + pagelistJson.optString("message"));
        }
        JSONArray pages = pagelistJson.getJSONArray("data");
        if (page > pages.length()) {
            throw new RuntimeException("指定的分P不存在: " + page);
        }
        String cid = pages.getJSONObject(page - 1).get("cid").toString();

        // 3. 获取播放地址
        String playApi = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                "&cid=" + cid + "&qn=112&type=&otype=json&platform=html5&high_quality=1&fnval=16";
        HttpRequest.Builder playRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(playApi))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/");
        if (cookie != null && !cookie.isEmpty()) {
            playRequestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> playResponse = client.send(playRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JSONObject playJson = new JSONObject(playResponse.body());
        if (playJson.getInt("code") != 0) {
            throw new RuntimeException("获取视频播放地址失败: " + playJson.optString("message"));
        }

        JSONObject data = playJson.getJSONObject("data");

        // 4. 优先解析 DASH 格式
        if (data.has("dash")) {
            JSONObject dash = data.getJSONObject("dash");
            if (dash.has("video") && dash.has("audio") && dash.getJSONArray("video").length() > 0 && dash.getJSONArray("audio").length() > 0) {
                JSONObject selectedVideo = findBestStream(dash.getJSONArray("video"), data.optJSONArray("support_formats"), desiredQuality);
                JSONObject selectedAudio = findBestStream(dash.getJSONArray("audio"), null, "自动");

                if (selectedVideo != null && selectedAudio != null) {
                    String videoBaseUrl = selectedVideo.getString("baseUrl");
                    String audioBaseUrl = selectedAudio.getString("baseUrl");
                    return new VideoInfo(videoBaseUrl, audioBaseUrl);
                }
            }
        }

        // 5. 回退到 DURL 格式
        if (data.has("durl")) {
            JSONArray durlArray = data.getJSONArray("durl");
            if (durlArray.length() > 0) {
                String url = durlArray.getJSONObject(0).getString("url");
                return new VideoInfo(url, null);
            }
        }

        throw new RuntimeException("未能从API响应中找到可用的视频流");
    }

    private static JSONObject findBestStream(JSONArray streams, @Nullable JSONArray formats, String desiredQuality) {
        if (streams == null || streams.length() == 0) {
            return null;
        }
        if ("自动".equals(desiredQuality) || formats == null || formats.length() == 0) {
            return streams.getJSONObject(0);
        }
        Map<String, Integer> qualityMap = new HashMap<>();
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.getJSONObject(i);
            qualityMap.put(format.getString("new_description"), format.getInt("quality"));
        }
        Integer targetQualityId = qualityMap.get(desiredQuality);
        if (targetQualityId != null) {
            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                if (stream.getInt("id") == targetQualityId) {
                    LOGGER.info("找到匹配的清晰度: {} (ID: {})", desiredQuality, targetQualityId);
                    return stream;
                }
            }
        }
        LOGGER.warn("未找到清晰度 '{}'，将使用最高可用清晰度。", desiredQuality);
        return streams.getJSONObject(0);
    }
}
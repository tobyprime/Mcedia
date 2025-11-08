package top.tobyprime.mcedia.video_fetcher;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.BilibiliAuthRequiredException;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiliBiliVideoFetcher {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BiliBiliVideoFetcher.class);

    public static VideoInfo fetch(String videoUrl, @Nullable String cookie, String desiredQuality) throws Exception {
        // 提取 BV 号
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

        // 获取视频信息（标题/作者）
        String viewApi = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        HttpRequest viewRequest = HttpRequest.newBuilder().uri(URI.create(viewApi)).header("User-Agent", "Mozilla/5.0").build();
        HttpResponse<String> viewResponse = client.send(viewRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject viewJson = new JSONObject(viewResponse.body());
        String title = "未知标题";
        String author = "未知作者";

        if (viewJson.optInt("code") == 0) {
            JSONObject viewData = viewJson.getJSONObject("data");
            title = viewData.getString("title");
            author = viewData.getJSONObject("owner").getString("name");

            JSONObject rights = viewData.getJSONObject("rights");
        }

        // 获取 CID
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

        // 获取播放地址
        String playApi = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                "&cid=" + cid + "&127&fnval=4048";
        HttpRequest.Builder playRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(playApi))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/");
        if (cookie != null && !cookie.isEmpty()) {
            playRequestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> playResponse = client.send(playRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JSONObject playJson = new JSONObject(playResponse.body());

        // 检查API响应，处理需要登录或VIP的情况
        if (playJson.getInt("code") != 0) {
            String message = playJson.optString("message");
            if (playJson.getInt("code") == -10403) { // B站特定错误码，表示需要登录或权限不足
                throw new BilibiliAuthRequiredException("b站说这个视频要登录或大会员才能看T.T: " + message);
            }
            throw new RuntimeException("获取视频播放地址失败: " + message);
        }

        JSONObject data = playJson.getJSONObject("data");
        boolean hasDash = data.has("dash") && data.getJSONObject("dash").has("video") && data.getJSONObject("dash").getJSONArray("video").length() > 0;

        // 优先解析 DASH 格式
        if (data.has("dash")) {
            JSONObject dash = data.getJSONObject("dash");
            if (dash.has("video") && dash.has("audio") && dash.getJSONArray("video").length() > 0 && dash.getJSONArray("audio").length() > 0) {
                JSONObject selectedVideo = findBestStream(dash.getJSONArray("video"), data.optJSONArray("support_formats"), desiredQuality);
                JSONObject selectedAudio = findBestStream(dash.getJSONArray("audio"), null, "自动");

                if (selectedVideo != null && selectedAudio != null) {
                    String videoBaseUrl = selectedVideo.getString("baseUrl");
                    String audioBaseUrl = selectedAudio.getString("baseUrl");
                    return new VideoInfo(videoBaseUrl, audioBaseUrl, title, author);
                }
            }
        }

        // 回退到 DURL 格式
        if (data.has("durl")) {
            JSONArray durlArray = data.getJSONArray("durl");
            if (durlArray.length() > 0) {
                String url = durlArray.getJSONObject(0).getString("url");
                LOGGER.warn("未找到DASH流，可能为会员内容。正在尝试播放试看片段 (DURL)。");
                return new VideoInfo(url, null, title, author);
            }
        }

        throw new BilibiliAuthRequiredException("该视频需要登录或大会员，且没有提供试看片段。");
    }

    private static JSONObject findBestStream(JSONArray streams, @Nullable JSONArray formats, String desiredQuality) {
        if (streams == null || streams.length() == 0) {
            return null;
        }

        // --- 自动清晰度逻辑 ---
        if ("自动".equals(desiredQuality)) {
            // 如果 formats 信息不可用，安全地回退到 API 默认提供的第一个流（通常是最高画质）
            if (formats == null || formats.length() == 0) {
                LOGGER.warn("自动清晰度选择: 缺少 formats 信息，将使用 API 提供的默认最高画质。");
                return streams.getJSONObject(0);
            }

            // 遍历 formats 数组，找到 quality ID 最高的对象
            int bestQualityId = -1;
            String bestQualityDescription = "N/A";
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.getJSONObject(i);
                int currentQuality = format.getInt("quality");
                if (currentQuality > bestQualityId) {
                    bestQualityId = currentQuality;
                    bestQualityDescription = format.getString("new_description");
                }
            }
            if (bestQualityId != -1) {
                LOGGER.info("自动清晰度选择: API 中可用的最高画质为 '{}' (ID: {})", bestQualityDescription, bestQualityId);
                JSONObject stream = findStreamByIdAndCodec(streams, bestQualityId);
                if (stream != null) {
                    return stream;
                }
            }
            LOGGER.warn("自动清晰度选择: 未能在 streams 数组中找到 ID 为 {} 的流，将使用 API 提供的默认最高画质。", bestQualityId);
            return streams.getJSONObject(0);
        }

        // --- 手动指定清晰度逻辑 ---
        if (formats == null || formats.length() == 0) {
            return streams.getJSONObject(0);
        }
        Map<String, Integer> qualityMap = new HashMap<>();
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.getJSONObject(i);
            qualityMap.put(format.getString("new_description"), format.getInt("quality"));
        }
        Integer targetQualityId = qualityMap.get(desiredQuality);
        if (targetQualityId != null) {
            JSONObject stream = findStreamByIdAndCodec(streams, targetQualityId);
            if (stream != null) {
                return stream;
            }
        }

        LOGGER.warn("未找到指定的清晰度 '{}'，将使用最高可用清晰度。", desiredQuality);
        return streams.getJSONObject(0);
    }

    // [新增] 辅助方法，用于根据ID查找流并选择最佳编码
    private static JSONObject findStreamByIdAndCodec(JSONArray streams, int targetId) {
        JSONObject bestStream = null;
        int bestCodecScore = -1; // -1: 未找到, 1: AV1, 2: HEVC, 3: AVC (H.264)

        for (int i = 0; i < streams.length(); i++) {
            JSONObject stream = streams.getJSONObject(i);
            if (stream.getInt("id") == targetId) {
                String codecs = stream.optString("codecs", "");
                int currentCodecScore = 0;
                if (codecs.contains("avc1")) currentCodecScore = 3;
                else if (codecs.contains("hev1")) currentCodecScore = 2;
                else if (codecs.contains("av01")) currentCodecScore = 1;
                else currentCodecScore = 0; // 未知编码

                if (currentCodecScore > bestCodecScore) {
                    bestStream = stream;
                    bestCodecScore = currentCodecScore;
                }
            }
        }
        return bestStream;
    }
}
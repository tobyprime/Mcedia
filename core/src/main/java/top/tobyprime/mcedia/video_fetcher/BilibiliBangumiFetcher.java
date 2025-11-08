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

public class BilibiliBangumiFetcher {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliBangumiFetcher.class);
    private static final int QUALITY_ID_4K = 120;

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
        boolean requiresVip = false;
        boolean requiresPurchase = false;

        if (viewJson.optInt("code") == 0) {
            JSONObject result = viewJson.getJSONObject("result");
            title = result.getString("title");

            // 解析付费信息
            if (result.has("payment")) {
                JSONObject payment = result.getJSONObject("payment");
                if (payment.has("price") && !payment.getString("price").equals("0.0")) {
                    requiresPurchase = true;
                }
            }
            // 默认番剧都要VIP
            requiresVip = true;

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
                "&qn=116&type=&otype=json&platform=html5&high_quality=1&fnval=4048";

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
        if (result == null) {
            throw new RuntimeException("B站API未返回有效的 result 数据：" + responseJson.optString("message", "未知错误"));
        }

        if (result.has("is_preview") && result.getInt("is_preview") == 1) {
            throw new BilibiliAuthRequiredException("该内容需要大会员或已登录。API返回了预览内容。");
        }
        if (result.has("code") && result.getInt("code") == -10403) {
            throw new BilibiliAuthRequiredException("B站返回权限错误(-10403)，该内容需要大会员或已登录。");
        }

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

        // --- 自动清晰度逻辑 ---
        if ("自动".equals(desiredQuality)) {
            if (formats == null || formats.length() == 0) {
                LOGGER.warn("自动清晰度选择: 缺少 formats 信息，将使用 API 提供的默认最高画质。");
                return streams.getJSONObject(0);
            }
            int bestQualityId = -1;
            String bestQualityDescription = "N/A";
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.getJSONObject(i);
                int currentQuality = format.getInt("quality");
                if (currentQuality < QUALITY_ID_4K && currentQuality > bestQualityId) {
                    bestQualityId = currentQuality;
                    bestQualityDescription = format.getString("new_description");
                }
            }
            if (bestQualityId != -1) {
                LOGGER.info("自动清晰度选择: 找到最佳的4K以下画质为 '{}' (ID: {})", bestQualityDescription, bestQualityId);
                JSONObject stream = findStreamByIdAndCodec(streams, bestQualityId);
                if (stream != null) {
                    return stream;
                }
            }
            LOGGER.warn("自动清晰度选择: 未找到合适的4K以下画质，将使用 API 提供的默认最高画质。");
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
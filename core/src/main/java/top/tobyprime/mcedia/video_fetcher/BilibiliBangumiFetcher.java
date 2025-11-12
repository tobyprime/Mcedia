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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BilibiliBangumiFetcher {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliBangumiFetcher.class);
    private static final int QUALITY_ID_4K = 120;

    private static Supplier<Boolean> authStatusSupplier = () -> false;

    private static class StreamSelection {
        final JSONObject stream;
        final String qualityDescription;
        StreamSelection(JSONObject stream, String qualityDescription) {
            this.stream = stream;
            this.qualityDescription = qualityDescription;
        }
    }

    public static void setAuthStatusSupplier(Supplier<Boolean> supplier) {
        if (supplier != null) {
            authStatusSupplier = supplier;
        }
    }

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

        String mainTitle = "未知番剧/电影";
        String author = "Bilibili";
        String partName = null;
        int currentP = 0;
        boolean requiresVip = false;
        boolean requiresPurchase = false;
        String cid = null;

        if (viewJson.optInt("code") == 0) {
            JSONObject result = viewJson.getJSONObject("result");
            mainTitle = result.getString("title");
            author = result.optString("season_title", "Bilibili");

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
            JSONArray episodes = result.getJSONArray("episodes");
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject ep = episodes.getJSONObject(i);
                if (String.valueOf(ep.getInt("id")).equals(epId)) {
                    partName = ep.getString("share_copy");
                    cid = String.valueOf(ep.getLong("cid"));
                    currentP = i + 1;
                    if (partName != null && partName.equals(mainTitle)) {
                        partName = null;
                    }
                    break;
                }
            }
        } else {
            throw new RuntimeException("获取番剧信息失败: " + viewJson.optString("message"));
        }

        if (cid == null) {
            throw new RuntimeException("无法获取当前分集的CID，可能是ep_id无效。");
        }

        // 调用 PGC 的播放地址 API
        String playApi = "https://api.bilibili.com/pgc/player/web/playurl?ep_id=" + epId +
                "&qn=116&fnval=4048";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(playApi))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/");

        if (cookie != null && !cookie.isEmpty()) {
            requestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
//        LOGGER.info("Bilibili Bangumi API Response: {}", responseBody);

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

        List<String> availableQualities = new ArrayList<>();
        JSONArray supportFormats = result.optJSONArray("support_formats");
        if (supportFormats != null) {
            for (int i = 0; i < supportFormats.length(); i++) {
                availableQualities.add(supportFormats.getJSONObject(i).getString("new_description"));
            }
        }

        String finalCurrentQuality = null;

        // 优先尝试解析 DASH (高画质，音视频分离)
        if (result.has("dash")) {
            JSONObject dash = result.getJSONObject("dash");
            if (dash.has("video") && dash.has("audio") && !dash.getJSONArray("video").isEmpty() && !dash.getJSONArray("audio").isEmpty()) {
                StreamSelection videoSelection = findBestStream(dash.getJSONArray("video"), supportFormats, desiredQuality);
                StreamSelection audioSelection = findBestStream(dash.getJSONArray("audio"), null, "自动");

                if (videoSelection != null && videoSelection.stream != null && audioSelection != null) {
                    finalCurrentQuality = videoSelection.qualityDescription;
                    String videoBaseUrl = videoSelection.stream.getString("baseUrl");
                    String audioBaseUrl = audioSelection.stream.getString("baseUrl");
                    return new VideoInfo(videoBaseUrl, audioBaseUrl, mainTitle, author, null, partName, currentP, availableQualities, finalCurrentQuality);
                }
            }
        }

        // 如果没有 DASH，尝试解析 DURL (低画质/预览，音视频合并)
        if (result.has("durl")) {
            JSONArray durlArray = result.getJSONArray("durl");
            if (durlArray.length() > 0) {
                if (supportFormats != null && !supportFormats.isEmpty()) {
                    finalCurrentQuality = supportFormats.getJSONObject(0).getString("new_description");
                }
                LOGGER.info("解析DASH失败，降级到DURL格式");
                String playableUrl = durlArray.getJSONObject(0).getString("url");
                return new VideoInfo(playableUrl, null, mainTitle, author, null, partName, currentP, availableQualities, finalCurrentQuality);
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
    private static StreamSelection findBestStream(JSONArray streams, @Nullable JSONArray formats, String desiredQuality) {
        if (streams == null || streams.length() == 0) {
            return null;
        }

        if (formats == null || formats.length() == 0) {
            return new StreamSelection(streams.getJSONObject(0), "默认音质");
        }

        Map<String, Integer> availableQualityMap = new HashMap<>();
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.getJSONObject(i);
            availableQualityMap.put(format.getString("new_description"), format.getInt("quality"));
        }

        // --- 自动清晰度逻辑 ---
        boolean isLoggedIn = authStatusSupplier.get();

        if ("自动".equals(desiredQuality)) {
            // 对于音频流 (formats == null)，总是选择第一个（通常是最好的）
            if (isLoggedIn) {
                LOGGER.info("用户已登录，应用1080P60帧为上限的画质策略。");
                List<String> preferredQualities = List.of(
                        "1080P 60帧", "1080P 高码率", "1080P 高清", "1080P",
                        "720P 60帧", "720P 高清", "720P", "高清 720P",
                        "480P 高清", "480P", "标清 480P"
                );
                for (String preferred : preferredQualities) {
                    Integer targetId = availableQualityMap.get(preferred);
                    if (targetId != null) {
                        JSONObject stream = findStreamByIdAndCodec(streams, targetId);
                        if (stream != null) {
                            LOGGER.info("自动清晰度(已登录): 找到匹配 '{}'", preferred);
                            return new StreamSelection(stream, preferred);
                        }
                    }
                }
                LOGGER.warn("自动清晰度(已登录): 未在偏好列表中找到匹配项，回退到API最高画质。");
                return new StreamSelection(streams.getJSONObject(0), formats.getJSONObject(0).getString("new_description"));
            } else {
                LOGGER.info("用户未登录，尝试锁定至 360P 画质。");
                String targetQuality = "360P 流畅";
                Integer targetId = availableQualityMap.get(targetQuality);
                if (targetId != null) {
                    JSONObject stream = findStreamByIdAndCodec(streams, targetId);
                    if (stream != null) {
                        LOGGER.info("自动清晰度(未登录): 成功锁定到 '{}'", targetQuality);
                        return new StreamSelection(stream, targetQuality);
                    }
                }
                LOGGER.warn("自动清晰度(未登录): 未找到 '{}'，回退到最低画质。", targetQuality);
                String lowestQualityDesc = formats.getJSONObject(formats.length() - 1).getString("new_description");
                return new StreamSelection(streams.getJSONObject(streams.length() - 1), lowestQualityDesc);
            }
        }

        // --- 手动指定清晰度逻辑 ---
        Integer targetQualityId = availableQualityMap.get(desiredQuality);
        if (targetQualityId != null) {
            JSONObject stream = findStreamByIdAndCodec(streams, targetQualityId);
            if (stream != null) {
                return new StreamSelection(stream, desiredQuality);
            }
        }

        LOGGER.warn("未找到指定的清晰度 '{}'，将使用最高可用清晰度。", desiredQuality);
        String highestQualityDesc = formats.getJSONObject(0).getString("new_description");
        return new StreamSelection(streams.getJSONObject(0), highestQualityDesc);
    }

    // 辅助方法，用于根据ID查找流并选择最佳编码
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
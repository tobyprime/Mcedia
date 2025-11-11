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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiliBiliVideoFetcher {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BiliBiliVideoFetcher.class);
    private static final int QUALITY_ID_4K = 120;

    private static Supplier<Boolean> authStatusSupplier = () -> false;

    public static void setAuthStatusSupplier(Supplier<Boolean> supplier) {
        if (supplier != null) {
            authStatusSupplier = supplier;
        }
    }

    private static int parsePNumberFromUrl(String url) {
        try {
            Pattern pattern = Pattern.compile("[?&]p=(\\d+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) { /* 忽略解析错误 */ }
        return 1;
    }

    private static String parseBvidFromUrl(String url) {
        Pattern pattern = Pattern.compile("(BV[a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static VideoInfo fetch(String videoUrl, @Nullable String cookie, String desiredQuality) throws Exception {
        String bvid = parseBvidFromUrl(videoUrl);
        if (bvid == null) {
            throw new IllegalArgumentException("未找到BV号，请检查视频链接");
        }
        int page = parsePNumberFromUrl(videoUrl);

        String viewApi = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        HttpRequest viewRequest = HttpRequest.newBuilder().uri(URI.create(viewApi)).header("User-Agent", "Mozilla/5.0").build();
        HttpResponse<String> viewResponse = client.send(viewRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject viewJson = new JSONObject(viewResponse.body());

        String mainTitle = "未知标题";
        String author = "未知作者";
        String partName = null;
        String cid = null;

        if (viewJson.optInt("code") == 0) {
            JSONObject viewData = viewJson.getJSONObject("data");
            mainTitle = viewData.getString("title");
            author = viewData.getJSONObject("owner").getString("name");

            JSONArray pagesArray = viewData.optJSONArray("pages");
            if (pagesArray != null && pagesArray.length() > 1) { // [关键修正] 只有当分P数 > 1 时才处理
                if (page > 0 && page <= pagesArray.length()) {
                    JSONObject currentPageData = pagesArray.getJSONObject(page - 1);
                    cid = String.valueOf(currentPageData.getLong("cid"));
                    partName = currentPageData.getString("part");
                    if (partName.equals(mainTitle)) {
                        partName = null;
                    }
                } else {
                    // 如果指定的 P 不存在，就默认播放 P1
                    page = 1;
                    JSONObject currentPageData = pagesArray.getJSONObject(0);
                    cid = String.valueOf(currentPageData.getLong("cid"));
                    partName = currentPageData.getString("part");
                }
            } else { // 单P视频
                cid = String.valueOf(viewData.getLong("cid"));
                partName = null; // 确保单P视频的 partName 为 null
                page = 1; // 确保单P视频的 page 为 1
            }
    } else {
            throw new RuntimeException("获取视频信息失败: " + viewJson.optString("message"));
        }

        if (cid == null) {
            throw new RuntimeException("无法确定视频的CID，可能是分P号错误或API已更改。");
        }

        String playApi = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                "&cid=" + cid + "&fnval=4048"; // 移除了多余的127
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
            String message = playJson.optString("message");
            if (playJson.getInt("code") == -10403) {
                throw new BilibiliAuthRequiredException("该内容需要大会员或已登录。API返回了预览内容。");
            }
            throw new RuntimeException("获取视频播放地址失败: " + message);
        }

        JSONObject data = playJson.getJSONObject("data");

        if (data.has("dash")) {
            JSONObject dash = data.getJSONObject("dash");
            if (dash.has("video") && dash.has("audio") && dash.getJSONArray("video").length() > 0 && dash.getJSONArray("audio").length() > 0) {
                JSONObject selectedVideo = findBestStream(dash.getJSONArray("video"), data.optJSONArray("support_formats"), desiredQuality);
                JSONObject selectedAudio = findBestStream(dash.getJSONArray("audio"), null, "自动");

                if (selectedVideo != null && selectedAudio != null) {
                    String videoBaseUrl = selectedVideo.getString("baseUrl");
                    String audioBaseUrl = selectedAudio.getString("baseUrl");
                    return new VideoInfo(videoBaseUrl, audioBaseUrl, mainTitle, author, null, partName, page);
                }
            }
        }

        if (data.has("durl")) {
            JSONArray durlArray = data.getJSONArray("durl");
            if (durlArray.length() > 0) {
                String url = durlArray.getJSONObject(0).getString("url");
                LOGGER.warn("未找到DASH流，可能为会员内容。正在尝试播放试看片段 (DURL)。");
                return new VideoInfo(url, null, mainTitle, author, null, partName, page);
            }
        }

        throw new BilibiliAuthRequiredException("该视频需要登录或大会员，且没有提供试看片段。");
    }

    private static JSONObject findBestStream(JSONArray streams, @Nullable JSONArray formats, String desiredQuality) {
        if (streams == null || streams.length() == 0) {
            return null;
        }

        boolean isLoggedIn = authStatusSupplier.get();

        // --- 自动清晰度逻辑 ---
        if ("自动".equals(desiredQuality)) {
            if (formats == null) {
                return streams.getJSONObject(0);
            }
            Map<String, Integer> availableQualityMap = new HashMap<>();
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.getJSONObject(i);
                availableQualityMap.put(format.getString("new_description"), format.getInt("quality"));
            }
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
                            return stream;
                        }
                    }
                }
                LOGGER.warn("自动清晰度(已登录): 未在偏好列表中找到匹配项，回退到API最高画质。");
                return streams.getJSONObject(0);
            } else {
                LOGGER.info("用户未登录，尝试锁定至 360P 画质。");
                String targetQuality = "360P 流畅";
                Integer targetId = availableQualityMap.get(targetQuality);
                if (targetId != null) {
                    JSONObject stream = findStreamByIdAndCodec(streams, targetId);
                    if (stream != null) {
                        LOGGER.info("自动清晰度(未登录): 成功锁定到 '{}'", targetQuality);
                        return stream;
                    }
                }
                LOGGER.warn("自动清晰度(未登录): 未找到 '{}'，回退到最低画质。", targetQuality);
                return streams.getJSONObject(streams.length() - 1);
            }
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
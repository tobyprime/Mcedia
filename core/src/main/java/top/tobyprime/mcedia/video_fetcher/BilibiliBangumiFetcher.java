package top.tobyprime.mcedia.video_fetcher;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.BilibiliAuthRequiredException;
import top.tobyprime.mcedia.provider.VideoInfo;
import top.tobyprime.mcedia.provider.QualityInfo;
import top.tobyprime.mcedia.provider.BilibiliBangumiInfo;
import top.tobyprime.mcedia.video_fetcher.BilibiliStreamUtils.StreamSelection;

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

    private static Supplier<Boolean> authStatusSupplier = () -> false;

    public static void setAuthStatusSupplier(Supplier<Boolean> supplier) {
        if (supplier != null) {
            authStatusSupplier = supplier;
        }
    }

    public static BilibiliBangumiInfo fetch(String bangumiUrl, @Nullable String cookie, String desiredQuality) throws Exception {
        String epId = null;
        String ssId = null;

        Pattern epPattern = Pattern.compile("/ep(\\d+)");
        Matcher epMatcher = epPattern.matcher(bangumiUrl);
        if (epMatcher.find()) {
            epId = epMatcher.group(1);
        }

        Pattern ssPattern = Pattern.compile("/ss(\\d+)");
        Matcher ssMatcher = ssPattern.matcher(bangumiUrl);
        if (ssMatcher.find()) {
            ssId = ssMatcher.group(1);
        }
        if (epId == null && ssId == null) {
            throw new IllegalArgumentException("链接中未找到ep号或ss号");
        }

        String viewApi = (epId != null)
                ? "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epId
                : "https://api.bilibili.com/pgc/view/web/season?season_id=" + ssId;

        HttpRequest viewRequest = HttpRequest.newBuilder().uri(URI.create(viewApi)).header("User-Agent", "Mozilla/5.0").build();
        HttpResponse<String> viewResponse = client.send(viewRequest, HttpResponse.BodyHandlers.ofString());
        JSONObject viewJson = new JSONObject(viewResponse.body());

        if (viewJson.optInt("code") != 0) {
            throw new RuntimeException("获取番剧信息失败: " + viewJson.optString("message"));
        }

        JSONObject resultData = viewJson.getJSONObject("result");

        int pNumFromUrl = parsePNumberFromUrl(bangumiUrl);
        if (epId == null) {
            JSONArray episodes = resultData.getJSONArray("episodes");
            int targetIndex = (pNumFromUrl > 0 && pNumFromUrl <= episodes.length()) ? pNumFromUrl - 1 : 0;
            if (!episodes.isEmpty()) {
                epId = String.valueOf(episodes.getJSONObject(targetIndex).getInt("id"));
                LOGGER.info("检测到 ssId 链接，从第 {} 集 (ep{}) 开始播放。", targetIndex + 1, epId);
            } else {
                throw new RuntimeException("该番剧系列下没有找到任何剧集。");
            }
        }

        BilibiliBangumiInfo bangumiInfo = BilibiliBangumiInfo.fromJson(resultData, epId);
        String cid = bangumiInfo.getCurrentEpisode().cid;

        String playApi = "https://api.bilibili.com/pgc/player/web/playurl?ep_id=" + epId +
                "&cid=" + cid + "&fnval=4048";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(playApi))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.bilibili.com/");

        if (cookie != null && !cookie.isEmpty()) {
            requestBuilder.header("Cookie", cookie);
        }

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        JSONObject responseJson = new JSONObject(responseBody);

        if (responseJson.getInt("code") != 0) {
            throw new RuntimeException("获取番剧播放地址失败: " + responseJson.optString("message"));
        }

        JSONObject result = responseJson.optJSONObject("result");
        if (result == null) {
            throw new RuntimeException("B站API未返回有效的 result 数据：" + responseJson.optString("message", "未知错误"));
        }

        if (result.has("is_preview") && result.getInt("is_preview") == 1) {
            throw new BilibiliAuthRequiredException("该内容需要大会员或已登录。API返回了预览内容。");
        }
        if (result.has("code") && result.getInt("code") == -10403) {
            throw new BilibiliAuthRequiredException("B站返回权限错误(-10403)，该内容需要大会员或已登录。");
        }

        List<QualityInfo> availableQualities = new ArrayList<>();
        JSONArray supportFormats = result.optJSONArray("support_formats");
        if (supportFormats != null) {
            for (int i = 0; i < supportFormats.length(); i++) {
                JSONObject format = supportFormats.getJSONObject(i);
                availableQualities.add(new QualityInfo(format.getString("new_description")));
            }
        }

        String finalCurrentQuality = null;

        if (result.has("dash")) {
            JSONObject dash = result.getJSONObject("dash");
            if (dash.has("video") && dash.has("audio") && !dash.getJSONArray("video").isEmpty() && !dash.getJSONArray("audio").isEmpty()) {
                StreamSelection videoSelection = BilibiliStreamUtils.findBestStream(dash.getJSONArray("video"), supportFormats, desiredQuality, authStatusSupplier);
                StreamSelection audioSelection = BilibiliStreamUtils.findBestStream(dash.getJSONArray("audio"), null, "自动", authStatusSupplier);

                if (videoSelection != null && videoSelection.stream != null && audioSelection != null) {
                    finalCurrentQuality = videoSelection.qualityDescription;
                    String videoBaseUrl = videoSelection.stream.getString("baseUrl");
                    String audioBaseUrl = audioSelection.stream.getString("baseUrl");

                    // 将解析出的 VideoInfo 存入 BangumiInfo 对象
                    bangumiInfo.setVideoInfo(new VideoInfo(videoBaseUrl, audioBaseUrl, bangumiInfo.title, "BiliBili", null,
                            bangumiInfo.getCurrentEpisode().title, bangumiInfo.getCurrentEpisodeIndex() + 1,
                            availableQualities, finalCurrentQuality, true, Long.parseLong(cid)));
                    return bangumiInfo;
                }
            }
        }

        if (result.has("durl")) {
            JSONArray durlArray = result.getJSONArray("durl");
            if (durlArray.length() > 0) {
                if (supportFormats != null && !supportFormats.isEmpty()) {
                    finalCurrentQuality = supportFormats.getJSONObject(0).getString("new_description");
                }
                String playableUrl = durlArray.getJSONObject(0).getString("url");
                LOGGER.warn("未找到DASH流，可能为会员内容。正在尝试播放DURL流。");

                bangumiInfo.setVideoInfo(new VideoInfo(playableUrl, null, bangumiInfo.title, "BiliBili", null,
                        bangumiInfo.getCurrentEpisode().title, bangumiInfo.getCurrentEpisodeIndex() + 1,
                        availableQualities, finalCurrentQuality, true, Long.parseLong(cid)));
                return bangumiInfo;
            }
        }

        throw new RuntimeException("未能从番剧API响应中找到可用的视频流。API Response: " + responseBody);
    }

    private static int parsePNumberFromUrl(String url) {
        try {
            Pattern pattern = Pattern.compile("[?&]p=(\\d+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
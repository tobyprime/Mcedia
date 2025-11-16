package top.tobyprime.mcedia.video_fetcher;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.BilibiliAuthRequiredException;
import top.tobyprime.mcedia.provider.QualityInfo;
import top.tobyprime.mcedia.provider.VideoInfo;
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

public class BiliBiliVideoFetcher {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BiliBiliVideoFetcher.class);

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
        long cid = 0;
        boolean isMultiPart = false;

        if (viewJson.optInt("code") == 0) {
            JSONObject viewData = viewJson.getJSONObject("data");
            mainTitle = viewData.getString("title");
            author = viewData.getJSONObject("owner").getString("name");

            JSONArray pagesArray = viewData.optJSONArray("pages");
            if (pagesArray != null && pagesArray.length() > 1) {
                isMultiPart = true;
                if (page > 0 && page <= pagesArray.length()) {
                    JSONObject currentPageData = pagesArray.getJSONObject(page - 1);
                    cid = currentPageData.getLong("cid");
                    partName = currentPageData.getString("part");
                    if (partName.equals(mainTitle)) {
                        partName = null;
                    }
                } else {
                    page = 1;
                    JSONObject currentPageData = pagesArray.getJSONObject(0);
                    cid = currentPageData.getLong("cid");
                    partName = currentPageData.getString("part");
                }
            } else {
                cid = viewData.getLong("cid");
                partName = null;
                page = 1;
            }
    } else {
            throw new RuntimeException("获取视频信息失败: " + viewJson.optString("message"));
        }

        if (cid == 0) {
            throw new RuntimeException("无法确定视频的CID，可能是分P号错误或API已更改。");
        }

        String playApi = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                "&cid=" + cid + "&fnval=4048";
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

        List<QualityInfo> availableQualities = new ArrayList<>();
        JSONArray supportFormats = data.optJSONArray("support_formats");
        if (supportFormats != null) {
            for (int i = 0; i < supportFormats.length(); i++) {
                JSONObject format = supportFormats.getJSONObject(i);
                availableQualities.add(new QualityInfo(format.getString("new_description")));
            }
        }

        String finalCurrentQuality = null;

        if (data.has("dash")) {
            JSONObject dash = data.getJSONObject("dash");
            if (dash.has("video") && dash.has("audio") && dash.getJSONArray("video").length() > 0 && dash.getJSONArray("audio").length() > 0) {
                StreamSelection videoSelection = BilibiliStreamUtils.findBestStream(dash.getJSONArray("video"), supportFormats, desiredQuality, authStatusSupplier);
                StreamSelection audioSelection = BilibiliStreamUtils.findBestStream(dash.getJSONArray("audio"), null, "自动", authStatusSupplier);

                if (videoSelection != null && videoSelection.stream != null && audioSelection != null) {
                    finalCurrentQuality = videoSelection.qualityDescription;
                    String videoBaseUrl = videoSelection.stream.getString("baseUrl");
                    String audioBaseUrl = audioSelection.stream.getString("baseUrl");
                    return new VideoInfo(videoBaseUrl, audioBaseUrl, mainTitle, author, null, partName, page, availableQualities, finalCurrentQuality, isMultiPart, cid);
                }
            }
        }

        if (data.has("durl")) {
            JSONArray durlArray = data.getJSONArray("durl");
            if (durlArray.length() > 0) {
                if (supportFormats != null && !supportFormats.isEmpty()) {
                    finalCurrentQuality = supportFormats.getJSONObject(0).getString("new_description");
                }
                String url = durlArray.getJSONObject(0).getString("url");
                LOGGER.warn("未找到DASH流，可能为会员内容。正在尝试播放试看片段 (DURL)。");
                return new VideoInfo(url, null, mainTitle, author, null, partName, page, availableQualities, finalCurrentQuality, isMultiPart, cid);
            }
        }

        throw new BilibiliAuthRequiredException("该视频需要登录或大会员，且没有提供试看片段。");
    }
}
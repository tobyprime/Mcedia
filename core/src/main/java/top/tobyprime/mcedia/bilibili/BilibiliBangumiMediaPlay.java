package top.tobyprime.mcedia.bilibili;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.BaseMediaPlay;
import top.tobyprime.mcedia.core.IMediaPlay;
import top.tobyprime.mcedia.media_play_resolvers.MediaPlayFactory;
import top.tobyprime.mcedia.video_fetcher.MediaInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BilibiliBangumiMediaPlay extends BaseMediaPlay implements BilibiliAccountStatusUpdateEventHandler {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliBangumiMediaPlay.class);

    private static Supplier<Boolean> authStatusSupplier = () -> false;
    private final String bangumiUrl;
    private boolean waitForLoginStatusUpdate;

    public BilibiliBangumiMediaPlay(String bangumiUrl) {
        BilibiliAuthManager.getInstance().AddStatusUpdateHandler(this);
        this.bangumiUrl = bangumiUrl;
        CompletableFuture.runAsync(this::load, MediaPlayFactory.EXECUTOR);
    }

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
        } catch (Exception ignored) {
        }
        return 0;
    }

    public void load() {
        loading = true;
        try {
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
                setStatus("链接中未找到ep号或ss号");
                return;
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
                    setStatus("该番剧系列下没有找到任何剧集。");
                    return;
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
            var cookie = BilibiliConfigs.getCookie();
            if (cookie != null && !cookie.isEmpty()) {
                requestBuilder.header("Cookie", cookie);
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            JSONObject responseJson = new JSONObject(responseBody);

            if (responseJson.getInt("code") != 0) {
                setStatus("获取番剧播放地址失败: " + responseJson.optString("message"));
                return;
            }

            JSONObject result = responseJson.optJSONObject("result");
            if (result == null) {
                setStatus("B站API未返回有效的 result 数据：" + responseJson.optString("message", "未知错误"));
                return;
            }

            if (result.has("is_preview") && result.getInt("is_preview") == 1) {
                setStatus("该内容需要大会员或已登录。API返回了预览内容。");
                waitForLoginStatusUpdate = true;
                return;
            }
            if (result.has("code") && result.getInt("code") == -10403) {
                setStatus("B站返回权限错误(-10403)，该内容需要大会员或已登录。");
                waitForLoginStatusUpdate = true;
                return;
            }

            JSONArray supportFormats = result.optJSONArray("support_formats");

            var mediaInfo = new MediaInfo();

            mediaInfo.title = bangumiInfo.title + " - " + bangumiInfo.getCurrentEpisode().title;
            mediaInfo.platform = "Bilibili Bangumi";
            mediaInfo.cookie = BilibiliConfigs.getCookie();
            mediaInfo.author = "Bilibili";
            var headers = new HashMap<String, String>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.put("Referer", "https://www.bilibili.com/");
            headers.put("Origin" , "https://www.bilibili.com");
            mediaInfo.headers = headers;


            if (result.has("dash")) {
                JSONObject dash = result.getJSONObject("dash");
                if (dash.has("video") && dash.has("audio") && !dash.getJSONArray("video").isEmpty() && !dash.getJSONArray("audio").isEmpty()) {
                    BilibiliStreamSelection videoSelection = BilibiliHelper.findBestStream(dash.getJSONArray("video"), supportFormats);
                    BilibiliStreamSelection audioSelection = BilibiliHelper.findBestStream(dash.getJSONArray("audio"), null);

                    if (videoSelection != null && videoSelection.stream != null && audioSelection != null) {
                        String videoBaseUrl = videoSelection.stream.getString("baseUrl");
                        String audioBaseUrl = audioSelection.stream.getString("baseUrl");
                        mediaInfo.streamUrl = videoBaseUrl;
                        mediaInfo.audioUrl = audioBaseUrl;


                        setStatus("加载完成");
                        setMediaInfo(mediaInfo);
                        return;
                    }
                }
            }

            if (result.has("durl")) {
                JSONArray durlArray = result.getJSONArray("durl");
                if (!durlArray.isEmpty()) {
                    if (supportFormats != null && !supportFormats.isEmpty()) {
                        supportFormats.getJSONObject(0).getString("new_description");
                    }
                    String playableUrl = durlArray.getJSONObject(0).getString("url");
                    LOGGER.warn("未找到DASH流，可能为会员内容。正在尝试播放DURL流。");
                    mediaInfo.streamUrl = playableUrl;
                    setMediaInfo(mediaInfo);
                    return;
                }
            }

            throw new RuntimeException("未能从番剧API响应中找到可用的视频流。API Response: " + responseBody);
        } catch (Throwable e) {
            setStatus("加载失败");
            return;
        } finally {
            loading = false;
        }
    }

    @Override
    public void OnAccountStatusUpdated(BilibiliAccountStatus status) {
        if (this.waitForLoginStatusUpdate) {
            this.waitForLoginStatusUpdate = false;
            this.load();
        }
    }
}
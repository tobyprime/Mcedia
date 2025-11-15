package top.tobyprime.mcedia.bilibili;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.BaseMediaPlay;
import top.tobyprime.mcedia.core.IMediaPlay;
import top.tobyprime.mcedia.media_play_resolvers.MediaPlayFactory;
import top.tobyprime.mcedia.video_fetcher.MediaInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BilibiliVideoMediaPlay extends BaseMediaPlay implements IMediaPlay, BilibiliAccountStatusUpdateEventHandler {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliVideoMediaPlay.class);

    private volatile boolean waitForLoginStatusUpdate = false;

    private final String videoUrl;

    public BilibiliVideoMediaPlay(String videoUrl) {
        BilibiliAuthManager.getInstance().AddStatusUpdateHandler(this);
        this.videoUrl = videoUrl;
        CompletableFuture.runAsync(this::load, MediaPlayFactory.EXECUTOR);
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

    public void load() {
        loading = true;
        try {
            setStatus("正在获取视频信息...");
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
//        boolean isMultiPart = false;

            if (viewJson.optInt("code") == 0) {
                JSONObject viewData = viewJson.getJSONObject("data");
                mainTitle = viewData.getString("title");
                author = viewData.getJSONObject("owner").getString("name");

                JSONArray pagesArray = viewData.optJSONArray("pages");
                if (pagesArray != null && pagesArray.length() > 1) {
//                isMultiPart = true;
                    if (page > 0 && page <= pagesArray.length()) {
                        JSONObject currentPageData = pagesArray.getJSONObject(page - 1);
                        cid = currentPageData.getLong("cid");
                        partName = currentPageData.getString("part");
                        if (partName.equals(mainTitle)) {
                            partName = null;
                        }
                    } else {
//                    page = 1;
                        JSONObject currentPageData = pagesArray.getJSONObject(0);
                        cid = currentPageData.getLong("cid");
                        partName = currentPageData.getString("part");
                    }
                } else {
                    cid = viewData.getLong("cid");
//                page = 1;
                }
            } else {
                setStatus("获取视频信息失败:" + viewJson.optString("message"));
                return;
            }

            if (cid == 0) {
                setStatus("无法确定视频的CID，可能是分P号错误或API已更改。");
                return;
            }

            String playApi = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                    "&cid=" + cid + "&fnval=4048";
            HttpRequest.Builder playRequestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(playApi))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.bilibili.com/");
            var cookie = BilibiliConfigs.getCookie();
            if (cookie != null && !cookie.isEmpty()) {
                playRequestBuilder.header("Cookie", cookie);
            }

            setStatus("正在获取视频播放链接...");

            HttpResponse<String> playResponse = client.send(playRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            JSONObject playJson = new JSONObject(playResponse.body());

            var info = new MediaInfo();

            if (partName != null && !partName.isEmpty()) {
                info.title = mainTitle + " - " + partName;
            } else {
                info.title = mainTitle;
            }

            info.author = author;
            info.platform = "bilibili";
            info.cookie = BilibiliConfigs.getCookie();
            var headers = new HashMap<String, String>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.put("Referer", "https://www.bilibili.com/");
            headers.put("Origin", "https://www.bilibili.com");
            info.headers = headers;

            if (playJson.getInt("code") != 0) {
                String message = playJson.optString("message");
                if (playJson.getInt("code") == -10403) {
                    waitForLoginStatusUpdate = true;
                    return;
                }
                setStatus("获取视频播放地址失败: " + message);
                return;
            }

            JSONObject data = playJson.getJSONObject("data");

            JSONArray supportFormats = data.optJSONArray("support_formats");
//        String finalCurrentQuality = null;


            if (data.has("dash")) {
                JSONObject dash = data.getJSONObject("dash");
                if (dash.has("video") && dash.has("audio") && !dash.getJSONArray("video").isEmpty() && !dash.getJSONArray("audio").isEmpty()) {
                    BilibiliStreamSelection videoSelection = BilibiliHelper.findBestStream(dash.getJSONArray("video"), supportFormats);
                    BilibiliStreamSelection audioSelection = BilibiliHelper.findBestStream(dash.getJSONArray("audio"), null);

                    if (videoSelection != null && videoSelection.stream != null && audioSelection != null) {
//                    finalCurrentQuality = videoSelection.qualityDescription;
                        String videoBaseUrl = videoSelection.stream.getString("baseUrl");
                        String audioBaseUrl = audioSelection.stream.getString("baseUrl");

                        info.streamUrl = videoBaseUrl;
                        info.audioUrl = audioBaseUrl;
                        setStatus("获取播放地址成功");
                        setMediaInfo(info);
                        return;
                    }
                }
            }

            if (data.has("durl")) {
                JSONArray durlArray = data.getJSONArray("durl");
                if (!durlArray.isEmpty()) {
//                if (supportFormats != null && !supportFormats.isEmpty()) {
//                    finalCurrentQuality = supportFormats.getJSONObject(0).getString("new_description");
//                }
                    String url = durlArray.getJSONObject(0).getString("url");
                    LOGGER.warn("未找到DASH流，可能为会员内容。正在尝试播放试看片段 (DURL)。");
                    setStatus("获取试看地址成功");
                    info.streamUrl = url;
                    return;
                }
            }

            waitForLoginStatusUpdate = true;
        } catch (Exception e) {
            setStatus("加载失败" + e.getMessage());
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

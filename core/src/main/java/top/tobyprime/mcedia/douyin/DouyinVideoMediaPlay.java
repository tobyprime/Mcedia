package top.tobyprime.mcedia.douyin;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.BaseMediaPlay;
import top.tobyprime.mcedia.media_play_resolvers.MediaPlayFactory;
import top.tobyprime.mcedia.core.MediaInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DouyinVideoMediaPlay extends BaseMediaPlay {

    private static final Logger logger = LoggerFactory.getLogger(DouyinVideoMediaPlay.class);

    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1";

    public String sharedLink;

    public DouyinVideoMediaPlay(String sharedLink){
        this.sharedLink = sharedLink;
        CompletableFuture.runAsync(this::load, MediaPlayFactory.EXECUTOR);

    }
    public static String getSharedUrl(String share) {
        if (share == null) {
            return null;
        }
        String regex = "(https://v\\.douyin\\.com/[A-Za-z0-9_]+/?)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(share);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public void load() {
        loading = true;
        try {
            var shareUrl = getSharedUrl(sharedLink);
            String videoPageUrl;
            HttpResponse<String> videoPageResponse;
            try (HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS) // 自动重定向
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()) {

                // 第一次请求，拿到重定向后的真实URL
                HttpRequest firstRequest = HttpRequest.newBuilder()
                        .uri(URI.create(getSharedUrl(sharedLink)))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<Void> firstResponse = client.send(firstRequest, HttpResponse.BodyHandlers.discarding());

                if (firstResponse.statusCode() / 100 != 2) {
                    logger.error("请求分享链接失败，code: {}, url: {}", firstResponse.statusCode(), shareUrl);
                    return;
                }

                // 获取重定向后的最终URL
                URI finalUri = firstResponse.uri();
                String finalUrl = finalUri.toString();

                // 提取视频ID
                String videoId = extractVideoId(finalUrl);
                if (videoId == null) {
                    logger.error("无法从URL中提取视频ID: {}", finalUrl);
                    return;
                }

                videoPageUrl = "https://www.iesdouyin.com/share/video/" + videoId;

                // 请求视频详情页
                HttpRequest videoPageRequest = HttpRequest.newBuilder()
                        .uri(URI.create(videoPageUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                videoPageResponse = client.send(videoPageRequest, HttpResponse.BodyHandlers.ofString());
            }

            if (videoPageResponse.statusCode() / 100 != 2) {
                logger.error("请求视频详情页失败，code: {}, url: {}", videoPageResponse.statusCode(), videoPageUrl);
                setStatus("请求视频失败");
                return;
            }

            String html = videoPageResponse.body();

            // 提取 JSON 数据
            String routerDataJsonStr = extractRouterDataJson(html);
            if (routerDataJsonStr == null) {
                logger.error("无法从HTML中提取 _ROUTER_DATA JSON");
                setStatus("请求视频失败");
                return;
            }

            // 解析并返回无水印视频地址
            var info = getMediaInfo(routerDataJsonStr);
            if (info == null) {
                setStatus("读取视频信息失败");
                return;
            }
            info.headers = new HashMap<>();

            info.headers.put("User-Agent", USER_AGENT);
            info.headers.put("Referer", "https://www.douyin.com/");
            info.headers.put("Origin", "https://www.douyin.com/");

            setMediaInfo(info);
            setStatus("获取视频信息完成");
            return;

        } catch (IOException | InterruptedException e) {
            logger.error("网络请求异常", e);
            setStatus("网络请求异常");
            return;
        } catch (Exception e) {
            logger.error("解析异常", e);
            setStatus("解析异常");
            return;
        }
        finally {
            loading = false;
        }
    }

    private static String extractVideoId(String url) {
        try {
            String[] parts = url.split("\\?");
            String path = parts[0];
            String[] segments = path.split("/");
            if (segments.length > 0) {
                String last = segments[segments.length - 1];
                if (last.isEmpty() && segments.length > 1) {
                    return segments[segments.length - 2];
                }
                return last;
            }
        } catch (Exception e) {
            logger.warn("提取视频ID异常", e);
        }
        return null;
    }

    private static String extractRouterDataJson(String html) {
        Pattern pattern = Pattern.compile("window\\._ROUTER_DATA\\s*=\\s*(\\{.*?})</script>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private  MediaInfo getMediaInfo(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject loaderData = root.getJSONObject("loaderData");
            JSONObject videoPage = loaderData.getJSONObject("video_(id)/page");
            JSONObject videoInfoRes = videoPage.getJSONObject("videoInfoRes");
            JSONObject item = videoInfoRes.getJSONArray("item_list").getJSONObject(0);
            JSONObject video = item.getJSONObject("video");
            JSONObject playAddr = video.getJSONObject("play_addr");
            String url = playAddr.getJSONArray("url_list").getString(0);

            var mediaInfo = new MediaInfo();
            mediaInfo.title = item.getString("desc");
            mediaInfo.author = item.getJSONObject("author").getString("nickname");
            mediaInfo.platform = "抖音";
            mediaInfo.rawUrl = this.sharedLink;
            mediaInfo.streamUrl = url.replace("playwm", "play");
            return mediaInfo;
        } catch (Exception e) {
            logger.error("解析视频URL异常", e);
            return null;
        }
    }
}

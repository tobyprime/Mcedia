package top.tobyprime.mcedia.video_fetcher;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DouyinVideoFetcher {

    private static final Logger logger = LoggerFactory.getLogger(DouyinVideoFetcher.class);

    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1";

    public static String getSharedUrl(String share){
        if (share == null){return null;}
        String regex = "(https://v\\.douyin\\.com/[A-Za-z0-9_]+/?)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(share);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public static String fetch(String shareUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(Redirect.ALWAYS) // 自动重定向
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            // 第一次请求，拿到重定向后的真实URL
            HttpRequest firstRequest = HttpRequest.newBuilder()
                    .uri(URI.create(shareUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<Void> firstResponse = client.send(firstRequest, HttpResponse.BodyHandlers.discarding());

            if (firstResponse.statusCode() / 100 != 2) {
                logger.error("请求分享链接失败，code: {}, url: {}", firstResponse.statusCode(), shareUrl);
                return null;
            }

            // 获取重定向后的最终URL
            URI finalUri = firstResponse.uri();
            String finalUrl = finalUri.toString();

            // 提取视频ID
            String videoId = extractVideoId(finalUrl);
            if (videoId == null) {
                logger.error("无法从URL中提取视频ID: {}", finalUrl);
                return null;
            }

            String videoPageUrl = "https://www.iesdouyin.com/share/video/" + videoId;

            // 请求视频详情页
            HttpRequest videoPageRequest = HttpRequest.newBuilder()
                    .uri(URI.create(videoPageUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> videoPageResponse = client.send(videoPageRequest, BodyHandlers.ofString());

            if (videoPageResponse.statusCode() / 100 != 2) {
                logger.error("请求视频详情页失败，code: {}, url: {}", videoPageResponse.statusCode(), videoPageUrl);
                return null;
            }

            String html = videoPageResponse.body();

            // 提取 JSON 数据
            String routerDataJsonStr = extractRouterDataJson(html);
            if (routerDataJsonStr == null) {
                logger.error("无法从HTML中提取 _ROUTER_DATA JSON");
                return null;
            }

            // 解析并返回无水印视频地址
            return parseVideoUrl(routerDataJsonStr);

        } catch (IOException | InterruptedException e) {
            logger.error("网络请求异常", e);
            return null;
        } catch (Exception e) {
            logger.error("解析异常", e);
            return null;
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
        Pattern pattern = Pattern.compile("window\\._ROUTER_DATA\\s*=\\s*(\\{.*?\\})</script>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private static String parseVideoUrl(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject loaderData = root.getJSONObject("loaderData");
            JSONObject videoPage = loaderData.getJSONObject("video_(id)/page");
            JSONObject videoInfoRes = videoPage.getJSONObject("videoInfoRes");
            JSONObject item = videoInfoRes.getJSONArray("item_list").getJSONObject(0);
            JSONObject video = item.getJSONObject("video");
            JSONObject playAddr = video.getJSONObject("play_addr");
            String url = playAddr.getJSONArray("url_list").getString(0);
            return url.replace("playwm", "play");
        } catch (Exception e) {
            logger.error("解析视频URL异常", e);
            return null;
        }
    }
}

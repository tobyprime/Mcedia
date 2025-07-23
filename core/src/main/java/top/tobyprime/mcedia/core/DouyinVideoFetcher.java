package top.tobyprime.mcedia.core;

import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DouyinVideoFetcher {

    private static final Logger logger = LoggerFactory.getLogger(DouyinVideoFetcher.class);

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();

    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1";

    public static String fetch(String shareUrl) {
        try {
            // 第一次请求，拿到重定向后的真实URL
            Request firstRequest = new Request.Builder()
                    .url(shareUrl)
                    .header("User-Agent", USER_AGENT)
                    .build();

            Response firstResponse = client.newCall(firstRequest).execute();
            if (!firstResponse.isSuccessful()) {
                logger.error("请求分享链接失败，code: {}, url: {}", firstResponse.code(), shareUrl);
                return null;
            }
            String finalUrl = firstResponse.request().url().toString();
            logger.debug("最终跳转链接: {}", finalUrl);

            // 提取视频ID
            String videoId = extractVideoId(finalUrl);
            if (videoId == null) {
                logger.error("无法从URL中提取视频ID: {}", finalUrl);
                return null;
            }

            String videoPageUrl = "https://www.iesdouyin.com/share/video/" + videoId;

            // 请求视频详情页
            Request videoPageRequest = new Request.Builder()
                    .url(videoPageUrl)
                    .header("User-Agent", USER_AGENT)
                    .build();

            Response videoPageResponse = client.newCall(videoPageRequest).execute();
            if (!videoPageResponse.isSuccessful()) {
                logger.error("请求视频详情页失败，code: {}, url: {}", videoPageResponse.code(), videoPageUrl);
                return null;
            }
            String html = videoPageResponse.body().string();

            // 提取 JSON 数据
            String routerDataJsonStr = extractRouterDataJson(html);
            if (routerDataJsonStr == null) {
                logger.error("无法从HTML中提取 _ROUTER_DATA JSON");
                return null;
            }

            // 解析并返回无水印视频地址
            return parseVideoUrl(routerDataJsonStr);

        } catch (IOException e) {
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

    // 仅测试用
    public static void main(String[] args) {
        String testUrl = "https://v.douyin.com/4xrQAmlpYYs/";
        String videoUrl = fetch(testUrl);
        if (videoUrl != null) {
            System.out.println("无水印视频地址: " + videoUrl);
        } else {
            System.out.println("未能获取视频地址");
        }
    }
}

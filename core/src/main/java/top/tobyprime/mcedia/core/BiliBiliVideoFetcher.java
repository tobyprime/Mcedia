package top.tobyprime.mcedia.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiliBiliVideoFetcher {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static String fetch(String videoUrl) {
        // 1. 提取 BV 号
        Pattern bvPattern = Pattern.compile("(BV[0-9A-Za-z]+)");
        Matcher matcher = bvPattern.matcher(videoUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("未找到BV号，请检查视频链接");
        }
        String bvid = matcher.group(1);

        // 2. 获取 CID
        String pagelistApi = "https://api.bilibili.com/x/player/pagelist?bvid=" + bvid + "&jsonp=jsonp";
        HttpRequest pagelistRequest = HttpRequest.newBuilder()
                .uri(URI.create(pagelistApi))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> pagelistResponse = null;
        try {
            pagelistResponse = client.send(pagelistRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            return null;
        }
        JSONObject pagelistJson = new JSONObject(pagelistResponse.body());
        if (pagelistJson.getInt("code") != 0) {
            throw new RuntimeException("获取 CID 失败: " + pagelistJson);
        }
        JSONArray pages = pagelistJson.getJSONArray("data");
        String cid = pages.getJSONObject(0).get("cid").toString();

        System.out.println("[INFO] BV号: " + bvid + " | CID: " + cid);

        // 3. 获取播放地址 (qn=112 -> 1080P+)
        String playApi = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                "&cid=" + cid + "&qn=112&type=&otype=json&platform=html5&high_quality=1";
        HttpRequest playRequest = HttpRequest.newBuilder()
                .uri(URI.create(playApi))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", videoUrl)
                .GET()
                .build();

        HttpResponse<String> playResponse = null;
        try {
            playResponse = client.send(playRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            return null;
        }
        JSONObject playJson = new JSONObject(playResponse.body());
        if (playJson.getInt("code") != 0) {
            throw new RuntimeException("获取视频播放地址失败: " + playJson);
        }

        JSONArray durlArray = playJson.getJSONObject("data").getJSONArray("durl");

        return durlArray.getJSONObject(0).getString("url");
    }

}

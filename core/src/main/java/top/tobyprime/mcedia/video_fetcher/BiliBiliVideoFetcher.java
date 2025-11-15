package top.tobyprime.mcedia.video_fetcher;

import org.json.JSONArray;
import org.json.JSONObject;
import top.tobyprime.mcedia.interfaces.IMediaFetcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiliBiliVideoFetcher implements IMediaFetcher {

    HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS) // 关键
            .build();

    @Override
    public boolean isValidUrl(String url) {
        return url.contains("www.bilibili.com/video/BV");
    }

    public void getTitleAndAuthor(String url, MediaInfo mediaInfo, HttpClient client) throws IOException, InterruptedException {
        HttpRequest initRequest = HttpRequest.newBuilder()
                .uri(URI.create(url.split("/\\?")[0]))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36")
                .header("Accept-Charset", "UTF-8")

                .GET()
                .build();
        HttpResponse<String> initResponse = client.send(initRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var body = initResponse.body();
        try{
            String content = body.replaceAll(".*?<title[^>]*>(.*?)</title>.*", "$1");
            var vars = content.split("_");
            mediaInfo.title = vars[0];
        }
        catch(Exception ignored){
        }
    }

    @Override
    public MediaInfo getMedia(String videoUrl) {
        // 1. 提取 BV 号
        var media = new MediaInfo();
        media.platform = "BiliBili";
        media.rawUrl = videoUrl;

        Pattern bvPattern = Pattern.compile("(BV[0-9A-Za-z]+)");
        Matcher matcher = bvPattern.matcher(videoUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("未找到BV号，请检查视频链接");
        }
        String bvid = matcher.group(1);

        Pattern pagePattern = Pattern.compile("[?&]p=(\\d+)");
        Matcher pageMatcher = pagePattern.matcher(videoUrl);
        int page = 1;
        if (pageMatcher.find()) {
            page = Integer.parseInt(pageMatcher.group(1));
        }
        try{
            getTitleAndAuthor(videoUrl, media, client);
        }
        catch(Exception ignored){

        }
        // 2. 获取 CID
        String pagelistApi = "https://api.bilibili.com/x/player/pagelist?bvid=" + bvid + "&jsonp=jsonp";
        HttpRequest pagelistRequest = HttpRequest.newBuilder()
                .uri(URI.create(pagelistApi))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> pagelistResponse;
        try {
            pagelistResponse = client.send(pagelistRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("获取视频播放地址失败", e);
        }
        JSONObject pagelistJson = new JSONObject(pagelistResponse.body());
        if (pagelistJson.getInt("code") != 0) {
            throw new RuntimeException("获取 CID 失败: " + pagelistJson);
        }
        JSONArray pages = pagelistJson.getJSONArray("data");

        String cid = pages.getJSONObject(page-1).get("cid").toString();


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
            throw new RuntimeException("获取视频播放地址失败", e);
        }
        JSONObject playJson = new JSONObject(playResponse.body());
        if (playJson.getInt("code") != 0) {
            throw new RuntimeException("获取视频播放地址失败: " + playJson);
        }

        JSONArray durlArray = playJson.getJSONObject("data").getJSONArray("durl");
        media.streamUrl = durlArray.getJSONObject(0).getString("url");
        return media;
    }
}

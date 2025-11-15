package top.tobyprime.mcedia.video_fetcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.*;
import top.tobyprime.mcedia.interfaces.IMediaFetcher;

public class BiliBiliLiveFetcher implements IMediaFetcher {

    private static final String USER_AGENT = "Mozilla/5.0 (iPod; CPU iPhone OS 14_5 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/87.0.4280.163 Mobile/15E148 Safari/604.1";


    /**
     * 提取直播房间号
     */
    private static String extractRoomId(String url) {
        Pattern pattern = Pattern.compile("live\\.bilibili\\.com/(\\d+)");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    public boolean isValidUrl(String url) {
        return url.contains("live.bilibili.com");
    }

    public void getTitleAndAuthor(String roomId, MediaInfo mediaInfo) throws IOException, InterruptedException {
        var url = "https://live.bilibili.com/"+roomId;
        HttpResponse<String> initResponse;
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER) // 禁止自动跳转
                .build()) {
            HttpRequest initRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            initResponse = client.send(initRequest, HttpResponse.BodyHandlers.ofString());
        }
        var body = initResponse.body();
        try{
            String content = body.replaceAll(".*?<title[^>]*>(.*?)</title>.*", "$1");
            var vars = content.split(" - ");
            mediaInfo.title = vars[0];
            mediaInfo.author =vars[1];
        }
        catch(Exception ignored){
        }
    }

    @Override
    public MediaInfo getMedia(String liveUrl) {
        try {
            var mediaInfo  = new MediaInfo();
            mediaInfo.rawUrl = liveUrl;
            mediaInfo.platform = "BiliBili";

            String roomId = extractRoomId(liveUrl);
            if (roomId == null) throw new IllegalArgumentException("无法从链接中提取房间号");

            HttpResponse<String> playResponse;
            try (HttpClient client = HttpClient.newHttpClient()) {
                getTitleAndAuthor(roomId, mediaInfo);
                // Step 1: 获取真实房间 ID 和直播状态
                String initUrl = "https://api.live.bilibili.com/room/v1/Room/room_init?id=" + roomId;
                HttpRequest initRequest = HttpRequest.newBuilder()
                        .uri(URI.create(initUrl))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
                HttpResponse<String> initResponse = client.send(initRequest, HttpResponse.BodyHandlers.ofString());
                JSONObject initJson = new JSONObject(initResponse.body());
                if ("直播间不存在".equals(initJson.getString("msg")))
                    throw new RuntimeException("直播间不存在");

                JSONObject initData = initJson.getJSONObject("data");
                if (initData.getInt("live_status") != 1)
                    throw new RuntimeException("直播未开启");

                String realRoomId = String.valueOf(initData.getInt("room_id"));

                // Step 2: 获取直播播放地址
                int qn = 10000;
                String playUrl = String.format("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo" +
                        "?room_id=%s&protocol=0,1&format=0,1,2&codec=0,1&qn=%d&platform=h5&ptype=8", realRoomId, qn);

                HttpRequest playRequest = HttpRequest.newBuilder()
                        .uri(URI.create(playUrl))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
                playResponse = client.send(playRequest, HttpResponse.BodyHandlers.ofString());
            }
            JSONObject playJson = new JSONObject(playResponse.body());

            JSONObject playurl = playJson.getJSONObject("data")
                    .getJSONObject("playurl_info")
                    .getJSONObject("playurl");
            JSONArray streams = playurl.getJSONArray("stream");

            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                JSONArray formats = stream.getJSONArray("format");
                for (int j = 0; j < formats.length(); j++) {
                    JSONObject format = formats.getJSONObject(j);
                    if (!"ts".equals(format.getString("format_name"))) continue;

                    JSONObject codec = format.getJSONArray("codec").getJSONObject(0);
                    String baseUrl = codec.getString("base_url");
                    JSONArray urlInfos = codec.getJSONArray("url_info");

                    if (!urlInfos.isEmpty()) {
                        JSONObject info = urlInfos.getJSONObject(0);
                        String host = info.getString("host");
                        String extra = info.getString("extra");
                        mediaInfo.streamUrl = host + baseUrl + extra;

                        return mediaInfo;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            throw new RuntimeException("获取 bilibili 直播失败",e);
        }
    }
}

package top.tobyprime.mcedia.video_fetcher;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.provider.QualityInfo;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiliBiliLiveFetcher {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = LoggerFactory.getLogger(BiliBiliLiveFetcher.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static class QualitySelection {
        final int qn;
        final String description;

        QualitySelection(int qn, String description) {
            this.qn = qn;
            this.description = description;
        }
    }

    public static VideoInfo fetch(String liveUrl, String desiredQuality) throws Exception {
        try {
            String roomId = extractRoomId(liveUrl);
            if (roomId == null) throw new IllegalArgumentException("无法从链接中提取房间号");

            // 获取直播间真ID
            String playInfoApiUrl = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id=" + roomId +
                    "&no_playurl=0&mask=1&qn=0&platform=web&protocol=0,1&format=0,1,2&codec=0,1,2";
            HttpRequest initRequest = HttpRequest.newBuilder().uri(URI.create(playInfoApiUrl)).header("User-Agent", USER_AGENT).build();
            HttpResponse<String> initResponse = client.send(initRequest, HttpResponse.BodyHandlers.ofString());
            JSONObject initJson = new JSONObject(initResponse.body());
            if (initJson.optInt("code") != 0 || !initJson.has("data")) {
                throw new RuntimeException("直播间不存在或API错误: " + initJson.optString("message"));
            }
            JSONObject initData = initJson.getJSONObject("data");
            if (initData.optInt("live_status") != 1) {
                throw new RuntimeException("直播未开启");
            }
            String realRoomId = String.valueOf(initData.getInt("room_id"));
            long uid;

            // 获取直播间详细信息 (标题和主播UID)
            String title = "Bilibili 直播";
            String author = "未知主播";
            uid = initData.optLong("uid", 0);

            try {
                String roomInfoApi = "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + realRoomId;
                HttpRequest roomInfoRequest = HttpRequest.newBuilder().uri(URI.create(roomInfoApi)).build();
                HttpResponse<String> roomInfoResponse = client.send(roomInfoRequest, HttpResponse.BodyHandlers.ofString());
                JSONObject roomInfoJson = new JSONObject(roomInfoResponse.body());
                if (roomInfoJson.optInt("code") == 0) {
                    JSONObject roomData = roomInfoJson.getJSONObject("data");
                    title = roomData.optString("title", "未知直播间");
                    uid = roomData.optLong("uid", 0);
                }
            } catch (Exception e) {
                LOGGER.warn("获取直播间标题失败，使用默认值。");
            }

            // 如果获取到了 UID, 则通过新 API 获取主播名字
            if (uid > 0) {
                try {
                    String userInfoApi = "https://api.bilibili.com/x/space/acc/info?mid=" + uid;
                    HttpRequest userInfoRequest = HttpRequest.newBuilder().uri(URI.create(userInfoApi)).build();
                    HttpResponse<String> userInfoResponse = client.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
                    JSONObject userInfoJson = new JSONObject(userInfoResponse.body());
                    if (userInfoJson.optInt("code") == 0) {
                        author = userInfoJson.getJSONObject("data").optString("name", "未知主播");
                        LOGGER.info("成功获取到主播昵称: {}", author);
                    }
                } catch (Exception e) {
                    LOGGER.warn("根据UID获取B站用户名失败，将使用默认值。", e);
                }
            }

            // 获取可用的清晰度列表
            String infoApiUrl = "https://api.live.bilibili.com/xlive/web-room/v1/playUrl/playUrl?cid=" + realRoomId + "&platform=h5&qn=10000";
            HttpRequest infoRequest = HttpRequest.newBuilder().uri(URI.create(infoApiUrl)).header("User-Agent", USER_AGENT).build();
            HttpResponse<String> infoResponse = client.send(infoRequest, HttpResponse.BodyHandlers.ofString());
            JSONObject infoJson = new JSONObject(infoResponse.body());
            if (infoJson.optInt("code") != 0) {
                throw new RuntimeException("获取直播清晰度列表失败: " + infoJson.optString("message"));
            }
            JSONArray qualityOptions = infoJson.getJSONObject("data").optJSONArray("quality_description");

            List<QualityInfo> availableQualities = new ArrayList<>();
            if (qualityOptions != null) {
                for (int i = 0; i < qualityOptions.length(); i++) {
                    JSONObject quality = qualityOptions.getJSONObject(i);
                    int qn = quality.getInt("qn");
                    String desc = quality.getString("desc");
                    availableQualities.add(new QualityInfo(desc));
                }
            }

            // 根据期望选择清晰度 qn
            QualitySelection selection = findBestQualityNumber(qualityOptions, desiredQuality);

            // 使用计算出的 targetQn 请求最终的流
            String finalApiUrl = "https://api.live.bilibili.com/xlive/web-room/v1/playUrl/playUrl?cid=" + realRoomId +
                    "&platform=h5&qn=" + selection.qn;
            HttpRequest finalRequest = HttpRequest.newBuilder().uri(URI.create(finalApiUrl)).header("User-Agent", USER_AGENT).build();
            HttpResponse<String> finalResponse = client.send(finalRequest, HttpResponse.BodyHandlers.ofString());
            JSONObject finalJson = new JSONObject(finalResponse.body());
            if (finalJson.optInt("code") != 0) {
                throw new RuntimeException("获取目标清晰度直播流失败: " + finalJson.optString("message"));
            }
            JSONArray durlArray = finalJson.getJSONObject("data").optJSONArray("durl");
            if (durlArray != null && durlArray.length() > 0) {
                String finalUrl = durlArray.getJSONObject(0).getString("url");
                return new VideoInfo(finalUrl, null, title, author, null, null, 0, availableQualities, selection.description);
            }

            throw new RuntimeException("API响应中未找到任何可用的直播流 (durl is empty or null)");
        } catch (Exception e) {
            LOGGER.error("获取直播流失败", e);
            throw new RuntimeException("获取直播流失败: " + e.getMessage());
        }
    }

    private static QualitySelection findBestQualityNumber(@Nullable JSONArray qualityOptions, String desiredQuality) {
        if (qualityOptions == null || qualityOptions.length() == 0) {
            LOGGER.warn("API未返回清晰度列表，默认使用原画(10000)");
            return new QualitySelection(10000, "原画");
        }
        Map<String, Integer> qualityMap = new HashMap<>();
        for (int i = 0; i < qualityOptions.length(); i++) {
            JSONObject quality = qualityOptions.getJSONObject(i);
            qualityMap.put(quality.getString("desc"), quality.getInt("qn"));
        }
        if ("自动".equals(desiredQuality)) {
            JSONObject bestQuality = qualityOptions.getJSONObject(0);
            LOGGER.info("自动选择最高清晰度: {} (qn={})", bestQuality.getString("desc"), bestQuality.getInt("qn"));
            return new QualitySelection(bestQuality.getInt("qn"), bestQuality.getString("desc"));
        }
        Integer targetQn = qualityMap.get(desiredQuality);
        if (targetQn != null) {
            LOGGER.info("找到匹配的清晰度: {} (qn={})", desiredQuality, targetQn);
            return new QualitySelection(targetQn, desiredQuality);
        }
        JSONObject fallbackQuality = qualityOptions.getJSONObject(0);
        LOGGER.warn("未找到清晰度 '{}'，将使用最高可用清晰度: {} (qn={})",
                desiredQuality, qualityOptions.getJSONObject(0).getString("desc"), fallbackQuality);
        return new QualitySelection(fallbackQuality.getInt("qn"), fallbackQuality.getString("desc"));
    }

    private static String extractRoomId(String url) {
        Pattern pattern = Pattern.compile("live\\.bilibili\\.com/(\\d+)");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
}
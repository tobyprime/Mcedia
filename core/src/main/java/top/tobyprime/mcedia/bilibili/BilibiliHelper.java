package top.tobyprime.mcedia.bilibili;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BilibiliHelper {
    public static Logger  LOGGER = LoggerFactory.getLogger(BilibiliHelper.class);
    // 辅助方法，用于根据ID查找流并选择最佳编码
    private static JSONObject findStreamByIdAndCodec(JSONArray streams, int targetId) {
        JSONObject bestStream = null;
        int bestCodecScore = -1; // -1: 未找到, 1: AV1, 2: HEVC, 3: AVC (H.264)

        for (int i = 0; i < streams.length(); i++) {
            JSONObject stream = streams.getJSONObject(i);
            if (stream.getInt("id") == targetId) {
                String codecs = stream.optString("codecs", "");
                int currentCodecScore = 0;
                if (codecs.contains("avc1")) currentCodecScore = 3;
                else if (codecs.contains("hev1")) currentCodecScore = 2;
                else if (codecs.contains("av01")) currentCodecScore = 1;
                else currentCodecScore = 0; // 未知编码

                if (currentCodecScore > bestCodecScore) {
                    bestStream = stream;
                    bestCodecScore = currentCodecScore;
                }
            }
        }
        return bestStream;
    }
    public static BilibiliStreamSelection findBestStream(JSONArray streams, @Nullable JSONArray formats) {
        if (streams == null || streams.isEmpty()) {
            return null;
        }

        if (formats == null || formats.isEmpty()) {
            return new BilibiliStreamSelection(streams.getJSONObject(0), "默认音质");
        }

        boolean isLoggedIn = BilibiliAuthManager.getInstance().getAccountStatus().isLoggedIn;

        Map<String, Integer> availableQualityMap = new HashMap<>();
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.getJSONObject(i);
            availableQualityMap.put(format.getString("new_description"), format.getInt("quality"));
        }

        // todo: 配置画质设置
        if (true) {
            if (isLoggedIn) {
//                LOGGER.info("用户已登录，应用1080P60帧为上限的画质策略。");
//                List<String> preferredQualities = List.of(
//                        "1080P 60帧", "1080P 高码率", "1080P 高清", "1080P",
//                        "720P 60帧", "720P 高清", "720P", "高清 720P",
//                        "480P 高清", "480P", "标清 480P"
//                );
//                for (String preferred : preferredQualities) {
//                    Integer targetId = availableQualityMap.get(preferred);
//                    if (targetId != null) {
//                        JSONObject stream = findStreamByIdAndCodec(streams, targetId);
//                        if (stream != null) {
//                            LOGGER.info("自动清晰度(已登录): 找到匹配 '{}'", preferred);
//                            return new BilibiliStreamSelection(stream, preferred);
//                        }
//                    }
//                }
                var selection = new BilibiliStreamSelection(streams.getJSONObject(0), formats.getJSONObject(0).getString("new_description"));
                LOGGER.warn("自动清晰度(已登录): 使用API最高画质 {}", selection.qualityDescription);
            } else {
                LOGGER.info("用户未登录，尝试锁定至 360P 画质。");
                String targetQuality = "360P 流畅";
                Integer targetId = availableQualityMap.get(targetQuality);
                if (targetId != null) {
                    JSONObject stream = findStreamByIdAndCodec(streams, targetId);
                    if (stream != null) {
                        LOGGER.info("自动清晰度(未登录): 成功锁定到 '{}'", targetQuality);
                        return new BilibiliStreamSelection(stream, targetQuality);
                    }
                }
                LOGGER.warn("自动清晰度(未登录): 未找到 '{}'，回退到最低画质。", targetQuality);
                return new BilibiliStreamSelection(streams.getJSONObject(streams.length() - 1), formats.getJSONObject(formats.length() - 1).getString("new_description"));
            }
        }

        // --- 手动指定清晰度逻辑 ---
        Integer targetQualityId = availableQualityMap.get("自动");
        if (targetQualityId != null) {
            JSONObject stream = findStreamByIdAndCodec(streams, targetQualityId);
            if (stream != null) {
                return new BilibiliStreamSelection(stream, "自动");
            }
        }

        LOGGER.warn("未找到指定的清晰度 '{}'，将使用最高可用清晰度。", "自动");
        return new BilibiliStreamSelection(streams.getJSONObject(0), formats.getJSONObject(0).getString("new_description"));
    }
}

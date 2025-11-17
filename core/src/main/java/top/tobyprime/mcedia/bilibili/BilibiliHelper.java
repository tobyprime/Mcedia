package top.tobyprime.mcedia.bilibili;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Configs;

import java.util.*;

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

        Map<String, Integer> availableQualityMap = new HashMap<>();
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.getJSONObject(i);
            availableQualityMap.put(format.getString("new_description"), format.getInt("quality"));
        }
        if (Configs.QUALITY == 0){
            String lowestQualityDesc = formats.getJSONObject(formats.length() - 1).getString("new_description");
            var selection = new BilibiliStreamSelection(streams.getJSONObject(streams.length() - 1), lowestQualityDesc);
            LOGGER.info("清晰度{}: 找到匹配 '{}'",Configs.QUALITY, selection);
            return selection;
        }


        List<String> ignoreQualities = new ArrayList<>();

        if (Configs.QUALITY <= 2){
            ignoreQualities.add("8k 超高清");
        }
        if (Configs.QUALITY <= 1){
            ignoreQualities.add("4K 超高清");
        }


        for (var preferred : availableQualityMap.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getValue)).toList().reversed()) {
            if (ignoreQualities.contains(preferred.getKey())) {
                continue;
            }

            Integer targetId = availableQualityMap.get(preferred.getKey());
            if (targetId != null) {
                JSONObject stream = findStreamByIdAndCodec(streams, targetId);
                if (stream != null) {
                    LOGGER.info("清晰度{}: 找到匹配 '{}'",Configs.QUALITY, preferred);
                    return new BilibiliStreamSelection(stream, preferred.getKey());
                }
            }
        }

        String lowestQualityDesc = formats.getJSONObject(formats.length() - 1).getString("new_description");
        var selection = new BilibiliStreamSelection(streams.getJSONObject(streams.length() - 1), lowestQualityDesc);
        LOGGER.warn("未找到符合清晰度，使用低画质 {}", selection.qualityDescription);
        return selection;

    }
}

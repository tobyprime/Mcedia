package top.tobyprime.mcedia.video_fetcher;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 一个工具类，包含用于处理Bilibili DASH流选择的共享逻辑。
 * 这避免了在 BiliBiliVideoFetcher 和 BilibiliBangumiFetcher 之间的代码重复。
 */
public final class BilibiliStreamUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliStreamUtils.class);

    // 私有构造函数，防止该工具类被实例化。
    private BilibiliStreamUtils() {}

    /**
     * 一个简单的数据类，用于封装 findBestStream 的返回结果。
     */
    public static class StreamSelection {
        public final JSONObject stream;
        public final String qualityDescription;

        public StreamSelection(JSONObject stream, String qualityDescription) {
            this.stream = stream;
            this.qualityDescription = qualityDescription;
        }
    }

    /**
     * 根据期望的清晰度从流列表中选择最佳的流。
     * @param streams JSON 数组，包含视频或音频流
     * @param formats JSON 数组，包含清晰度格式的描述信息
     * @param desiredQuality 用户期望的清晰度描述字符串
     * @param authStatusSupplier 一个提供当前登录状态的 Supplier
     * @return 匹配的最佳流及其描述
     */
    public static StreamSelection findBestStream(JSONArray streams, @Nullable JSONArray formats, String desiredQuality, Supplier<Boolean> authStatusSupplier) {
        if (streams == null || streams.length() == 0) {
            return null;
        }

        // 对于没有 format 信息的流 (通常是音频)，直接返回第一个（最高质量）。
        if (formats == null || formats.length() == 0) {
            return new StreamSelection(streams.getJSONObject(0), "默认音质");
        }

        Map<String, Integer> availableQualityMap = new HashMap<>();
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.getJSONObject(i);
            availableQualityMap.put(format.getString("new_description"), format.getInt("quality"));
        }

        // --- 自动清晰度逻辑 ---
        boolean isLoggedIn = authStatusSupplier.get();

        if ("自动".equals(desiredQuality)) {
            if (isLoggedIn) {
                LOGGER.info("用户已登录，应用1080P60帧为上限的画质策略。");
                List<String> preferredQualities = List.of(
                        "1080P 60帧", "1080P 高码率", "1080P 高清", "1080P",
                        "720P 60帧", "720P 高清", "720P", "高清 720P",
                        "480P 高清", "480P", "标清 480P"
                );
                for (String preferred : preferredQualities) {
                    Integer targetId = availableQualityMap.get(preferred);
                    if (targetId != null) {
                        JSONObject stream = findStreamByIdAndCodec(streams, targetId);
                        if (stream != null) {
                            LOGGER.info("自动清晰度(已登录): 找到匹配 '{}'", preferred);
                            return new StreamSelection(stream, preferred);
                        }
                    }
                }
                LOGGER.warn("自动清晰度(已登录): 未在偏好列表中找到匹配项，回退到API最高画质。");
                return new StreamSelection(streams.getJSONObject(0), formats.getJSONObject(0).getString("new_description"));
            } else {
                LOGGER.info("用户未登录，尝试锁定至 360P 画质。");
                String targetQuality = "360P 流畅";
                Integer targetId = availableQualityMap.get(targetQuality);
                if (targetId != null) {
                    JSONObject stream = findStreamByIdAndCodec(streams, targetId);
                    if (stream != null) {
                        LOGGER.info("自动清晰度(未登录): 成功锁定到 '{}'", targetQuality);
                        return new StreamSelection(stream, targetQuality);
                    }
                }
                LOGGER.warn("自动清晰度(未登录): 未找到 '{}'，回退到最低画质。", targetQuality);
                String lowestQualityDesc = formats.getJSONObject(formats.length() - 1).getString("new_description");
                return new StreamSelection(streams.getJSONObject(streams.length() - 1), lowestQualityDesc);
            }
        }

        // --- 手动指定清晰度逻辑 ---
        Integer targetQualityId = availableQualityMap.get(desiredQuality);
        if (targetQualityId != null) {
            JSONObject stream = findStreamByIdAndCodec(streams, targetQualityId);
            if (stream != null) {
                return new StreamSelection(stream, desiredQuality);
            }
        }

        LOGGER.warn("未找到指定的清晰度 '{}'，将使用最高可用清晰度。", desiredQuality);
        String highestQualityDesc = formats.getJSONObject(0).getString("new_description");
        return new StreamSelection(streams.getJSONObject(0), highestQualityDesc);
    }

    /**
     * 辅助方法，用于根据ID查找流并优先选择AVC (H.264) 编码。
     * @param streams JSON 数组
     * @param targetId 目标清晰度 ID
     * @return 最佳匹配的流 JSONObject
     */
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
}
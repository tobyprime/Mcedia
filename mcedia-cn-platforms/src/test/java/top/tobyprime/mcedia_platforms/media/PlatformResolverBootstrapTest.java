package top.tobyprime.mcedia_platforms.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformResolverBootstrapTest {
    private static Method selectBestVideoTrack;
    private static Method extractSupportedBilibiliInput;
    private static Method extractSupportedDouyinInput;
    private static Method normalizeDouyinPlayUrl;
    private static Method isBilibiliBangumiUrl;
    private static Method parseBangumiEpIdFromUrl;
    private static Method parseBangumiSeasonIdFromUrl;
    private static Method parsePNumberFromUrl;
    private static Method normalizeBilibiliCoverUrl;

    @BeforeAll
    static void setUp() throws Exception {
        selectBestVideoTrack = PlatformResolverBootstrap.class.getDeclaredMethod(
                "selectBestVideoTrack", JsonArray.class, int.class);
        selectBestVideoTrack.setAccessible(true);
        extractSupportedBilibiliInput = BilibiliUrlParser.class.getDeclaredMethod(
                "extractSupportedInput", String.class);
        extractSupportedBilibiliInput.setAccessible(true);
        extractSupportedDouyinInput = DouyinUrlParser.class.getDeclaredMethod(
                "extractSupportedInput", String.class);
        extractSupportedDouyinInput.setAccessible(true);
        normalizeDouyinPlayUrl = PlatformResolverBootstrap.class.getDeclaredMethod(
                "normalizeDouyinPlayUrl", String.class);
        normalizeDouyinPlayUrl.setAccessible(true);
        isBilibiliBangumiUrl = PlatformResolverBootstrap.class.getDeclaredMethod(
                "isBilibiliBangumiUrl", String.class);
        isBilibiliBangumiUrl.setAccessible(true);
        parseBangumiEpIdFromUrl = PlatformResolverBootstrap.class.getDeclaredMethod(
                "parseBangumiEpIdFromUrl", String.class);
        parseBangumiEpIdFromUrl.setAccessible(true);
        parseBangumiSeasonIdFromUrl = PlatformResolverBootstrap.class.getDeclaredMethod(
                "parseBangumiSeasonIdFromUrl", String.class);
        parseBangumiSeasonIdFromUrl.setAccessible(true);
        parsePNumberFromUrl = PlatformResolverBootstrap.class.getDeclaredMethod(
                "parsePNumberFromUrl", String.class);
        parsePNumberFromUrl.setAccessible(true);
        normalizeBilibiliCoverUrl = PlatformResolverBootstrap.class.getDeclaredMethod(
                "normalizeBilibiliCoverUrl", String.class);
        normalizeBilibiliCoverUrl.setAccessible(true);
    }

    private JsonObject invokeSelect(JsonArray video, int maxHeight) throws Exception {
        return (JsonObject) selectBestVideoTrack.invoke(null, video, maxHeight);
    }

    private String invokeExtractSupportedBilibiliInput(String input) throws Exception {
        return (String) extractSupportedBilibiliInput.invoke(null, input);
    }

    private String invokeExtractSupportedDouyinInput(String input) throws Exception {
        return (String) extractSupportedDouyinInput.invoke(null, input);
    }

    private String invokeNormalizeDouyinPlayUrl(String input) throws Exception {
        return (String) normalizeDouyinPlayUrl.invoke(null, input);
    }

    private boolean invokeIsBilibiliBangumiUrl(String input) throws Exception {
        return (boolean) isBilibiliBangumiUrl.invoke(null, input);
    }

    private String invokeParseBangumiEpIdFromUrl(String input) throws Exception {
        return (String) parseBangumiEpIdFromUrl.invoke(null, input);
    }

    private String invokeParseBangumiSeasonIdFromUrl(String input) throws Exception {
        return (String) parseBangumiSeasonIdFromUrl.invoke(null, input);
    }

    private int invokeParsePNumberFromUrl(String input) throws Exception {
        return (int) parsePNumberFromUrl.invoke(null, input);
    }

    private String invokeNormalizeBilibiliCoverUrl(String input) throws Exception {
        return (String) normalizeBilibiliCoverUrl.invoke(null, input);
    }

    private static JsonArray videoTracks(JsonObject... tracks) {
        var array = new JsonArray();
        for (var track : tracks) {
            array.add(track);
        }
        return array;
    }

    private static JsonObject track(String id, int width, int height) {
        var object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("width", width);
        object.addProperty("height", height);
        object.addProperty("bandwidth", width * height * 10);
        object.addProperty("baseUrl", "https://example.com/" + id + ".m4s");
        return object;
    }

    @Test
    void selectsHighestTrackWhenUnlimited() throws Exception {
        var video = videoTracks(
                track("80", 1920, 1080),
                track("64", 1280, 720)
        );

        var result = invokeSelect(video, 0);

        assertEquals("80", result.get("id").getAsString());
    }

    @Test
    void selectsWithinToleranceThreshold() throws Exception {
        var video = videoTracks(
                track("120", 3840, 2160),
                track("80", 1920, 1080),
                track("64", 1280, 720)
        );

        var result = invokeSelect(video, 1080);

        assertEquals("80", result.get("id").getAsString());
    }

    @Test
    void appliesToleranceToSlightlyExceedingTrack() throws Exception {
        var video = videoTracks(
                track("120", 3840, 2160),
                track("90", 1920, 1200),
                track("64", 1280, 720)
        );

        var result = invokeSelect(video, 1080);

        assertEquals("90", result.get("id").getAsString());
    }

    @Test
    void fallsBackToLowestWhenAllExceedWithTolerance() throws Exception {
        var video = videoTracks(
                track("120", 3840, 2160),
                track("80", 1920, 1080)
        );

        var result = invokeSelect(video, 720);

        assertEquals("80", result.get("id").getAsString());
    }

    @Test
    void skipsTracksWithoutHeightInfo() throws Exception {
        var unknown = new JsonObject();
        unknown.addProperty("id", "unknown");
        unknown.addProperty("baseUrl", "https://example.com/unknown.m4s");
        var video = videoTracks(
                unknown,
                track("64", 1280, 720),
                track("32", 640, 360)
        );

        var result = invokeSelect(video, 1080);

        assertEquals("64", result.get("id").getAsString());
    }

    @Test
    void singleTrackWithinTolerance() throws Exception {
        var video = videoTracks(track("64", 1280, 720));

        var result = invokeSelect(video, 1080);

        assertEquals("64", result.get("id").getAsString());
    }

    @Test
    void singleTrackExceedsEvenWithTolerance() throws Exception {
        var video = videoTracks(track("120", 3840, 2160));

        var result = invokeSelect(video, 720);

        assertEquals("120", result.get("id").getAsString());
    }

    @Test
    void higherWithinTolerancePreferred() throws Exception {
        var video = videoTracks(
                track("4k", 3840, 2160),
                track("1080p", 1920, 1080),
                track("720p", 1280, 720)
        );

        var result = invokeSelect(video, 1080);

        assertEquals("1080p", result.get("id").getAsString());
    }

    @Test
    void extractsBilibiliUrlFromShareText() throws Exception {
        var input = "【奥马尔资产从3000万改成10万，撒谎被抓包 - Cash Jordan - 中配】 https://www.bilibili.com/video/BV1pRVF6kEPh/?share_source=copy_web&vd_source=ce53985b2847d681d3062f2c4d129036";

        var result = invokeExtractSupportedBilibiliInput(input);

        assertEquals("https://www.bilibili.com/video/BV1pRVF6kEPh/?share_source=copy_web&vd_source=ce53985b2847d681d3062f2c4d129036", result);
    }

    @Test
    void extractsB23UrlFromShareText() throws Exception {
        var input = "【【智人TV】对咯，这么带娃就对咯-哔哩哔哩】 https://b23.tv/Gt0zQrG";

        var result = invokeExtractSupportedBilibiliInput(input);

        assertEquals("https://b23.tv/Gt0zQrG", result);
    }

    @Test
    void extractsBangumiUrlFromPlainUrl() throws Exception {
        var input = "https://www.bilibili.com/bangumi/play/ep249469";

        var result = invokeExtractSupportedBilibiliInput(input);

        assertEquals(input, result);
    }

    @Test
    void extractsBangumiUrlWithQueryFromPlainUrl() throws Exception {
        var input = "https://www.bilibili.com/bangumi/play/ep249469/?share_source=copy_web";

        var result = invokeExtractSupportedBilibiliInput(input);

        assertEquals(input, result);
    }

    @Test
    void extractsBangumiUrlFromShareText() throws Exception {
        var input = "【猫和老鼠 旧版：第1话 飞翔猫 The Flying Cat】 https://www.bilibili.com/bangumi/play/ep249469";

        var result = invokeExtractSupportedBilibiliInput(input);

        assertEquals("https://www.bilibili.com/bangumi/play/ep249469", result);
    }

    @Test
    void extractsBangumiB23UrlFromShareText() throws Exception {
        var input = "【《猫和老鼠 旧版》 第1话 飞翔猫 The Flying Cat-哔哩哔哩番剧】https://b23.tv/ep249469";

        var result = invokeExtractSupportedBilibiliInput(input);

        assertEquals("https://b23.tv/ep249469", result);
    }

    @Test
    void detectsBangumiPlayUrls() throws Exception {
        assertTrue(invokeIsBilibiliBangumiUrl("https://www.bilibili.com/bangumi/play/ep249469"));
        assertTrue(invokeIsBilibiliBangumiUrl("https://www.bilibili.com/bangumi/play/ss12345?p=2"));
        assertFalse(invokeIsBilibiliBangumiUrl("https://www.bilibili.com/video/BV1pRVF6kEPh"));
    }

    @Test
    void parsesBangumiIdentifiersFromUrl() throws Exception {
        assertEquals("249469", invokeParseBangumiEpIdFromUrl("https://www.bilibili.com/bangumi/play/ep249469"));
        assertEquals("12345", invokeParseBangumiSeasonIdFromUrl("https://www.bilibili.com/bangumi/play/ss12345?p=2"));
    }

    @Test
    void parsesBangumiPageNumberFromSeasonUrl() throws Exception {
        assertEquals(2, invokeParsePNumberFromUrl("https://www.bilibili.com/bangumi/play/ss12345?p=2"));
        assertEquals(1, invokeParsePNumberFromUrl("https://www.bilibili.com/bangumi/play/ss12345"));
    }

    @Test
    void bilibiliCoverUrlsAreEmittedOverHttps() throws Exception {
        assertEquals("https://i0.hdslb.com/bfs/archive/abc.jpg",
                invokeNormalizeBilibiliCoverUrl("http://i0.hdslb.com/bfs/archive/abc.jpg"));
        assertEquals("https://i0.hdslb.com/bfs/bangumi/image/abc.png",
                invokeNormalizeBilibiliCoverUrl("https://i0.hdslb.com/bfs/bangumi/image/abc.png"));
        assertEquals(null, invokeNormalizeBilibiliCoverUrl(null));
        assertEquals(null, invokeNormalizeBilibiliCoverUrl("  "));
    }

    @Test
    void extractsDouyinUrlFromShareText() throws Exception {
        var input = "6.99 “每个人的桌面都有自己的故事” # 热门 # Windows # Windows11 # 毛玻璃质感 # 电脑美化 https://v.douyin.com/-K9yAYyQFqk/ 复制此链接，打开抖音搜索，直接观看视频！ 11/01 :1pm eOK:/ c@a.aN";

        var result = invokeExtractSupportedDouyinInput(input);

        assertEquals("https://v.douyin.com/-K9yAYyQFqk/", result);
    }

    @Test
    void unwrapsNestedDouyinCdnUrlFromVideoIdQuery() throws Exception {
        var input = "https://aweme.snssdk.com/aweme/v1/play/?video_id=https://sf6-cdn-tos.douyinstatic.com/obj/tos-cn-ve-2774/ooqMdecTGGTlLzj1peIvXyQXfFAYZeIAhZmKrK&ratio=720p&line=0";

        var result = invokeNormalizeDouyinPlayUrl(input);

        assertEquals("https://sf6-cdn-tos.douyinstatic.com/obj/tos-cn-ve-2774/ooqMdecTGGTlLzj1peIvXyQXfFAYZeIAhZmKrK", result);
    }
}

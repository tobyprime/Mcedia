package top.tobyprime.mcedia_platforms.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia.api.resolver.MediaResolverSettings;
import top.tobyprime.mcedia_platforms.auth.BilibiliCookie;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

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
    private static Method trustedBilibiliMediaCookie;
    private static Method extractBilibiliPlayInfo;
    private static Method resolveBilibiliInternal;
    private static java.lang.reflect.Field wbiMixinKey;
    private static java.lang.reflect.Field wbiCachedDay;

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
        trustedBilibiliMediaCookie = PlatformResolverBootstrap.class.getDeclaredMethod(
                "trustedBilibiliMediaCookie", String.class, String.class, String.class);
        trustedBilibiliMediaCookie.setAccessible(true);
        extractBilibiliPlayInfo = PlatformResolverBootstrap.class.getDeclaredMethod(
                "extractBilibiliPlayInfo", JsonObject.class, java.util.Map.class, String.class, int.class, int.class);
        extractBilibiliPlayInfo.setAccessible(true);
        resolveBilibiliInternal = PlatformResolverBootstrap.class.getDeclaredMethod("resolveBilibiliInternal", String.class);
        resolveBilibiliInternal.setAccessible(true);
        wbiMixinKey = top.tobyprime.mcedia_platforms.auth.BilibiliWbiSign.class.getDeclaredField("cachedMixinKey");
        wbiMixinKey.setAccessible(true);
        wbiCachedDay = top.tobyprime.mcedia_platforms.auth.BilibiliWbiSign.class.getDeclaredField("cachedDay");
        wbiCachedDay.setAccessible(true);
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

    private String invokeTrustedBilibiliMediaCookie(String cookie, String videoUrl, String audioUrl) throws Exception {
        return (String) trustedBilibiliMediaCookie.invoke(null, cookie, videoUrl, audioUrl);
    }

    private Object invokeExtractBilibiliPlayInfo(JsonObject data, String cookie, int maxHeight, int requestedQn) throws Exception {
        return extractBilibiliPlayInfo.invoke(null, data, java.util.Map.of("User-Agent", "fixture"), cookie, maxHeight, requestedQn);
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

    private static JsonObject playurlTrack(String id, int width, int height, String url) {
        var object = track(id, width, height);
        object.addProperty("baseUrl", url);
        object.addProperty("codecid", 7);
        object.addProperty("frameRate", "60");
        return object;
    }

    private static JsonObject realBilibiliViewResponse() {
        var root = new JsonObject();
        root.addProperty("code", 0);
        var data = new JsonObject();
        data.addProperty("title", "fixture BV1z73MzAEeb");
        var owner = new JsonObject();
        owner.addProperty("name", "fixture-owner");
        data.add("owner", owner);
        data.addProperty("pic", "https://i0.hdslb.com/bfs/archive/fixture.jpg");
        data.addProperty("cid", 38501680872L);
        root.add("data", data);
        return root;
    }

    private static JsonObject limitedBilibiliPlayurlResponse() {
        var root = new JsonObject();
        root.addProperty("code", 0);
        var data = new JsonObject();
        data.addProperty("quality", 80);
        var dash = new JsonObject();
        var video = new JsonArray();
        video.add(playurlTrack("80", 1920, 1080,
                "https://upos-sz-estgoss.bilivideo.com/upgcxcode/72/08/38501680872/38501680872-1-30080.m4s"));
        var audio = new JsonArray();
        audio.add(playurlTrack("30280", 0, 0,
                "https://upos-sz-estgoss.bilivideo.com/upgcxcode/72/08/38501680872/38501680872-1-30280.m4s"));
        dash.add("video", video);
        dash.add("audio", audio);
        data.add("dash", dash);
        root.add("data", data);
        return root;
    }

    private static JsonObject realBilibiliDashResponse() {
        var root = new JsonObject();
        root.addProperty("code", 0);
        var data = new JsonObject();
        var dash = new JsonObject();
        var video = new JsonArray();
        video.add(playurlTrack("80", 1920, 1080,
                "https://cn-zjjh-ct-04-05.bilivideo.com/upgcxcode/95/37/114787497285357/114787497285357-1-80.m4s"));
        video.add(playurlTrack("120", 3840, 2160,
                "https://cn-zjjh-ct-04-05.bilivideo.com/upgcxcode/95/37/114787497285357/114787497285357-1-120.m4s"));
        var audio = new JsonArray();
        audio.add(playurlTrack("30280", 0, 0,
                "https://xy36x158x181x7xy.mcdn.bilivideo.cn/upgcxcode/95/37/114787497285357/114787497285357-1-30280.m4s"));
        dash.add("video", video);
        dash.add("audio", audio);
        data.add("dash", dash);
        root.add("data", data);
        return root.getAsJsonObject("data");
    }

    private static final class FixtureResponse implements HttpResponse<String> {
        private final HttpRequest request;
        private final String body;

        private FixtureResponse(HttpRequest request, String body) {
            this.request = request;
            this.body = body;
        }

        @Override public int statusCode() { return 200; }
        @Override public HttpRequest request() { return request; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public String body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static String queryValue(URI uri, String key) {
        for (var part : uri.getRawQuery().split("&")) {
            var pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) {
                return java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @Test
    void usesExactModRequestsAndReproducesServerLimitedQuality() throws Exception {
        var requests = new ArrayList<HttpRequest>();
        PlatformResolverBootstrap.setBilibiliHttpTransportForTests(request -> {
            requests.add(request);
            var uri = request.uri();
            if (uri.getPath().equals("/x/web-interface/view")) {
                return new FixtureResponse(request, realBilibiliViewResponse().toString());
            }
            if (uri.getPath().equals("/x/player/wbi/playurl")) {
                var qn = queryValue(uri, "qn");
                if ("120".equals(qn)) {
                    var root = new JsonObject();
                    root.addProperty("code", 0);
                    root.add("data", realBilibiliDashResponse());
                    return new FixtureResponse(request, root.toString());
                }
                return new FixtureResponse(request, limitedBilibiliPlayurlResponse().toString());
            }
            assertEquals("/x/player/playurl", uri.getPath());
            return new FixtureResponse(request, limitedBilibiliPlayurlResponse().toString());
        });
        try {
            var properties = new java.util.Properties();
            properties.setProperty("BILIBILI_COOKIES", "SESSDATA=fixture; DedeUserID=385677210");
            properties.setProperty("BILIBILI_BUVID3", "fixture-buvid3");
            properties.setProperty("BILIBILI_BUVID4", "fixture-buvid4");
            BilibiliCookie.fromProperties(properties);
            wbiMixinKey.set(null, "fixture-mixin-key");
            wbiCachedDay.setLong(null, System.currentTimeMillis() / 86400_000);
            MediaResolverSettings.setResolutionLimit(0);

            var result = (PlatformMedia) resolveBilibiliInternal.invoke(null, "https://www.bilibili.com/video/BV1z73MzAEeb/");

            assertEquals("https://api.bilibili.com/x/web-interface/view?bvid=BV1z73MzAEeb", requests.get(0).uri().toString());
            assertEquals(List.of("/x/player/wbi/playurl", "/x/player/wbi/playurl"),
                    requests.subList(1, requests.size()).stream().map(request -> request.uri().getPath()).toList());
            assertEquals(List.of("127", "120"),
                    requests.subList(1, requests.size()).stream()
                            .map(request -> queryValue(request.uri(), "qn"))
                            .toList());
            var firstPlayurl = requests.get(1);
            assertEquals("BV1z73MzAEeb", queryValue(firstPlayurl.uri(), "bvid"));
            assertEquals("38501680872", queryValue(firstPlayurl.uri(), "cid"));
            assertEquals("0", queryValue(firstPlayurl.uri(), "fnver"));
            assertEquals("4048", queryValue(firstPlayurl.uri(), "fnval"));
            assertEquals("1", queryValue(firstPlayurl.uri(), "fourk"));
            assertEquals("1", queryValue(firstPlayurl.uri(), "try_look"));
            assertEquals("1", queryValue(firstPlayurl.uri(), "voice_balance"));
            assertEquals("pre-load", queryValue(firstPlayurl.uri(), "gaia_source"));
            assertEquals("1550101", queryValue(firstPlayurl.uri(), "web_location"));
            assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    firstPlayurl.headers().firstValue("User-Agent").orElseThrow());
            assertEquals("https://www.bilibili.com/",
                    firstPlayurl.headers().firstValue("Referer").orElseThrow());
            assertEquals("SESSDATA=fixture; DedeUserID=385677210; buvid3=fixture-buvid3; buvid4=fixture-buvid4",
                    firstPlayurl.headers().firstValue("Cookie").orElseThrow());
            assertEquals("3840x2160", result.getInfo().getExtraMetadata().get("bilibili.selected_width") + "x"
                    + result.getInfo().getExtraMetadata().get("bilibili.selected_height"));
            assertEquals("120", result.getInfo().getExtraMetadata().get("bilibili.selected_qn"));
        } finally {
            PlatformResolverBootstrap.setBilibiliHttpTransportForTests(null);
        }
    }

    @Test
    void resolvesRealBilibiliMember4kDashResponseWhenUnlimited() throws Exception {
        var result = invokeExtractBilibiliPlayInfo(
                realBilibiliDashResponse(),
                "SESSDATA=fixture; DedeUserID=385677210",
                0,
                120);
        var playInfoMethod = result.getClass().getDeclaredMethod("playInfo");
        var diagnosticsMethod = result.getClass().getDeclaredMethod("diagnostics");
        playInfoMethod.setAccessible(true);
        diagnosticsMethod.setAccessible(true);
        var playInfo = (top.tobyprime.mcedia.api.media.MediaPlayInfo) playInfoMethod.invoke(result);
        @SuppressWarnings("unchecked")
        var diagnostics = (java.util.Map<String, String>) diagnosticsMethod.invoke(result);

        assertTrue(playInfo.getUrl().endsWith("-1-120.m4s"));
        assertTrue(playInfo.getAudioUrl().contains("mcdn.bilivideo.cn"));
        assertEquals("SESSDATA=fixture; DedeUserID=385677210", playInfo.getCookie());
        assertEquals("120", diagnostics.get("bilibili.selected_qn"));
        assertEquals("3840", diagnostics.get("bilibili.selected_width"));
        assertEquals("2160", diagnostics.get("bilibili.selected_height"));
    }

    @Test
    void preservesCookieForMcdnBilivideoAudioHostFromRealBilibiliPlayback() throws Exception {
        var cookie = "SESSDATA=fixture; DedeUserID=385677210";
        var videoUrl = "https://cn-zjjh-ct-04-05.bilivideo.com/upgcxcode/95/37/114787497285357/114787497285357-1-120.m4s";
        var audioUrl = "https://xy36x158x181x7xy.mcdn.bilivideo.cn/upgcxcode/95/37/114787497285357/114787497285357-1-30280.m4s";

        assertEquals(cookie, invokeTrustedBilibiliMediaCookie(cookie, videoUrl, audioUrl));
    }

    @Test
    void selects4kTrackForTheRealBvVideoWhenUnlimited() throws Exception {
        var video = videoTracks(
                track("80", 1920, 1080),
                track("120", 3840, 2160),
                track("64", 1280, 720)
        );

        var result = invokeSelect(video, 0);

        assertEquals("120", result.get("id").getAsString());
        assertEquals(2160, result.get("height").getAsInt());
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

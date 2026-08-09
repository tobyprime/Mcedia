package top.tobyprime.mcedia_platforms.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.media.MediaInfo;
import top.tobyprime.mcedia.api.media.MediaPlayInfo;
import top.tobyprime.mcedia.api.resolver.MediaResolverSettings;
import top.tobyprime.mcedia.api.resolver.MediaResolvers;
import top.tobyprime.mcedia_platforms.auth.BilibiliAccountStatus;
import top.tobyprime.mcedia_platforms.auth.BilibiliAuthManager;
import top.tobyprime.mcedia_platforms.auth.BilibiliCookie;
import top.tobyprime.mcedia_platforms.auth.BilibiliWbiSign;
import top.tobyprime.mcedia_platforms.auth.NeteaseCookie;
import top.tobyprime.mcedia_platforms.auth.NeteaseCrypto;
import top.tobyprime.mcedia_platforms.danmaku.bilibili.BilibiliDanmakuProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class PlatformResolverBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformResolverBootstrap.class);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    private static final String BILIBILI_UA = "Mozilla/5.0";
    private static final String DOUYIN_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String NETEASE_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // Bilibili 播放清晰度 qn 值
    private static final int BILIBILI_QN_720P = 64;
    private static final int BILIBILI_QN_1080P = 80;
    private static final int BILIBILI_QN_1080P_HIGH = 112;
    private static final int BILIBILI_QN_1080P_60 = 116;
    private static final int BILIBILI_QN_4K = 120;
    private static final int BILIBILI_QN_8K = 127;

    private static final Pattern SONG_ID_URL_PATTERN = Pattern.compile("(?:song|music)(?:\\?id=|/)(\\d+)");
    private static final Pattern SONG_ID_HASH_PATTERN = Pattern.compile("(?:#/)?song\\?id=(\\d+)");
    private static final Pattern PURE_DIGIT_PATTERN = Pattern.compile("\\d+");

    private PlatformResolverBootstrap() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        MediaResolvers.register("bilibili", PlatformResolverBootstrap::resolveBilibili);
        MediaResolvers.register("bilibili_live", PlatformResolverBootstrap::resolveBilibiliLive);
        MediaResolvers.register("douyin", PlatformResolverBootstrap::resolveDouyin);
        MediaResolvers.register("netease", PlatformResolverBootstrap::resolveNetease);
        MediaResolvers.registerParser(new BilibiliUrlParser(), 0);
        MediaResolvers.registerParser(new DouyinUrlParser(), 0);
        MediaResolvers.registerParser(new NeteaseUrlParser(), 0);
        MediaResolvers.registerCollectionResolver("bilibili", new BilibiliCollectionResolver());
        LOGGER.info("Registered platform media resolvers: bilibili, bilibili_live, douyin, netease");
    }

    private static PlatformMedia resolveBilibili(String target) {
        try {
            return resolveBilibiliInternal(target);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve bilibili media: " + e.getMessage(), e);
        }
    }

    private static PlatformMedia resolveNetease(String target) {
        try {
            return resolveNeteaseInternal(target);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve netease media: " + e.getMessage(), e);
        }
    }

    private static PlatformMedia resolveDouyin(String target) {
        try {
            return resolveDouyinInternal(target);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve douyin media: " + e.getMessage(), e);
        }
    }

    private static PlatformMedia resolveBilibiliInternal(String target) throws Exception {
        var sourceUrl = resolveBilibiliSourceUrl(target);
        if (isBilibiliBangumiUrl(sourceUrl)) {
            return resolveBilibiliBangumiSourceUrl(sourceUrl);
        }

        var bvid = parseBvidFromUrl(sourceUrl);
        if (bvid == null) {
            throw new IllegalArgumentException("未找到 BV 号");
        }

        var page = parsePNumberFromUrl(sourceUrl);
        var viewApi = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        var viewRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(viewApi))
                .header("User-Agent", BILIBILI_UA)
                .header("Referer", "https://www.bilibili.com/");
        addBilibiliCookie(viewRequestBuilder, viewApi);
        var viewResponse = HTTP.send(viewRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        var viewJson = parseObject(viewResponse.body());
        if (optInt(viewJson, "code", -1) != 0) {
            throw new IllegalStateException(optString(viewJson, "message", "获取视频信息失败"));
        }

        var data = viewJson.getAsJsonObject("data");
        var title = data.get("title").getAsString();
        var owner = data.getAsJsonObject("owner").get("name").getAsString();
        long cid;
        String partName = null;
        var pages = optArray(data, "pages");
        if (pages != null && !pages.isEmpty()) {
            var index = Math.max(0, Math.min(page - 1, pages.size() - 1));
            var currentPage = pages.get(index).getAsJsonObject();
            cid = currentPage.get("cid").getAsLong();
            partName = optString(currentPage, "part", null);
            if (title.equals(partName)) {
                partName = null;
            }
        } else {
            cid = data.get("cid").getAsLong();
        }

        var headers = createBilibiliMediaHeaders();
        var resolved = resolveBilibiliPlayInfoWithFallback(
                "https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + cid,
                "data",
                headers,
                false
        );
        var metadata = new HashMap<String, String>();
        metadata.put(BilibiliDanmakuProvider.KEY_BVID, bvid);
        metadata.put(BilibiliDanmakuProvider.KEY_CID, String.valueOf(cid));
        metadata.put(BilibiliDanmakuProvider.KEY_PAGE, String.valueOf(page));
        metadata.putAll(resolved.diagnostics());
        var info = new MediaInfo(
                partName == null || partName.isBlank() ? title : title + " - " + partName,
                owner,
                normalizeBilibiliCoverUrl(optString(data, "pic", null)),
                "bilibili",
                metadata
        );

        return new PlatformMedia(resolved.playInfo(), info);
    }

    private static PlatformMedia resolveBilibiliBangumiSourceUrl(String sourceUrl) throws Exception {
        var episodeId = parseBangumiEpIdFromUrl(sourceUrl);
        var seasonId = parseBangumiSeasonIdFromUrl(sourceUrl);
        if (episodeId == null && seasonId == null) {
            throw new IllegalArgumentException("未找到番剧剧集 ID");
        }

        var viewApi = episodeId != null
                ? "https://api.bilibili.com/pgc/view/web/season?ep_id=" + episodeId
                : "https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId;
        var viewRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(viewApi))
                .header("User-Agent", BILIBILI_UA)
                .header("Referer", "https://www.bilibili.com/");
        addBilibiliCookie(viewRequestBuilder, viewApi);
        var viewResponse = HTTP.send(viewRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        var viewJson = parseObject(viewResponse.body());
        if (optInt(viewJson, "code", -1) != 0) {
            throw new IllegalStateException(optString(viewJson, "message", "获取番剧信息失败"));
        }

        var result = viewJson.getAsJsonObject("result");
        var selection = selectBangumiEpisode(result, sourceUrl, episodeId);
        var headers = createBilibiliMediaHeaders();
        var resolved = resolveBilibiliPlayInfoWithFallback(
                "https://api.bilibili.com/pgc/player/web/playurl?ep_id=" + selection.episodeId()
                        + "&cid=" + selection.cid(),
                "result",
                headers,
                true
        );
        var metadata = new HashMap<String, String>();
        metadata.put(BilibiliDanmakuProvider.KEY_CID, String.valueOf(selection.cid()));
        metadata.put("bilibili.type", "bangumi");
        metadata.put("bilibili.ep_id", selection.episodeId());
        if (selection.seasonId() != null) {
            metadata.put("bilibili.season_id", selection.seasonId());
        }
        metadata.putAll(resolved.diagnostics());
        var info = new MediaInfo(
                selection.displayTitle(),
                "Bilibili",
                selection.coverUrl(),
                "bilibili",
                metadata
        );

        return new PlatformMedia(resolved.playInfo(), info);
    }

    private static PlatformMedia resolveBilibiliLive(String target) {
        try {
            return resolveBilibiliLiveInternal(target);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve bilibili live: " + e.getMessage(), e);
        }
    }

    private static PlatformMedia resolveBilibiliLiveInternal(String input) throws Exception {
        var roomId = extractRoomIdFromInput(input);
        var realRoomId = getRealRoomId(roomId);
        var cookie = BilibiliCookie.getCookie();
        var streamUrl = getLiveStreamUrl(realRoomId, cookie);

        var roomInfoUrl = "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=" + realRoomId;
        var infoResponse = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(roomInfoUrl))
                .header("User-Agent", BILIBILI_UA)
                .build(), HttpResponse.BodyHandlers.ofString());
        var infoJson = parseObject(infoResponse.body());
        String title = "Bilibili Live";
        String uname = "Unknown";
        if (optInt(infoJson, "code", -1) == 0) {
            var liveData = infoJson.getAsJsonObject("data");
            var roomInfo = liveData.getAsJsonObject("room_info");
            title = optString(roomInfo, "title", title);
            var anchorInfo = optObject(liveData, "anchor_info");
            if (anchorInfo != null) {
                var baseInfo = optObject(anchorInfo, "base_info");
                if (baseInfo != null) {
                    uname = optString(baseInfo, "uname", uname);
                }
            }
        }

        var headers = new HashMap<String, String>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", "https://www.bilibili.com/");
        headers.put("Origin", "https://www.bilibili.com");

        var info = new MediaInfo(title, uname, null, "bilibili");
        return new PlatformMedia(new MediaPlayInfo(streamUrl, null, headers, cookie), info);
    }

    private static String extractRoomIdFromInput(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input 不能为空");
        }
        var liveMatcher = Pattern.compile("live\\.bilibili\\.com/(\\d+)").matcher(input);
        if (liveMatcher.find()) {
            return liveMatcher.group(1);
        }
        return input.trim();
    }

    private static String getRealRoomId(String roomId) throws Exception {
        var url = "https://api.live.bilibili.com/room/v1/Room/room_init?id=" + roomId;
        var response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", BILIBILI_UA)
                .build(), HttpResponse.BodyHandlers.ofString());
        var json = parseObject(response.body());
        if (optInt(json, "code", -1) != 0) {
            throw new IllegalStateException(optString(json, "message", "获取直播间信息失败"));
        }
        var data = json.getAsJsonObject("data");
        var liveStatus = optInt(data, "live_status", 0);
        if (liveStatus != 1) {
            throw new IllegalStateException("直播间未开播");
        }
        return String.valueOf(data.get("room_id").getAsLong());
    }

    private static String getLiveStreamUrl(String realRoomId, String cookie) throws Exception {
        var resolutionLimit = MediaResolverSettings.getResolutionLimit();

        // Step 1: request with qn=0 to discover available qualities
        var params = BilibiliWbiSign.createBaseParams(realRoomId, "0");
        BilibiliWbiSign.sign(params);
        var responseBody = doV2LiveRequest(params, cookie);
        var root = parseObject(responseBody);
        if (optInt(root, "code", -1) != 0) {
            throw new IllegalStateException("获取直播清晰度列表失败: " + optString(root, "message", ""));
        }
        var data = root.getAsJsonObject("data");

        var qualityOptions = optArray(data, "quality_description");
        int selectedQn = selectLiveQn(qualityOptions, resolutionLimit);
        if (qualityOptions != null && !qualityOptions.isEmpty()) {
            var available = new StringBuilder();
            for (int i = 0; i < qualityOptions.size(); i++) {
                if (!available.isEmpty()) available.append(", ");
                var q = qualityOptions.get(i).getAsJsonObject();
                available.append(q.get("qn")).append("=").append(q.get("desc"));
            }
            LOGGER.info("Bilibili live quality options: [{}], selected qn={}", available, selectedQn);
        }

        // Step 2: request with selected quality for stream URL
        params = BilibiliWbiSign.createBaseParams(realRoomId, String.valueOf(selectedQn));
        BilibiliWbiSign.sign(params);
        responseBody = doV2LiveRequest(params, cookie);
        root = parseObject(responseBody);
        if (optInt(root, "code", -1) != 0) {
            throw new IllegalStateException("获取直播流失败: " + optString(root, "message", ""));
        }
        data = root.getAsJsonObject("data");

        var url = extractV2StreamUrl(data);
        if (url == null) {
            throw new IllegalStateException("未找到可播放流");
        }
        return url;
    }

    private static String doV2LiveRequest(Map<String, String> params, String cookie) throws Exception {
        var query = toQueryString(params);
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?" + query))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://live.bilibili.com/");
        if (cookie != null && !cookie.isBlank()) {
            requestBuilder.header("Cookie", cookie);
        }
        var response = HTTP.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("请求直播API失败, status=" + response.statusCode());
        }
        return response.body();
    }

    private static String toQueryString(Map<String, String> params) {
        var sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    /** Navigates data.playurl_info.playurl.stream[].format[].codec[].url_info[]
     *  where base_url is at codec level and host/extra are at url_info level. */
    private static String extractV2StreamUrl(JsonObject data) {
        var playurlInfo = optObject(data, "playurl_info");
        if (playurlInfo == null) return null;
        var playurl = optObject(playurlInfo, "playurl");
        if (playurl == null) return null;
        var streams = optArray(playurl, "stream");
        if (streams == null || streams.isEmpty()) return null;
        for (var streamEl : streams) {
            var formats = optArray(streamEl.getAsJsonObject(), "format");
            if (formats == null || formats.isEmpty()) continue;
            for (var formatEl : formats) {
                var codecs = optArray(formatEl.getAsJsonObject(), "codec");
                if (codecs == null || codecs.isEmpty()) continue;
                for (var codecEl : codecs) {
                    var codec = codecEl.getAsJsonObject();
                    var baseUrl = optString(codec, "base_url", "");
                    if (baseUrl.isEmpty()) continue;
                    var urlInfos = optArray(codec, "url_info");
                    if (urlInfos == null || urlInfos.isEmpty()) continue;
                    var urlInfo = urlInfos.get(0).getAsJsonObject();
                    var host = optString(urlInfo, "host", "");
                    var extra = optString(urlInfo, "extra", "");
                    if (!host.isEmpty()) {
                        return host + baseUrl + extra;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Maps a height-based resolution limit to a Bilibili live qn value,
     * then selects the best available option not exceeding the limit.
     */
    private static int selectLiveQn(JsonArray qualityOptions, int maxHeight) {
        int targetQn = resolutionLimitToLiveQn(maxHeight);
        if (qualityOptions == null || qualityOptions.isEmpty()) {
            return targetQn;
        }

        int bestMatch = qualityOptions.get(0).getAsJsonObject().get("qn").getAsInt();
        for (int i = 0; i < qualityOptions.size(); i++) {
            int qn = qualityOptions.get(i).getAsJsonObject().get("qn").getAsInt();
            if (qn <= targetQn) {
                return qn;
            }
            bestMatch = qn;
        }
        return bestMatch;
    }

    private static int resolutionLimitToLiveQn(int maxHeight) {
        if (maxHeight <= 0) return 10000;
        if (maxHeight >= 1440) return 10000;
        if (maxHeight >= 1080) return 400;
        if (maxHeight >= 720) return 250;
        if (maxHeight >= 480) return 150;
        return 80;
    }

    private static PlatformMedia resolveNeteaseInternal(String target) throws Exception {
        var songId = extractNeteaseSongId(target);
        if (songId == null) {
            throw new IllegalArgumentException("未找到歌曲 ID");
        }

        var cookie = NeteaseCookie.getCookie();

        // Step 1: fetch song detail
        // c needs to be a JSON string containing the array, not a raw array
        var detailData = "{\"c\":\"[{\\\"id\\\":" + songId + "}]\",\"csrf_token\":\"\"}";
        var detailBody = NeteaseCrypto.encrypt(detailData);
        var detailResponse = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/weapi/v3/song/detail"))
                .header("User-Agent", NETEASE_UA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", "https://music.163.com/")
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(detailBody)))
                .build(), HttpResponse.BodyHandlers.ofString());
        var detailJson = parseObject(detailResponse.body());
        if (optInt(detailJson, "code", -1) != 200) {
            throw new IllegalStateException("获取歌曲信息失败: " + optString(detailJson, "message", ""));
        }

        var songs = optArray(detailJson, "songs");
        if (songs == null || songs.isEmpty()) {
            throw new IllegalStateException("未找到歌曲信息");
        }
        var song = songs.get(0).getAsJsonObject();
        var title = song.get("name").getAsString();
        var artists = song.getAsJsonArray("ar");
        var artist = artists != null && !artists.isEmpty()
                ? artists.get(0).getAsJsonObject().get("name").getAsString()
                : "Unknown";
        var al = optObject(song, "al");
        var coverUrl = al != null ? optString(al, "picUrl", null) : null;
        var duration = optInt(song, "duration", 0);

        // Step 2: fetch audio URL
        var bitrate = selectBestBitrate(MediaResolverSettings.getResolutionLimit());
        var level = bitrateToLevel(bitrate);
        // ids must be a JSON array, and v1 endpoint needs encodeType for some levels
        var playData = "{\"ids\":[" + songId + "],\"br\":" + bitrate + ",\"level\":\"" + level + "\",\"encodeType\":\"mp3\",\"csrf_token\":\"\"}";
        var playBody = NeteaseCrypto.encrypt(playData);
        var playRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/weapi/song/enhance/player/url/v1"))
                .header("User-Agent", NETEASE_UA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", "https://music.163.com/")
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(playBody)));
        if (cookie != null && !cookie.isBlank()) {
            playRequestBuilder.header("Cookie", cookie);
        }
        var playResponse = HTTP.send(playRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        var playJson = parseObject(playResponse.body());
        if (optInt(playJson, "code", -1) != 200) {
            throw new IllegalStateException("获取播放地址失败: " + optString(playJson, "message", ""));
        }
        var playDataArray = optArray(playJson, "data");
        if (playDataArray == null || playDataArray.isEmpty()) {
            throw new IllegalStateException("未找到播放数据");
        }
        var songPlayInfo = playDataArray.get(0).getAsJsonObject();
        var url = optString(songPlayInfo, "url", null);
        if (url == null || url.isBlank() || optInt(songPlayInfo, "code", -1) != 200) {
            throw new IllegalStateException("未找到可播放的音频 URL（歌曲可能需要 VIP 或登录）");
        }

        var metadata = new HashMap<String, String>();
        metadata.put("netease.song_id", songId);
        metadata.put("netease.duration", String.valueOf(duration));
        metadata.put("netease.level", level);
        var info = new MediaInfo(title, artist, coverUrl, "netease", metadata);

        var headers = new HashMap<String, String>();
        headers.put("User-Agent", NETEASE_UA);
        headers.put("Referer", "https://music.163.com/");
        headers.put("Origin", "https://music.163.com");

        return new PlatformMedia(new MediaPlayInfo(url, null, headers, cookie), info);
    }

    private static String extractNeteaseSongId(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }

        var trimmed = target.trim();

        if (PURE_DIGIT_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        var matcher = SONG_ID_URL_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }

        matcher = SONG_ID_HASH_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private static int selectBestBitrate(int maxHeight) {
        // Map height to bitrate: 0 = max, >=1080 = lossless, 720 = 320k, 480 = 192k, else 128k
        if (maxHeight <= 0) return 999000; // lossless
        if (maxHeight >= 1080) return 999000;
        if (maxHeight >= 720) return 320000;
        if (maxHeight >= 480) return 192000;
        return 128000;
    }

    private static String bitrateToLevel(int bitrate) {
        if (bitrate >= 999000) return "lossless";
        if (bitrate >= 320000) return "exhigh";
        if (bitrate >= 192000) return "high";
        return "standard";
    }

    private static String toFormBody(Map<String, String> params) {
        var sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static PlatformMedia resolveDouyinInternal(String target) throws Exception {
        var shareUrl = normalizeDouyinUrl(target);
        if (shareUrl == null) {
            throw new IllegalArgumentException("未找到抖音分享链接");
        }

        var firstResponse = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(shareUrl))
                .header("User-Agent", DOUYIN_UA)
                .build(), HttpResponse.BodyHandlers.discarding());
        var finalUrl = firstResponse.uri().toString();
        var videoId = extractDouyinVideoId(finalUrl);
        if (videoId == null) {
            throw new IllegalArgumentException("未找到抖音视频 ID");
        }

        var videoPageUrl = "https://www.iesdouyin.com/share/video/" + videoId;
        var videoPageResponse = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(videoPageUrl))
                .header("User-Agent", DOUYIN_UA)
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (videoPageResponse.statusCode() / 100 != 2) {
            throw new IllegalStateException("请求抖音详情页失败");
        }

        var routerDataJson = extractDouyinRouterData(videoPageResponse.body());
        if (routerDataJson == null) {
            throw new IllegalStateException("无法提取抖音页面数据");
        }

        var root = parseObject(routerDataJson);
        var loaderData = root.getAsJsonObject("loaderData");
        var videoPage = loaderData.getAsJsonObject("video_(id)/page");
        var videoInfoRes = videoPage.getAsJsonObject("videoInfoRes");
        var item = videoInfoRes.getAsJsonArray("item_list").get(0).getAsJsonObject();
        var video = item.getAsJsonObject("video");
        var playAddr = video.getAsJsonObject("play_addr");
        var url = normalizeDouyinPlayUrl(playAddr.getAsJsonArray("url_list").get(0).getAsString());

        var info = new MediaInfo(
                item.get("desc").getAsString(),
                item.getAsJsonObject("author").get("nickname").getAsString(),
                null,
                "douyin"
        );

        var headers = new HashMap<String, String>();
        headers.put("User-Agent", DOUYIN_UA);
        headers.put("Referer", "https://www.douyin.com/");
        headers.put("Origin", "https://www.douyin.com/");

        return new PlatformMedia(new MediaPlayInfo(url, null, headers, null), info);
    }

    private static Map<String, String> createBilibiliMediaHeaders() {
        var headers = new HashMap<String, String>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", "https://www.bilibili.com/");
        headers.put("Origin", "https://www.bilibili.com");
        return headers;
    }

    /**
     * 按清晰度阶梯依次请求播放地址：先尝试高清晰度（含登录后可用的会员档），
     * 当前档不可用或未返回该档时降级到下一档。
     */
    private static BilibiliResolvedPlayInfo resolveBilibiliPlayInfoWithFallback(String basePlayApi, String payloadPath, Map<String, String> headers, boolean rejectRestrictedPlayback) throws Exception {
        int maxHeight = MediaResolverSettings.getResolutionLimit();
        int[] candidates = bilibiliQnCandidates(maxHeight);
        String lastMessage = null;
        for (int attempt = 0; attempt < candidates.length; attempt++) {
            int qn = candidates[attempt];
            String playApi = basePlayApi + bilibiliPlayUrlOptions(qn);
            var playRequestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(playApi))
                    .header("User-Agent", BILIBILI_UA)
                    .header("Referer", "https://www.bilibili.com/");
            String cookie = addBilibiliCookie(playRequestBuilder, playApi);
            HttpResponse<String> playResponse = HTTP.send(playRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            var playJson = parseObject(playResponse.body());
            if (optInt(playJson, "code", -1) != 0) {
                lastMessage = optString(playJson, "message", "qn=" + qn + " 获取播放地址失败");
                LOGGER.info("Bilibili playurl qn={} unavailable: {}", qn, lastMessage);
                continue;
            }
            var payload = optObjectPath(playJson, payloadPath);
            if (payload == null) {
                lastMessage = "qn=" + qn + " 响应缺少 " + payloadPath;
                LOGGER.info("Bilibili playurl qn={} response missing payload '{}'", qn, payloadPath);
                continue;
            }
            if (rejectRestrictedPlayback) {
                var restriction = bilibiliPlaybackRestriction(payload);
                if (restriction != null) {
                    throw new IllegalStateException(restriction);
                }
            }
            var resolved = extractBilibiliPlayInfo(payload, headers, cookie, maxHeight, qn);
            if (resolved == null) {
                lastMessage = "qn=" + qn + " 未找到可播放流";
                LOGGER.info("Bilibili playurl qn={} returned no playable stream", qn);
                continue;
            }
            resolved.diagnostics().put("bilibili.qn_attempt", String.valueOf(attempt + 1));
            resolved.diagnostics().put("bilibili.qn_candidates", joinQnCandidates(candidates));
            if (isBilibiliQnSatisfied(qn, resolved, attempt == candidates.length - 1)) {
                LOGGER.info("Selected Bilibili qn request={} selected={} {}x{} fps={} bandwidth={} codecid={}",
                        qn,
                        resolved.diagnostics().getOrDefault("bilibili.selected_qn", "unknown"),
                        resolved.diagnostics().getOrDefault("bilibili.selected_width", "unknown"),
                        resolved.diagnostics().getOrDefault("bilibili.selected_height", "unknown"),
                        resolved.diagnostics().getOrDefault("bilibili.selected_fps", "unknown"),
                        resolved.diagnostics().getOrDefault("bilibili.selected_bandwidth", "unknown"),
                        resolved.diagnostics().getOrDefault("bilibili.selected_codecid", "unknown"));
                return resolved;
            }
            lastMessage = "qn=" + qn + " 只返回 qn=" + resolved.diagnostics().getOrDefault("bilibili.selected_qn", "unknown");
            LOGGER.info("Bilibili playurl requested qn={} but selected {}, trying fallback", qn, resolved.diagnostics().getOrDefault("bilibili.selected_qn", "unknown"));
        }
        throw new IllegalStateException(lastMessage == null ? "未找到可播放流" : "未找到可播放流：" + lastMessage);
    }

    private static String bilibiliPlayUrlOptions(int qn) {
        int fnval = qn <= BILIBILI_QN_1080P ? 16 : 4048;
        var options = "&qn=" + qn + "&fnver=0&fnval=" + fnval;
        if (qn >= BILIBILI_QN_4K) {
            options += "&fourk=1";
        }
        return options;
    }

    /** 依据分辨率上限与登录状态构建清晰度尝试阶梯；登录（有 cookie/VIP）时加入会员专属高码率档。 */
    private static int[] bilibiliQnCandidates(int maxHeight) {
        var status = BilibiliAuthManager.getInstance().getAccountStatus();
        boolean vipLoggedIn = status != null && status.isLoggedIn && status.isVip;
        var cookie = BilibiliCookie.getCookie();
        boolean hasCookie = cookie != null && !cookie.isBlank();
        boolean canTryAuthenticatedHighBitrate = vipLoggedIn || hasCookie;

        if (maxHeight <= 0) {
            return canTryAuthenticatedHighBitrate
                    ? new int[]{BILIBILI_QN_8K, BILIBILI_QN_4K, BILIBILI_QN_1080P_HIGH, BILIBILI_QN_1080P_60, BILIBILI_QN_1080P, BILIBILI_QN_720P}
                    : new int[]{BILIBILI_QN_8K, BILIBILI_QN_4K, BILIBILI_QN_1080P_60, BILIBILI_QN_1080P, BILIBILI_QN_720P};
        }
        if (maxHeight <= 720) {
            return new int[]{BILIBILI_QN_720P};
        }
        if (maxHeight <= 1080) {
            return canTryAuthenticatedHighBitrate
                    ? new int[]{BILIBILI_QN_1080P_HIGH, BILIBILI_QN_1080P_60, BILIBILI_QN_1080P, BILIBILI_QN_720P}
                    : new int[]{BILIBILI_QN_1080P_60, BILIBILI_QN_1080P, BILIBILI_QN_720P};
        }
        if (maxHeight <= 2160) {
            return canTryAuthenticatedHighBitrate
                    ? new int[]{BILIBILI_QN_4K, BILIBILI_QN_1080P_HIGH, BILIBILI_QN_1080P_60, BILIBILI_QN_1080P, BILIBILI_QN_720P}
                    : new int[]{BILIBILI_QN_4K, BILIBILI_QN_1080P_60, BILIBILI_QN_1080P, BILIBILI_QN_720P};
        }
        return canTryAuthenticatedHighBitrate
                ? new int[]{BILIBILI_QN_8K, BILIBILI_QN_4K, BILIBILI_QN_1080P_HIGH, BILIBILI_QN_1080P_60, BILIBILI_QN_1080P, BILIBILI_QN_720P}
                : new int[]{BILIBILI_QN_8K, BILIBILI_QN_4K, BILIBILI_QN_1080P_60, BILIBILI_QN_1080P, BILIBILI_QN_720P};
    }

    /** 返回非 null 表示该视频被限制播放（DRM/预览），应直接失败而非降级。 */
    private static String bilibiliPlaybackRestriction(JsonObject payload) {
        if (optBoolean(payload, "is_drm", false)) {
            return "该B站视频使用DRM保护，客户端无法播放";
        }
        if (optInt(payload, "is_preview", 0) == 1) {
            var status = BilibiliAuthManager.getInstance().getAccountStatus();
            var cookie = BilibiliCookie.getCookie();
            return bilibiliPreviewRestrictionMessage(cookie != null && !cookie.isBlank(), status != null && status.isLoggedIn, status != null && status.isVip);
        }
        return null;
    }

    private static String bilibiliPreviewRestrictionMessage(boolean hasCookie, boolean loggedIn, boolean vip) {
        if (!hasCookie) {
            return "该视频需要 B 站大会员权限，请在本机扫码登录有权限的账号后重试";
        }
        if (!loggedIn) {
            return "本机 B 站登录已失效，请重新扫码登录有权限的账号后重试";
        }
        if (!vip) {
            return "该视频需要 B 站大会员权限，当前本机账号无权限播放";
        }
        return "B 站仅返回试看流，请确认本机大会员有效且该内容在当前地区可观看";
    }

    private static boolean isBilibiliQnSatisfied(int requestedQn, BilibiliResolvedPlayInfo resolved, boolean lastCandidate) {
        if (lastCandidate) {
            return true;
        }
        int selectedQn = parseInt(resolved.diagnostics().get("bilibili.selected_qn"), -1);
        if (selectedQn <= 0) {
            return requestedQn <= BILIBILI_QN_720P;
        }
        return selectedQn == requestedQn;
    }

    private static BilibiliResolvedPlayInfo extractBilibiliPlayInfo(JsonObject data, Map<String, String> headers, String cookie, int maxHeight, int requestedQn) {
        if (data != null && data.has("dash")) {
            var dash = data.getAsJsonObject("dash");
            var video = optArray(dash, "video");
            var audio = optArray(dash, "audio");
            if (video != null && !video.isEmpty()) {
                var bestVideo = selectBestVideoTrack(video, maxHeight);
                var videoUrl = bestVideo.get("baseUrl").getAsString();
                var diagnostics = new HashMap<String, String>();
                fillBilibiliTrackDiagnostics(diagnostics, data, bestVideo, requestedQn);
                String bestAudio = null;
                if (audio != null && !audio.isEmpty()) {
                    var audioTrack = audio.get(0).getAsJsonObject();
                    bestAudio = audioTrack.get("baseUrl").getAsString();
                    diagnostics.put("bilibili.selected_audio_bandwidth", String.valueOf(optInt(audioTrack, "bandwidth", 0)));
                    diagnostics.put("bilibili.selected_audio_id", String.valueOf(optInt(audioTrack, "id", 0)));
                }
                return new BilibiliResolvedPlayInfo(
                        new MediaPlayInfo(videoUrl, bestAudio, headers, trustedBilibiliMediaCookie(cookie, videoUrl, bestAudio)),
                        diagnostics
                );
            }
        }

        var durl = data == null ? null : optArray(data, "durl");
        if (durl != null && !durl.isEmpty()) {
            var videoUrl = durl.get(0).getAsJsonObject().get("url").getAsString();
            var diagnostics = new HashMap<String, String>();
            diagnostics.put("bilibili.requested_qn", String.valueOf(requestedQn));
            diagnostics.put("bilibili.selected_qn", String.valueOf(optInt(data, "quality", requestedQn)));
            diagnostics.put("bilibili.selected_stream_type", "durl");
            return new BilibiliResolvedPlayInfo(
                    new MediaPlayInfo(videoUrl, null, headers, trustedBilibiliMediaCookie(cookie, videoUrl, null)),
                    diagnostics
            );
        }
        return null;
    }

    private static void fillBilibiliTrackDiagnostics(Map<String, String> diagnostics, JsonObject data, JsonObject bestVideo, int requestedQn) {
        diagnostics.put("bilibili.requested_qn", String.valueOf(requestedQn));
        diagnostics.put("bilibili.selected_qn", String.valueOf(optInt(bestVideo, "id", 0)));
        diagnostics.put("bilibili.selected_width", String.valueOf(optInt(bestVideo, "width", 0)));
        diagnostics.put("bilibili.selected_height", String.valueOf(optInt(bestVideo, "height", 0)));
        diagnostics.put("bilibili.selected_bandwidth", String.valueOf(optInt(bestVideo, "bandwidth", 0)));
        diagnostics.put("bilibili.selected_codecid", String.valueOf(optInt(bestVideo, "codecid", 0)));
        diagnostics.put("bilibili.selected_fps", optString(bestVideo, "frameRate", optString(bestVideo, "frame_rate", String.valueOf(normalizedFrameRate(bestVideo)))));
        diagnostics.put("bilibili.selected_stream_type", "dash");
        var acceptQuality = optArray(data, "accept_quality");
        if (acceptQuality != null && !acceptQuality.isEmpty()) {
            diagnostics.put("bilibili.accept_quality", joinJsonArrayValues(acceptQuality));
        }
    }

    private static JsonObject selectBestVideoTrack(JsonArray video, int maxHeight) {
        if (maxHeight <= 0) {
            return video.get(0).getAsJsonObject();
        }

        var threshold = (int) (maxHeight * 1.2f);
        JsonObject bestAllowed = null;
        JsonObject fallback = null;
        for (int i = 0; i < video.size(); i++) {
            var track = video.get(i).getAsJsonObject();
            int height = optInt(track, "height", 0);
            if (height <= 0) {
                continue;
            }
            if (height <= threshold) {
                if (bestAllowed != null && compareBilibiliVideoTrack(track, bestAllowed) <= 0) {
                    continue;
                }
                bestAllowed = track;
            } else {
                if (fallback != null && height >= optInt(fallback, "height", Integer.MAX_VALUE)) {
                    continue;
                }
                fallback = track;
            }
        }
        if (bestAllowed != null) {
            return bestAllowed;
        }
        return fallback != null ? fallback : video.get(video.size() - 1).getAsJsonObject();
    }

    /** 同等高度优先高帧率，再优先高码率。 */
    private static int compareBilibiliVideoTrack(JsonObject left, JsonObject right) {
        int leftHeight = optInt(left, "height", 0);
        int rightHeight = optInt(right, "height", 0);
        if (leftHeight != rightHeight) {
            return Integer.compare(leftHeight, rightHeight);
        }
        int leftFps = normalizedFrameRate(left);
        int rightFps = normalizedFrameRate(right);
        if (leftFps != rightFps) {
            return Integer.compare(leftFps, rightFps);
        }
        return Integer.compare(optInt(left, "bandwidth", 0), optInt(right, "bandwidth", 0));
    }

    private static int normalizedFrameRate(JsonObject track) {
        var value = optString(track, "frameRate", optString(track, "frame_rate", null));
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            var normalized = value.trim();
            int slash = normalized.indexOf('/');
            if (slash > 0) {
                double numerator = Double.parseDouble(normalized.substring(0, slash));
                double denominator = Double.parseDouble(normalized.substring(slash + 1));
                return denominator == 0 ? 0 : (int) Math.round(numerator / denominator);
            }
            return (int) Math.round(Double.parseDouble(normalized));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** 仅对受信 B 站域名附带 Cookie，避免账号信息泄漏到第三方媒体 CDN。 */
    private static String addBilibiliCookie(HttpRequest.Builder builder, String url) {
        var cookie = BilibiliCookie.getCookie();
        if (cookie == null || cookie.isBlank()) {
            return null;
        }
        if (isTrustedBilibiliHost(hostOf(url))) {
            builder.header("Cookie", cookie);
            return cookie;
        }
        LOGGER.warn("Omit Bilibili cookie for non-Bilibili request host '{}'", hostOf(url));
        return null;
    }

    private static String trustedBilibiliMediaCookie(String cookie, String videoUrl, String audioUrl) {
        if (cookie == null || cookie.isBlank()) {
            return null;
        }
        if (!isTrustedBilibiliMediaUrl(videoUrl) || audioUrl != null && !audioUrl.isBlank() && !isTrustedBilibiliMediaUrl(audioUrl)) {
            LOGGER.warn("Omit Bilibili cookie for media hosts video='{}', audio='{}'", hostOf(videoUrl), hostOf(audioUrl));
            return null;
        }
        return cookie;
    }

    private static boolean isTrustedBilibiliMediaUrl(String url) {
        var host = hostOf(url);
        if (host == null || host.isBlank()) {
            return false;
        }
        var lower = host.toLowerCase(Locale.ROOT);
        return isTrustedBilibiliHost(lower) || lower.equals("bilivideo.com") || lower.endsWith(".bilivideo.com");
    }

    private static boolean isTrustedBilibiliHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        var lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("bilibili.com") || lower.endsWith(".bilibili.com")
                || lower.equals("b23.tv") || lower.endsWith(".b23.tv");
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String resolveBilibiliSourceUrl(String target) throws Exception {
        var normalizedUrl = normalizeBilibiliUrl(target);
        if (parseBvidFromUrl(normalizedUrl) != null || isBilibiliBangumiUrl(normalizedUrl)) {
            return normalizedUrl;
        }

        var redirected = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(normalizedUrl))
                .header("User-Agent", BILIBILI_UA)
                .header("Referer", "https://www.bilibili.com/")
                .build(), HttpResponse.BodyHandlers.discarding());
        return redirected.uri().toString();
    }

    private static String normalizeBilibiliUrl(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target 不能为空");
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        }
        return "https://www.bilibili.com/video/" + target;
    }

    /**
     * Bilibili returns http cover URLs; hdslb.com serves https too, so emit https
     * to keep the strict cover downloader from tripping over an http-&gt;https redirect.
     */
    private static String normalizeBilibiliCoverUrl(String coverUrl) {
        if (coverUrl == null || coverUrl.isBlank()) {
            return null;
        }
        String trimmed = coverUrl.trim();
        return trimmed.startsWith("http://") ? "https://" + trimmed.substring("http://".length()) : trimmed;
    }

    private static int parsePNumberFromUrl(String url) {
        var matcher = Pattern.compile("[?&]p=(\\d+)").matcher(url);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }

    private static boolean isBilibiliBangumiUrl(String url) {
        return Pattern.compile("(?:https?://)?(?:[\\w-]+\\.)?bilibili\\.com/bangumi/play/(?:ep|ss)\\d+([?/].*)?")
                .matcher(url)
                .matches();
    }

    private static String parseBangumiEpIdFromUrl(String url) {
        var matcher = Pattern.compile("/ep(\\d+)").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String parseBangumiSeasonIdFromUrl(String url) {
        var matcher = Pattern.compile("/ss(\\d+)").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static BangumiEpisodeSelection selectBangumiEpisode(JsonObject result, String sourceUrl, String requestedEpisodeId) {
        var episodes = optArray(result, "episodes");
        if (episodes == null || episodes.isEmpty()) {
            throw new IllegalStateException("未找到番剧剧集信息");
        }

        JsonObject selectedEpisode = null;
        if (requestedEpisodeId != null) {
            for (int i = 0; i < episodes.size(); i++) {
                var episode = episodes.get(i).getAsJsonObject();
                if (requestedEpisodeId.equals(optString(episode, "id", null))) {
                    selectedEpisode = episode;
                    break;
                }
            }
        }
        if (selectedEpisode == null) {
            var index = Math.max(0, Math.min(parsePNumberFromUrl(sourceUrl) - 1, episodes.size() - 1));
            selectedEpisode = episodes.get(index).getAsJsonObject();
        }

        var episodeId = optString(selectedEpisode, "id", requestedEpisodeId);
        if (episodeId == null || episodeId.isBlank()) {
            throw new IllegalStateException("未找到番剧 ep_id");
        }
        var cidElement = selectedEpisode.get("cid");
        if (cidElement == null || cidElement.isJsonNull()) {
            throw new IllegalStateException("未找到番剧 cid");
        }

        var seasonTitle = optString(result, "title", "Bilibili 番剧");
        var episodeTitle = optString(selectedEpisode, "share_copy",
                optString(selectedEpisode, "long_title", optString(selectedEpisode, "title", episodeId)));
        var displayTitle = seasonTitle.equals(episodeTitle) ? seasonTitle : seasonTitle + " - " + episodeTitle;
        var coverUrl = normalizeBilibiliCoverUrl(optString(selectedEpisode, "cover", optString(result, "cover", null)));
        return new BangumiEpisodeSelection(
                episodeId,
                cidElement.getAsLong(),
                optString(result, "season_id", null),
                displayTitle,
                coverUrl
        );
    }

    private static String parseBvidFromUrl(String url) {
        var matcher = Pattern.compile("(BV[a-zA-Z0-9]+)").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String normalizeDouyinUrl(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        }
        return "https://v.douyin.com/" + target;
    }

    private static String extractDouyinVideoId(String url) {
        var parts = url.split("\\?");
        var path = parts[0];
        var segments = path.split("/");
        if (segments.length == 0) {
            return null;
        }
        var last = segments[segments.length - 1];
        return last.isEmpty() && segments.length > 1 ? segments[segments.length - 2] : last;
    }

    private static String normalizeDouyinPlayUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("未找到抖音播放地址");
        }

        var normalized = url.replace("playwm", "play");
        try {
            var uri = URI.create(normalized);
            var query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return normalized;
            }
            for (var param : query.split("&")) {
                if (!param.startsWith("video_id=")) {
                    continue;
                }
                var value = java.net.URLDecoder.decode(param.substring("video_id=".length()), StandardCharsets.UTF_8);
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    return value;
                }
            }
            return normalized;
        } catch (IllegalArgumentException ignored) {
            return normalized;
        }
    }

    private static String extractDouyinRouterData(String html) {
        var matcher = Pattern.compile("window\\._ROUTER_DATA\\s*=\\s*(\\{.*?})</script>", Pattern.DOTALL).matcher(html);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonObject optObject(JsonObject object, String key) {
        var element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonObject optObjectPath(JsonObject object, String path) {
        JsonObject current = object;
        for (var key : path.split("\\.")) {
            current = optObject(current, key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static JsonArray optArray(JsonObject object, String key) {
        var element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String optString(JsonObject object, String key, String defaultValue) {
        var element = object.get(key);
        return isPresent(element) ? element.getAsString() : defaultValue;
    }

    private static int optInt(JsonObject object, String key, int defaultValue) {
        var element = object.get(key);
        return isPresent(element) ? element.getAsInt() : defaultValue;
    }

    private static boolean optBoolean(JsonObject object, String key, boolean defaultValue) {
        var element = object.get(key);
        return isPresent(element) ? element.getAsBoolean() : defaultValue;
    }

    private static boolean isPresent(JsonElement element) {
        return element != null && !element.isJsonNull();
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String joinQnCandidates(int[] candidates) {
        var builder = new StringBuilder();
        for (int i = 0; i < candidates.length; i++) {
            if (i > 0) {
                builder.append("->");
            }
            builder.append(candidates[i]);
        }
        return builder.toString();
    }

    private static String joinJsonArrayValues(JsonArray values) {
        var builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values.get(i).getAsString());
        }
        return builder.toString();
    }

    private record BilibiliResolvedPlayInfo(MediaPlayInfo playInfo, Map<String, String> diagnostics) {
    }

    private record BangumiEpisodeSelection(String episodeId, long cid, String seasonId, String displayTitle,
                                           String coverUrl) {
    }
}

package top.tobyprime.mcedia_platforms.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.media.MediaCollection;
import top.tobyprime.mcedia.api.media.MediaCollectionItem;
import top.tobyprime.mcedia.api.resolver.MediaCollectionResolver;
import top.tobyprime.mcedia_platforms.auth.BilibiliCookie;
import top.tobyprime.mcedia_platforms.auth.BilibiliWbiSign;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 将 bilibili 番剧（ss 季度 / ep 单集）与收藏夹（ml）链接解析为 {@link MediaCollection}。
 * ep 单集链接会按 ep_id 反查整个季度，因此同样以整季作为专辑产出。
 * 每个条目携带可再次交给 MediaResolvers.resolve 的解析目标（ep 链接 / BV 链接），
 * 条目本身不做网络解析，由消费方按需惰性解析。
 */
public final class BilibiliCollectionResolver implements MediaCollectionResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliCollectionResolver.class);
    private static final String UA = "Mozilla/5.0";
    private static final String BASE_DOMAIN = "(?:[\\w-]+\\.)?bilibili\\.com";

    private static final Pattern SEASON_PATTERN = Pattern.compile(BASE_DOMAIN + "/bangumi/play/ss(\\d+)");
    private static final Pattern EPISODE_PATTERN = Pattern.compile(BASE_DOMAIN + "/bangumi/play/ep(\\d+)");
    private static final Pattern FAVORITES_PATTERN = Pattern.compile(BASE_DOMAIN + "/list/ml(\\d+)");
    private static final Pattern BVID_PATTERN = Pattern.compile("(BV[a-zA-Z0-9]+)");

    private static final int MAX_FAVORITES_ITEMS = 100;
    private static final int MAX_VIDEO_ITEMS = 200;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    @Override
    public Optional<MediaCollection> tryResolveCollection(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        try {
            var seasonMatcher = SEASON_PATTERN.matcher(target);
            if (seasonMatcher.find()) {
                return Optional.of(resolveBangumi("season_id", seasonMatcher.group(1)));
            }
            var episodeMatcher = EPISODE_PATTERN.matcher(target);
            if (episodeMatcher.find()) {
                return Optional.of(resolveBangumi("ep_id", episodeMatcher.group(1)));
            }
            var favoritesMatcher = FAVORITES_PATTERN.matcher(target);
            if (favoritesMatcher.find()) {
                return Optional.of(resolveFavorites(favoritesMatcher.group(1)));
            }
            var bvidMatcher = BVID_PATTERN.matcher(target);
            if (bvidMatcher.find()) {
                var videoCollection = resolveVideoCollection(bvidMatcher.group(1));
                if (videoCollection.isPresent()) {
                    return videoCollection;
                }
            }
        } catch (Exception e) {
            LOGGER.info("Failed to resolve bilibili collection: target={}, reason={}", target, e.getMessage());
        }
        return Optional.empty();
    }

    /** 纯 URL 形态检测：target 是否是 bilibili 番剧/收藏夹链接（不发起网络请求）。 */
    static boolean isCollectionTarget(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        return SEASON_PATTERN.matcher(target).find()
                || EPISODE_PATTERN.matcher(target).find()
                || FAVORITES_PATTERN.matcher(target).find();
    }

    /** 通过 season_id 或 ep_id 查询番剧，返回整季专辑。ep 链接用 ep_id 反查同样返回完整季度。 */
    private MediaCollection resolveBangumi(String idKey, String idValue) throws Exception {
        var api = "https://api.bilibili.com/pgc/view/web/season?" + idKey + "=" + idValue;
        var response = http.send(HttpRequest.newBuilder()
                .uri(URI.create(api))
                .header("User-Agent", UA)
                .header("Referer", "https://www.bilibili.com/")
                .build(), HttpResponse.BodyHandlers.ofString());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (optInt(json, "code", -1) != 0) {
            throw new IllegalStateException(optString(json, "message", "获取番剧信息失败"));
        }
        var result = json.getAsJsonObject("result");
        var title = optString(result, "title", "Bilibili 番剧");
        var cover = normalizeCoverUrl(optString(result, "cover", null));
        var episodes = optArray(result, "episodes");
        var items = new ArrayList<MediaCollectionItem>();
        if (episodes != null) {
            for (var element : episodes) {
                var episode = element.getAsJsonObject();
                var episodeId = optString(episode, "id", null);
                if (episodeId == null || episodeId.isBlank()) {
                    continue;
                }
                var episodeTitle = episodeDisplayTitle(episode);
                items.add(new BilibiliCollectionItem(
                        episodeTitle,
                        normalizeCoverUrl(optString(episode, "cover", null)),
                        "https://www.bilibili.com/bangumi/play/ep" + episodeId
                ));
            }
        }
        return new BilibiliCollection(title, cover, items);
    }

    private MediaCollection resolveFavorites(String mediaId) throws Exception {
        var title = "Bilibili 收藏夹";
        String cover = null;
        var items = new ArrayList<MediaCollectionItem>();
        for (int page = 1; page <= (MAX_FAVORITES_ITEMS + 19) / 20; page++) {
            var params = new java.util.LinkedHashMap<String, String>();
            params.put("media_id", mediaId);
            params.put("pn", String.valueOf(page));
            params.put("ps", "20");
            params.put("platform", "web");
            BilibiliWbiSign.sign(params);
            var api = "https://api.bilibili.com/x/v3/fav/resource/list?" + toQueryString(params);
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(api))
                    .header("User-Agent", UA)
                    .header("Referer", "https://www.bilibili.com/");
            var cookie = BilibiliCookie.getCookie();
            if (cookie != null && !cookie.isBlank()) {
                requestBuilder.header("Cookie", cookie);
            }
            var response = http.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            var json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (optInt(json, "code", -1) != 0) {
                throw new IllegalStateException(optString(json, "message", "获取收藏夹信息失败"));
            }
            var data = json.getAsJsonObject("data");
            var info = optObject(data, "info");
            if (info != null) {
                title = optString(info, "title", title);
                if (cover == null) {
                    cover = normalizeCoverUrl(optString(info, "cover", null));
                }
            }
            var medias = optArray(data, "medias");
            if (medias == null || medias.isEmpty()) {
                break;
            }
            for (var element : medias) {
                if (items.size() >= MAX_FAVORITES_ITEMS) {
                    break;
                }
                var media = element.getAsJsonObject();
                var bvid = optString(media, "bvid", null);
                if (bvid == null || bvid.isBlank()) {
                    continue;
                }
                items.add(new BilibiliCollectionItem(
                        optString(media, "title", bvid),
                        normalizeCoverUrl(optString(media, "cover", null)),
                        "https://www.bilibili.com/video/" + bvid
                ));
            }
            boolean hasMore = optBoolean(data, "has_more", false);
            if (!hasMore || items.size() >= MAX_FAVORITES_ITEMS) {
                break;
            }
        }
        return new BilibiliCollection(title, cover, items);
    }

    /** 根据视频 BV 查询所属合集(ugc_season)或多分P,均不存在则 empty。 */
    private Optional<MediaCollection> resolveVideoCollection(String bvid) throws Exception {
        var api = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(api))
                .header("User-Agent", UA)
                .header("Referer", "https://www.bilibili.com/");
        var cookie = BilibiliCookie.getCookie();
        if (cookie != null && !cookie.isBlank()) {
            requestBuilder.header("Cookie", cookie);
        }
        var response = http.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (optInt(json, "code", -1) != 0) {
            return Optional.empty();
        }
        return buildVideoCollection(json.getAsJsonObject("data"));
    }

    /** 从 view API 的 data 构建视频合集;纯函数便于单元测试,不发网络请求。
     *  优先级: 视频所属合集(ugc_season) > 多分P;两者皆无返回 empty。 */
    static Optional<MediaCollection> buildVideoCollection(JsonObject data) {
        if (data == null) {
            return Optional.empty();
        }
        var season = optObject(data, "ugc_season");
        if (season != null) {
            var episodes = optArray(season, "episodes");
            if (episodes != null && !episodes.isEmpty()) {
                return Optional.of(buildUgcSeasonCollection(season, episodes));
            }
        }
        var pages = optArray(data, "pages");
        if (pages != null && pages.size() > 1 && isNotBlank(optString(data, "bvid", null))) {
            return Optional.of(buildMultiPageCollection(data, pages));
        }
        return Optional.empty();
    }

    private static MediaCollection buildUgcSeasonCollection(JsonObject season, JsonArray episodes) {
        var title = optString(season, "title", "Bilibili 合集");
        var cover = normalizeCoverUrl(optString(season, "cover", null));
        var items = new ArrayList<MediaCollectionItem>();
        for (var element : episodes) {
            if (items.size() >= MAX_VIDEO_ITEMS) {
                break;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            var episode = element.getAsJsonObject();
            var bvid = optString(episode, "bvid", null);
            if (isBlank(bvid)) {
                continue;
            }
            var page = optInt(episode, "page", 1);
            items.add(new BilibiliCollectionItem(
                    optString(episode, "title", "P" + page),
                    normalizeCoverUrl(optString(episode, "cover", null)),
                    videoTarget(bvid, page)
            ));
        }
        return new BilibiliCollection(title, cover, items);
    }

    private static MediaCollection buildMultiPageCollection(JsonObject data, JsonArray pages) {
        var bvid = optString(data, "bvid", null);
        var title = optString(data, "title", "Bilibili 视频");
        var cover = normalizeCoverUrl(optString(data, "pic", null));
        var items = new ArrayList<MediaCollectionItem>();
        int nextPage = 1;
        for (var element : pages) {
            if (items.size() >= MAX_VIDEO_ITEMS) {
                break;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            var pageObj = element.getAsJsonObject();
            var page = optInt(pageObj, "page", nextPage);
            nextPage = page + 1;
            var partTitle = optString(pageObj, "part", "P" + page);
            items.add(new BilibiliCollectionItem(
                    partTitle,
                    null,
                    videoTarget(bvid, page)
            ));
        }
        return new BilibiliCollection(title, cover, items);
    }

    private static String videoTarget(String bvid, int page) {
        return "https://www.bilibili.com/video/" + bvid + "?p=" + page;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    private static String episodeDisplayTitle(JsonObject episode) {
        var shareCopy = optString(episode.get("share_copy"), "");
        if (!shareCopy.isBlank()) {
            return shareCopy;
        }
        var longTitle = optString(episode.get("long_title"), "");
        if (!longTitle.isBlank()) {
            return longTitle;
        }
        return optString(episode.get("title"), optString(episode.get("id"), ""));
    }

    private static String toQueryString(java.util.Map<String, String> params) {
        var sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String normalizeCoverUrl(String coverUrl) {
        if (coverUrl == null || coverUrl.isBlank()) {
            return null;
        }
        String trimmed = coverUrl.trim();
        return trimmed.startsWith("http://") ? "https://" + trimmed.substring("http://".length()) : trimmed;
    }

    private static JsonObject optObject(JsonObject object, String key) {
        var element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray optArray(JsonObject object, String key) {
        var element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String optString(JsonObject object, String key, String defaultValue) {
        var element = object.get(key);
        return isPresent(element) ? element.getAsString() : defaultValue;
    }

    private static String optString(JsonElement element, String defaultValue) {
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

    private record BilibiliCollection(String title, String coverUrl, List<MediaCollectionItem> items)
            implements MediaCollection {
        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getCoverUrl() {
            return coverUrl;
        }

        @Override
        public List<MediaCollectionItem> getItems() {
            return items;
        }
    }

    private record BilibiliCollectionItem(String title, String coverUrl, String resolutionTarget)
            implements MediaCollectionItem {
        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getCoverUrl() {
            return coverUrl;
        }

        @Override
        public String getResolutionTarget() {
            return resolutionTarget;
        }
    }
}

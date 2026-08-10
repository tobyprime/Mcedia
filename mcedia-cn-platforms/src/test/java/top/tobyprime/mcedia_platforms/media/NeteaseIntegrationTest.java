package top.tobyprime.mcedia_platforms.media;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_platforms.auth.NeteaseCrypto;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for NeteaseCloudMusic API resolution.
 * Tests the live API with song 139774 (The truth that you leave - Pianoboy)
 * which is known to return a playable URL without login.
 *
 * <p>Live 测试依赖外部网络与网易云接口，默认禁用，仅手动运行。
 */
@Disabled("Live Netease API 测试，需手动运行（依赖外网与网易云接口）")
class NeteaseIntegrationTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String TEST_SONG_ID = "139774";

    @Test
    void songDetailApiReturnsValidData() throws Exception {
        var detailData = "{\"c\":\"[{\\\"id\\\":" + TEST_SONG_ID + "}]\",\"csrf_token\":\"\"}";
        var body = NeteaseCrypto.encrypt(detailData);

        var response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/weapi/v3/song/detail"))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", "https://music.163.com/")
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(body)))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(200, json.get("code").getAsInt());
        var songs = json.getAsJsonArray("songs");
        assertNotNull(songs);
        assertFalse(songs.isEmpty());
        var song = songs.get(0).getAsJsonObject();
        assertTrue(song.has("name"));
        var name = song.get("name").getAsString();
        assertFalse(name.isBlank());
        System.out.println("Song: " + name);
    }

    @Test
    void playUrlApiReturnsValidUrl() throws Exception {
        var playData = "{\"ids\":[" + TEST_SONG_ID + "],\"level\":\"standard\",\"encodeType\":\"mp3\",\"csrf_token\":\"\"}";
        var body = NeteaseCrypto.encrypt(playData);

        var response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/weapi/song/enhance/player/url/v1"))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", "https://music.163.com/")
                .POST(HttpRequest.BodyPublishers.ofString(toFormBody(body)))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        var json = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(200, json.get("code").getAsInt());
        var data = json.getAsJsonArray("data");
        assertNotNull(data);
        assertFalse(data.isEmpty());
        var songPlayInfo = data.get(0).getAsJsonObject();
        var url = songPlayInfo.get("url").getAsString();
        assertNotNull(url);
        assertFalse(url.isBlank());
        assertTrue(url.startsWith("http"));
        System.out.println("Play URL: " + url.substring(0, Math.min(100, url.length())) + "...");
    }

    @Test
    void extractSongIdFromUrl() throws Exception {
        var extractMethod = PlatformResolverBootstrap.class.getDeclaredMethod(
                "extractNeteaseSongId", String.class);
        extractMethod.setAccessible(true);
        var songId = (String) extractMethod.invoke(null, "https://music.163.com/song/" + TEST_SONG_ID);
        assertEquals(TEST_SONG_ID, songId);
    }

    private static String toFormBody(java.util.Map<String, String> params) {
        var sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}

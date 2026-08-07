package top.tobyprime.mcedia_platforms.media;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia.api.media.Media;
import top.tobyprime.mcedia.api.media.MediaInfo;
import top.tobyprime.mcedia.api.media.MediaPlayInfo;
import top.tobyprime.mcedia.api.resolver.MediaResolvers;

import static org.junit.jupiter.api.Assertions.*;

class NeteaseUrlParserTest {

    private final NeteaseUrlParser parser = new NeteaseUrlParser();

    @BeforeAll
    static void setUp() {
        MediaResolvers.register("netease", target -> new PlatformMedia(
                new MediaPlayInfo("https://example.com/test.mp3"),
                new MediaInfo("Test", "Artist", null, "netease")
        ));
    }

    @Test
    void parsesStandardSongUrl() {
        var result = parser.tryParse("https://music.163.com/song/12345");
        assertTrue(result.isPresent());
    }

    @Test
    void parsesStandardSongUrlWithQuery() {
        var result = parser.tryParse("https://music.163.com/song?id=12345");
        assertTrue(result.isPresent());
    }

    @Test
    void parsesHashRouteSongUrl() {
        var result = parser.tryParse("https://music.163.com/#/song?id=12345");
        assertTrue(result.isPresent());
    }

    @Test
    void parsesMobileSongUrl() {
        var result = parser.tryParse("https://music.163.com/m/song/12345");
        assertTrue(result.isPresent());
    }

    @Test
    void parsesPureSongId() {
        var result = parser.tryParse("12345");
        assertTrue(result.isPresent());
    }

    @Test
    void rejectsNonNeteaseUrl() {
        var result = parser.tryParse("https://example.com/song/12345");
        assertFalse(result.isPresent());
    }

    @Test
    void rejectsEmptyInput() {
        assertFalse(parser.tryParse("").isPresent());
        assertFalse(parser.tryParse("  ").isPresent());
    }

    @Test
    void rejectsNullInput() {
        assertFalse(parser.tryParse(null).isPresent());
    }

    @Test
    void extractsSongUrlFromShareText() {
        var input = "分享周杰伦的新歌 https://music.163.com/song/12345 快来听吧";
        var result = parser.tryParse(input);
        assertTrue(result.isPresent());
    }

    @Test
    void extractsSongIdFromShareText() {
        var input = "周杰伦 - 晴天 https://music.163.com/#/song?id=12345 推荐";
        var result = parser.tryParse(input);
        assertTrue(result.isPresent());
    }
}

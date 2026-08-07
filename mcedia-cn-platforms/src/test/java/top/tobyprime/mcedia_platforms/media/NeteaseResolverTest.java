package top.tobyprime.mcedia_platforms.media;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class NeteaseResolverTest {
    private static Method extractNeteaseSongId;
    private static Method selectBestBitrate;
    private static Method bitrateToLevel;

    @BeforeAll
    static void setUp() throws Exception {
        extractNeteaseSongId = PlatformResolverBootstrap.class.getDeclaredMethod(
                "extractNeteaseSongId", String.class);
        extractNeteaseSongId.setAccessible(true);

        selectBestBitrate = PlatformResolverBootstrap.class.getDeclaredMethod(
                "selectBestBitrate", int.class);
        selectBestBitrate.setAccessible(true);

        bitrateToLevel = PlatformResolverBootstrap.class.getDeclaredMethod(
                "bitrateToLevel", int.class);
        bitrateToLevel.setAccessible(true);
    }

    private String invokeExtractSongId(String input) throws Exception {
        return (String) extractNeteaseSongId.invoke(null, input);
    }

    private int invokeSelectBestBitrate(int maxHeight) throws Exception {
        return (int) selectBestBitrate.invoke(null, maxHeight);
    }

    private String invokeBitrateToLevel(int bitrate) throws Exception {
        return (String) bitrateToLevel.invoke(null, bitrate);
    }

    // -- extractSongId tests --

    @Test
    void extractsSongIdFromStandardUrl() throws Exception {
        assertEquals("12345", invokeExtractSongId("https://music.163.com/song/12345"));
    }

    @Test
    void extractsSongIdFromQueryUrl() throws Exception {
        assertEquals("12345", invokeExtractSongId("https://music.163.com/song?id=12345"));
    }

    @Test
    void extractsSongIdFromHashRoute() throws Exception {
        assertEquals("12345", invokeExtractSongId("https://music.163.com/#/song?id=12345"));
    }

    @Test
    void extractsSongIdFromMobileUrl() throws Exception {
        assertEquals("12345", invokeExtractSongId("https://music.163.com/m/song/12345"));
    }

    @Test
    void extractsSongIdFromPureNumber() throws Exception {
        assertEquals("12345", invokeExtractSongId("12345"));
    }

    @Test
    void returnsNullForNonSongUrl() throws Exception {
        assertNull(invokeExtractSongId("https://example.com/"));
    }

    @Test
    void returnsNullForBlankInput() throws Exception {
        assertNull(invokeExtractSongId(""));
        assertNull(invokeExtractSongId("  "));
    }

    @Test
    void returnsNullForNullInput() throws Exception {
        assertNull(invokeExtractSongId(null));
    }

    // -- selectBestBitrate tests --

    @Test
    void selectsLosslessForUnlimited() throws Exception {
        assertEquals(999000, invokeSelectBestBitrate(0));
    }

    @Test
    void selectsLosslessFor1080p() throws Exception {
        assertEquals(999000, invokeSelectBestBitrate(1080));
    }

    @Test
    void selectsExhighFor720p() throws Exception {
        assertEquals(320000, invokeSelectBestBitrate(720));
    }

    @Test
    void selectsHighFor480p() throws Exception {
        assertEquals(192000, invokeSelectBestBitrate(480));
    }

    @Test
    void selectsStandardForLowResolution() throws Exception {
        assertEquals(128000, invokeSelectBestBitrate(360));
    }

    // -- bitrateToLevel tests --

    @Test
    void levelIsLosslessFor999000() throws Exception {
        assertEquals("lossless", invokeBitrateToLevel(999000));
    }

    @Test
    void levelIsExhighFor320000() throws Exception {
        assertEquals("exhigh", invokeBitrateToLevel(320000));
    }

    @Test
    void levelIsHighFor192000() throws Exception {
        assertEquals("high", invokeBitrateToLevel(192000));
    }

    @Test
    void levelIsStandardFor128000() throws Exception {
        assertEquals("standard", invokeBitrateToLevel(128000));
    }

    @Test
    void levelIsExhighFor500000() throws Exception {
        assertEquals("exhigh", invokeBitrateToLevel(500000));
    }
}

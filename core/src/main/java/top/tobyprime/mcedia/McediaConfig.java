package top.tobyprime.mcedia;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class McediaConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("McediaConfig");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.properties");
    private static final Path COOKIE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.cookie.properties");

    private static final Properties mainProperties = new Properties();
    private static final Properties cookieProperties = new Properties();

    private static String YTDLP_BROWSER_COOKIE = "none";
    private static String BILIBILI_COOKIE = "";
    private static boolean CACHING_ENABLED = false;
    private static boolean RESUME_ON_RELOAD_ENABLED = false;
    private static boolean HARDWARE_DECODING_ENABLED = true;
    private static int FFMPEG_BUFFER_SIZE = 262144;
    private static int FFMPEG_PROBE_SIZE = 5 * 1024 * 1024;
    private static int FFMPEG_TIMEOUT = 15_000_000;
    private static int BUFFERING_AUDIO_TARGET = 256;
    private static int BUFFERING_VIDEO_TARGET = 90;
    private static int BUFFERING_VIDEO_LOW_WATERMARK = 30;
    private static int DECODER_MAX_AUDIO_FRAMES = 1024;
    private static int DECODER_MAX_VIDEO_FRAMES = 120;
    private static long PLAYER_STALL_TIMEOUT_MS = 5000;
    private static int DANMAKU_BASE_TRACK_COUNT = 25;
    private static int DANMAKU_MAX_PER_TICK = 8;
    private static float DANMAKU_BASE_DURATION_SEC = 10.0f;
    private static int DANMAKU_HARD_CAP = 300;

    public static String getYtdlpBrowserCookie() { return YTDLP_BROWSER_COOKIE; }
    public static String getBilibiliCookie() { return BILIBILI_COOKIE; }
    public static boolean isCachingEnabled() { return CACHING_ENABLED; }
    public static boolean isResumeOnReloadEnabled() { return RESUME_ON_RELOAD_ENABLED; }
    public static boolean isHardwareDecodingEnabled() { return HARDWARE_DECODING_ENABLED; }
    public static int getFfmpegBufferSize() { return FFMPEG_BUFFER_SIZE; }
    public static int getFfmpegProbeSize() { return FFMPEG_PROBE_SIZE; }
    public static int getFfmpegTimeout() { return FFMPEG_TIMEOUT; }
    public static int getBufferingAudioTarget() { return BUFFERING_AUDIO_TARGET; }
    public static int getBufferingVideoTarget() { return BUFFERING_VIDEO_TARGET; }
    public static int getBufferingVideoLowWatermark() { return BUFFERING_VIDEO_LOW_WATERMARK; }
    public static int getDecoderMaxAudioFrames() { return DECODER_MAX_AUDIO_FRAMES; }
    public static int getDecoderMaxVideoFrames() { return DECODER_MAX_VIDEO_FRAMES; }
    public static long getPlayerStallTimeoutMs() { return PLAYER_STALL_TIMEOUT_MS; }
    public static int getDanmakuBaseTrackCount() { return DANMAKU_BASE_TRACK_COUNT; }
    public static int getDanmakuMaxPerTick() { return DANMAKU_MAX_PER_TICK; }
    public static float getDanmakuBaseDurationSec() { return DANMAKU_BASE_DURATION_SEC; }
    public static int getDanmakuHardCap() { return DANMAKU_HARD_CAP; }

    public static void setYtdlpBrowserCookie(String browser) {
        YTDLP_BROWSER_COOKIE = browser;
        cookieProperties.setProperty("ytdlp.browser_cookie", browser);
    }

    public static void setBilibiliCookie(String cookie) {
        BILIBILI_COOKIE = cookie;
        cookieProperties.setProperty("bilibili.cookie", cookie);
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (FileInputStream fis = new FileInputStream(CONFIG_PATH.toFile())) {
                mainProperties.load(fis);
            } catch (IOException e) {
                LOGGER.error("加载 Mcedia 主配置文件失败！", e);
            }
        }
        if (Files.exists(COOKIE_PATH)) {
            try (FileInputStream fis = new FileInputStream(COOKIE_PATH.toFile())) {
                cookieProperties.load(fis);
            } catch (IOException e) {
                LOGGER.error("加载 Mcedia Cookie 配置文件失败！", e);
            }
        }

        try {
            // --- 读取主配置 ---
            CACHING_ENABLED = Boolean.parseBoolean(getPropertyOrDefault(mainProperties, "caching.enabled", "false"));
            RESUME_ON_RELOAD_ENABLED = Boolean.parseBoolean(getPropertyOrDefault(mainProperties, "feature.resumeOnReload.enabled", "false"));
            HARDWARE_DECODING_ENABLED = Boolean.parseBoolean(getPropertyOrDefault(mainProperties, "performance.hardwareDecoding", "true"));
            FFMPEG_BUFFER_SIZE = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.ffmpeg.bufferSize", "262144"));
            FFMPEG_PROBE_SIZE = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.ffmpeg.probeSize", "5242880"));
            FFMPEG_TIMEOUT = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.ffmpeg.timeout", "15000000"));
            BUFFERING_AUDIO_TARGET = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.buffering.audioTarget", "256"));
            BUFFERING_VIDEO_TARGET = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.buffering.videoTarget", "90"));
            BUFFERING_VIDEO_LOW_WATERMARK = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.buffering.videoLowWatermark", "30"));
            DECODER_MAX_AUDIO_FRAMES = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.decoder.maxAudioFrames", "1024"));
            DECODER_MAX_VIDEO_FRAMES = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.decoder.maxVideoFrames", "120"));
            PLAYER_STALL_TIMEOUT_MS = Long.parseLong(getPropertyOrDefault(mainProperties, "performance.player.stallTimeoutMs", "5000"));
            DANMAKU_BASE_TRACK_COUNT = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.danmaku.baseTrackCount", "25"));
            DANMAKU_MAX_PER_TICK = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.danmaku.maxPerTick", "8"));
            DANMAKU_BASE_DURATION_SEC = Float.parseFloat(getPropertyOrDefault(mainProperties, "performance.danmaku.baseDurationSec", "10.0"));
            DANMAKU_HARD_CAP = Integer.parseInt(getPropertyOrDefault(mainProperties, "performance.danmaku.hardCap", "300"));

            // --- 读取 Cookie 配置 ---
            setBilibiliCookie(cookieProperties.getProperty("bilibili.cookie", BILIBILI_COOKIE));
            setYtdlpBrowserCookie(cookieProperties.getProperty("ytdlp.browser_cookie", YTDLP_BROWSER_COOKIE));

        } catch (NumberFormatException e) {
            LOGGER.error("Mcedia 配置文件格式错误，将重置为默认值。", e);
        }
        save();
        saveCookieConfig();
    }

    private static String getPropertyOrDefault(Properties props, String key, String defaultValue) {
        String value = props.getProperty(key);
        if (value == null) {
            value = defaultValue;
            props.setProperty(key, defaultValue);
        }
        return value;
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            String header =
                    " Mcedia Configuration File\n" +
                            "# This file allows for advanced performance and buffering tuning.\n" +
                            "# Edit these values only if you are experiencing issues like frequent buffering or high memory usage.\n" +
                            "# --------------------------------------------------------------------------------------------------\n\n" +
                            "# --- General Settings ---\n" +
                            "# caching.enabled: Enable or disable video caching. (true / false)\n" +
                            "# feature.resumeOnReload.enabled: If true, the mod will save video progress locally and resume playback when you re-enter the area. (true / false)\n" +
                            "# Note: This progress is saved on your client, not on the server.\n" +
                            "\n" +
                            "# --- Performance Settings ---\n" +
                            "# performance.hardwareDecoding: Enable hardware-accelerated video decoding if supported by your GPU. (true / false)\n" +
                            "\n" +
                            "# --- Advanced Buffering & Decoding Performance Tuning ---\n" +
                            "# These settings control the player's buffering strategy. Higher values use more RAM but provide a smoother experience on unstable networks.\n" +
                            "\n" +
                            "# performance.decoder.maxVideoFrames: The maximum number of video frames the decoder can hold. Should be larger than videoTarget. Unit: frames.\n" +
                            "# performance.decoder.maxAudioFrames: The maximum number of audio frames the decoder can hold. Unit: frames.\n" +
                            "# performance.buffering.videoTarget: The target number of video frames to buffer in memory. Playback won't start until this target (multiplied by startThreshold) is reached. Unit: frames.\n" +
                            "# performance.buffering.audioTarget: The target number of audio frames to buffer in memory. Unit: frames.\n" +
                            "# performance.buffering.startThreshold: The percentage of the videoTarget buffer that must be filled before playback begins (e.g., 0.9 means 90%). Higher values mean longer initial load but less chance of buffering later. Unit: decimal (0.1 to 1.0).\n" +
                            "# performance.buffering.videoLowWatermark: If the number of buffered video frames drops below this value, the player will pause and re-buffer. Unit: frames.\n" +
                            "# performance.player.stallTimeoutMs: If no new video/audio data is received for this duration, the player will assume the stream has stalled and attempt to reconnect. Unit: milliseconds.\n" +
                            "\n" +
                            "# --- Advanced FFmpeg Network Tuning ---\n" +
                            "# These settings are passed directly to the underlying FFmpeg library for network operations.\n" +
                            "\n" +
                            "# performance.ffmpeg.timeout: Network timeout for all operations. Increase if you have a very slow connection. Unit: microseconds (1,000,000 = 1 second).\n" +
                            "# performance.ffmpeg.probeSize: How much data FFmpeg should download to analyze the video stream format. Increase if you have issues with strange video formats not playing. Unit: bytes.\n" +
                            "\n" +
                            "# --- Danmaku Performance Settings ---\n" +
                            "# These settings control the performance and appearance of danmaku (scrolling comments).\n" +
                            "# Higher values may impact performance on lower-end systems.\n" +
                            "\n" +
                            "# performance.danmaku.baseTrackCount: The base number of vertical tracks for danmaku. More tracks = smaller font size. This is scaled by the in-game setting.\n" +
                            "# performance.danmaku.maxPerTick: The maximum number of new danmaku that can be spawned in a single game tick (1/20th of a second). Prevents performance spikes during \"danmaku storms\".\n" +
                            "# performance.danmaku.baseDurationSec: The base time in seconds it takes for a danmaku to cross the screen. This is scaled by the in-game setting.\n" +
                            "# performance.danmaku.hardCap: The absolute maximum number of danmaku allowed on screen at once. If this limit is reached, no new danmaku will spawn until others disappear.\n"
                    ;
            mainProperties.store(writer, header);
        } catch (IOException e) {
            LOGGER.error("Failed to save Mcedia config", e);
        }
    }

    public static void saveCookieConfig() {
        try (FileWriter writer = new FileWriter(COOKIE_PATH.toFile())) {
            cookieProperties.store(writer, "Mcedia Authentication Cookies. Do not share this file.");
        } catch (IOException e) {
            LOGGER.error("Failed to save Mcedia cookie", e);
        }
    }

    public static void saveCookie(String cookie) {
        setBilibiliCookie(cookie);
        saveCookieConfig();
    }
}
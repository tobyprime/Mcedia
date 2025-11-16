package top.tobyprime.mcedia;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class McediaConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("McediaConfig");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.properties");
    private static final Path COOKIE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.cookie.properties");
    private static final Properties properties = new Properties();

    public static String YTDLP_BROWSER_COOKIE = "none";
    public static String BILIBILI_COOKIE = "";
    public static boolean CACHING_ENABLED = false;
    public static boolean RESUME_ON_RELOAD_ENABLED = false;
    public static boolean HARDWARE_DECODING_ENABLED = true;
    public static int FFMPEG_BUFFER_SIZE = 262144;
    public static int FFMPEG_PROBE_SIZE = 5 * 1024 * 1024;
    public static int FFMPEG_TIMEOUT = 15_000_000;

    // 缓冲与解码配置
    public static int BUFFERING_AUDIO_TARGET = 256;
    public static int BUFFERING_VIDEO_TARGET = 90;
    public static int BUFFERING_VIDEO_LOW_WATERMARK = 30;
    public static int DECODER_MAX_AUDIO_FRAMES = 1024;
    public static int DECODER_MAX_VIDEO_FRAMES = 120;
    public static long PLAYER_STALL_TIMEOUT_MS = 5000; // 5秒

    // 弹幕配置
    public static int DANMAKU_BASE_TRACK_COUNT = 25;
    public static int DANMAKU_MAX_PER_TICK = 8;
    public static float DANMAKU_BASE_DURATION_SEC = 10.0f;
    public static int DANMAKU_HARD_CAP = 300;

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                properties.load(Files.newInputStream(CONFIG_PATH));
                CACHING_ENABLED = Boolean.parseBoolean(properties.getProperty("caching.enabled", "false"));
                RESUME_ON_RELOAD_ENABLED = Boolean.parseBoolean(properties.getProperty("feature.resumeOnReload.enabled", "false"));
                HARDWARE_DECODING_ENABLED = Boolean.parseBoolean(properties.getProperty("performance.hardwareDecoding", "true"));
                FFMPEG_BUFFER_SIZE = Integer.parseInt(properties.getProperty("performance.ffmpeg.bufferSize", "262144"));
                FFMPEG_PROBE_SIZE = Integer.parseInt(properties.getProperty("performance.ffmpeg.probeSize", "5242880"));
                FFMPEG_TIMEOUT = Integer.parseInt(properties.getProperty("performance.ffmpeg.timeout", "15000000"));

                // 加载缓冲与解码配置
                BUFFERING_AUDIO_TARGET = Integer.parseInt(properties.getProperty("performance.buffering.audioTarget", "256"));
                BUFFERING_VIDEO_TARGET = Integer.parseInt(properties.getProperty("performance.buffering.videoTarget", "90"));
                BUFFERING_VIDEO_LOW_WATERMARK = Integer.parseInt(properties.getProperty("performance.buffering.videoLowWatermark", "42"));
                DECODER_MAX_AUDIO_FRAMES = Integer.parseInt(properties.getProperty("performance.decoder.maxAudioFrames", "1024"));
                DECODER_MAX_VIDEO_FRAMES = Integer.parseInt(properties.getProperty("performance.decoder.maxVideoFrames", "120"));
                PLAYER_STALL_TIMEOUT_MS = Long.parseLong(properties.getProperty("performance.player.stallTimeoutMs", "5000"));

                // 弹幕配置
                DANMAKU_BASE_TRACK_COUNT = Integer.parseInt(properties.getProperty("performance.danmaku.baseTrackCount", "25"));
                DANMAKU_MAX_PER_TICK = Integer.parseInt(properties.getProperty("performance.danmaku.maxPerTick", "8"));
                DANMAKU_BASE_DURATION_SEC = Float.parseFloat(properties.getProperty("performance.danmaku.baseDurationSec", "10.0"));
                DANMAKU_HARD_CAP = Integer.parseInt(properties.getProperty("performance.danmaku.hardCap", "300"));
            } catch (IOException | NumberFormatException e) {
                LOGGER.error("Failed to load Mcedia config, using default values.", e);
                setDanmakuDefaults();
                save();
            }
        } else {
            LOGGER.info("Mcedia config not found, creating a new one with default values.");
            save();
        }
        loadCookie();
    }

    public static void save() {
        properties.setProperty("caching.enabled", String.valueOf(CACHING_ENABLED));
        properties.setProperty("feature.resumeOnReload.enabled", String.valueOf(RESUME_ON_RELOAD_ENABLED));
        properties.setProperty("performance.hardwareDecoding", String.valueOf(HARDWARE_DECODING_ENABLED));
        properties.setProperty("performance.ffmpeg.bufferSize", String.valueOf(FFMPEG_BUFFER_SIZE));
        properties.setProperty("performance.ffmpeg.probeSize", String.valueOf(FFMPEG_PROBE_SIZE));
        properties.setProperty("performance.ffmpeg.timeout", String.valueOf(FFMPEG_TIMEOUT));

        // 保存缓冲与解码配置
        properties.setProperty("performance.buffering.audioTarget", String.valueOf(BUFFERING_AUDIO_TARGET));
        properties.setProperty("performance.buffering.videoTarget", String.valueOf(BUFFERING_VIDEO_TARGET));
        properties.setProperty("performance.buffering.videoLowWatermark", String.valueOf(BUFFERING_VIDEO_LOW_WATERMARK));
        properties.setProperty("performance.decoder.maxAudioFrames", String.valueOf(DECODER_MAX_AUDIO_FRAMES));
        properties.setProperty("performance.decoder.maxVideoFrames", String.valueOf(DECODER_MAX_VIDEO_FRAMES));
        properties.setProperty("performance.player.stallTimeoutMs", String.valueOf(PLAYER_STALL_TIMEOUT_MS));

        // 弹幕配置
        properties.setProperty("performance.danmaku.baseTrackCount", String.valueOf(DANMAKU_BASE_TRACK_COUNT));
        properties.setProperty("performance.danmaku.maxPerTick", String.valueOf(DANMAKU_MAX_PER_TICK));
        properties.setProperty("performance.danmaku.baseDurationSec", String.valueOf(DANMAKU_BASE_DURATION_SEC));
        properties.setProperty("performance.danmaku.hardCap", String.valueOf(DANMAKU_HARD_CAP));
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
                            "# performance.player.stallTimeoutMs: If no new video/audio data is received for this duration, the player will assume the stream has stalled and attempt to reconnect. Unit: milliseconds.\n" +
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

            properties.store(writer, header);
        } catch (IOException e) {
            LOGGER.error("Failed to save Mcedia config", e);
        }
    }

    private static void loadCookie() {
        try {
            if (Files.exists(COOKIE_PATH)) {
                Properties cookieProps = new Properties();
                cookieProps.load(Files.newInputStream(COOKIE_PATH));
                BILIBILI_COOKIE = cookieProps.getProperty("bilibili.cookie", "");
                YTDLP_BROWSER_COOKIE = properties.getProperty("ytdlp.browser_cookie", "none");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load Mcedia cookie file", e);
        }
    }

    public static synchronized void saveCookie(String cookie) {
        BILIBILI_COOKIE = cookie;
        Properties cookieProps = new Properties();
        cookieProps.setProperty("bilibili.cookie", BILIBILI_COOKIE);
        properties.setProperty("ytdlp.browser_cookie", YTDLP_BROWSER_COOKIE);
        try (FileWriter writer = new FileWriter(COOKIE_PATH.toFile())) {
            cookieProps.store(writer, "Bilibili authentication cookie. Do not edit this manually.");
        } catch (IOException e) {
            LOGGER.error("Failed to save Mcedia cookie", e);
        }
    }

    private static void setDanmakuDefaults() {
        DANMAKU_BASE_TRACK_COUNT = 25;
        DANMAKU_MAX_PER_TICK = 8;
        DANMAKU_BASE_DURATION_SEC = 10.0f;
        DANMAKU_HARD_CAP = 300;
    }
}
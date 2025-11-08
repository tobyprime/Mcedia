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

    // --- 配置项及其默认值 ---
    public static String BILIBILI_COOKIE = "";
    // 缓存设置
    public static boolean CACHING_ENABLED = false;
    // 性能设置
    public static boolean HARDWARE_DECODING_ENABLED = true;

    // FFmpeg 网络性能配置项
    // 默认262k网络缓冲区
    public static int FFMPEG_BUFFER_SIZE = 262144;
    // 默认10MB探测大小
    public static int FFMPEG_PROBE_SIZE = 10 * 1024 * 1024;
    // 默认10微妙网络超时
    public static int FFMPEG_TIMEOUT = 10_000_000;


    public static void load() {
        // 加载主配置文件
        if (Files.exists(CONFIG_PATH)) {
            try {
                properties.load(Files.newInputStream(CONFIG_PATH));
                CACHING_ENABLED = Boolean.parseBoolean(properties.getProperty("caching.enabled", "true"));
                HARDWARE_DECODING_ENABLED = Boolean.parseBoolean(properties.getProperty("performance.hardwareDecoding", "true"));
                FFMPEG_BUFFER_SIZE = Integer.parseInt(properties.getProperty("performance.ffmpeg.bufferSize", String.valueOf(FFMPEG_BUFFER_SIZE)));
                FFMPEG_PROBE_SIZE = Integer.parseInt(properties.getProperty("performance.ffmpeg.probeSize", String.valueOf(FFMPEG_PROBE_SIZE)));
                FFMPEG_TIMEOUT = Integer.parseInt(properties.getProperty("performance.ffmpeg.timeout", String.valueOf(FFMPEG_TIMEOUT)));

            } catch (IOException | NumberFormatException e) {
                LOGGER.error("Failed to load Mcedia config, using default values.", e);
                save();
            }
        } else {
            LOGGER.info("Mcedia config not found, creating a new one with default values.");
            save();
        }

        // 加载 Cookie 文件
        try {
            if (Files.exists(COOKIE_PATH)) {
                Properties cookieProps = new Properties();
                cookieProps.load(Files.newInputStream(COOKIE_PATH));
                BILIBILI_COOKIE = cookieProps.getProperty("bilibili.cookie", "");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load Mcedia cookie file", e);
        }
    }

    public static void save() {
        properties.setProperty("caching.enabled", String.valueOf(CACHING_ENABLED));
        properties.setProperty("performance.hardwareDecoding", String.valueOf(HARDWARE_DECODING_ENABLED));
        properties.setProperty("performance.ffmpeg.bufferSize", String.valueOf(FFMPEG_BUFFER_SIZE));
        properties.setProperty("performance.ffmpeg.probeSize", String.valueOf(FFMPEG_PROBE_SIZE));
        properties.setProperty("performance.ffmpeg.timeout", String.valueOf(FFMPEG_TIMEOUT));

        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            String header = " Mcedia Configuration File\n"
                    + " For performance.ffmpeg.* settings, higher values may improve fluency on high-resolution videos (like 4K)\n"
                    + " but will increase memory usage and initial loading time. Adjust them if you experience stuttering.\n";
            properties.store(writer, header);
        } catch (IOException e) {
            LOGGER.error("Failed to save Mcedia config", e);
        }
    }

    public static synchronized void saveCookie(String cookie) {
        BILIBILI_COOKIE = cookie;
        Properties cookieProps = new Properties();
        cookieProps.setProperty("bilibili.cookie", BILIBILI_COOKIE);
        try (FileWriter writer = new FileWriter(COOKIE_PATH.toFile())) {
            cookieProps.store(writer, "Bilibili authentication cookie. Do not edit this manually.");
        } catch (IOException e) {
            LOGGER.error("Failed to save Mcedia cookie", e);
        }
    }
}
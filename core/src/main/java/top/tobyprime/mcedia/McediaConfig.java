// McediaConfig.java (最终、完整的版本)
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

    // [修改] 配置文件和Cookie文件分离
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.properties");
    private static final Path COOKIE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.cookie.properties");

    private static final Properties properties = new Properties();

    // --- 配置项 ---
    public static String BILIBILI_COOKIE = "";

    // 缓存设置
    public static boolean CACHING_ENABLED = true;

    // 性能设置
    public static boolean HARDWARE_DECODING_ENABLED = true;

    public static void load() {
        // 加载主配置文件
        try {
            if (Files.notExists(CONFIG_PATH)) {
                // 如果文件不存在，则使用默认值并保存，以创建文件
                LOGGER.info("Mcedia config not found, creating a new one with default values.");
                save();
            } else {
                // 如果文件存在，则加载它
                properties.load(Files.newInputStream(CONFIG_PATH));
                CACHING_ENABLED = Boolean.parseBoolean(properties.getProperty("caching.enabled", "true"));
                HARDWARE_DECODING_ENABLED = Boolean.parseBoolean(properties.getProperty("performance.hardwareDecoding", "true"));
            }
        } catch (IOException | NumberFormatException e) {
            LOGGER.error("Failed to load Mcedia config, using default values.", e);
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
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            properties.store(writer, "Mcedia Configuration");
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
package top.tobyprime.mcedia;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class McediaConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.properties");
    private static final Properties properties = new Properties();

    public static String BILIBILI_COOKIE = "";

    public static void load() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                properties.setProperty("bilibili.cookie", "");
                save();
            }
            properties.load(Files.newInputStream(CONFIG_PATH));
            BILIBILI_COOKIE = properties.getProperty("bilibili.cookie", "");
        } catch (IOException e) {
            LoggerFactory.getLogger("McediaConfig").error("Failed to load Mcedia config", e);
        }
    }

    public static void save() {
        properties.setProperty("bilibili.cookie", BILIBILI_COOKIE);
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            properties.store(writer, "Mcedia Configuration\n" +
                    "# Bilibili authentication cookie. Do not edit this manually.\n" +
                    "# Use the in-game command '/mcedia login' to generate it.");
        } catch (IOException e) {
            LoggerFactory.getLogger("McediaConfig").error("Failed to save Mcedia config", e);
        }
    }

    // 用于保存 Cookie 的便捷方法
    public static synchronized void saveCookie(String cookie) {
        BILIBILI_COOKIE = cookie;
        save();
    }
}
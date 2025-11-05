package top.tobyprime.mcedia;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 该类负责加载和保存 Mod 的配置文件 (config/mcedia.properties)
 */
public class McediaConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.properties");
    private static final Properties properties = new Properties();

    // 公共静态字段，用于在代码中直接访问配置项
    public static String BILIBILI_COOKIE = "";

    /**
     * 从文件中加载配置。如果文件不存在，则创建一个新的。
     */
    public static void load() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                // 如果配置文件不存在，创建一个默认的
                properties.setProperty("bilibili.cookie", "SESSDATA=YourSessDataHere;");
                save();
            }
            // 从输入流加载属性
            properties.load(Files.newInputStream(CONFIG_PATH));
            // 读取 cookie 值，如果找不到则默认为空字符串
            BILIBILI_COOKIE = properties.getProperty("bilibili.cookie", "");
        } catch (IOException e) {
            LoggerFactory.getLogger("McediaConfig").error("Failed to load Mcedia config", e);
        }
    }

    /**
     * 将当前的配置属性保存到文件。
     */
    public static void save() {
        try {
            properties.store(Files.newOutputStream(CONFIG_PATH), "Mcedia Configuration\n" +
                    "# Enter your Bilibili SESSDATA cookie here to access member-only content.\n" +
                    "# Example: bilibili.cookie=SESSDATA=a1b2c3d4,...;");
        } catch (IOException e) {
            LoggerFactory.getLogger("McediaConfig").error("Failed to save Mcedia config", e);
        }
    }
}
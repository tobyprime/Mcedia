package top.tobyprime.mcedia.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.bilibili.BilibiliAuthManager;
import top.tobyprime.mcedia.bilibili.BilibiliCookie;
import top.tobyprime.mcedia.commands.CommandBilibili;
import top.tobyprime.mcedia.commands.CommandCommon;
import top.tobyprime.mcedia.commands.CommandDanmaku;
import top.tobyprime.mcedia.entities.MediaPlayerAgentEntity;
import top.tobyprime.mcedia.renderers.MediaPlayerAgentEntityRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class McediaClient implements ClientModInitializer {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.properties");

    private static Path getCookieConfig() {
        return Path.of(System.getProperty("user.home"), ".mcedia", "cookie.properties");
    }

    public static void SaveConfig() {
        var props = new Properties();
        var cookies = new Properties();

        try {
            if (!Files.exists(CONFIG_PATH)) {
                Files.createFile(CONFIG_PATH);
            }
            if (!Files.exists(getCookieConfig())) {
                Files.createDirectories(getCookieConfig().getParent());
                Files.createFile(getCookieConfig());
            }
            Configs.writeToProperties(props);
            BilibiliCookie.writeToProperties(cookies);

            props.store(Files.newOutputStream(CONFIG_PATH), "Mcedia props");
            cookies.store(Files.newOutputStream(getCookieConfig()), "Mcedia cookies");
        } catch (IOException e) {
            Mcedia.LOGGER.error("保存配置失败", e);
        }
    }

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MediaPlayerAgentEntity.TYPE, MediaPlayerAgentEntityRenderer::new);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CommandBilibili.register(dispatcher);
            CommandCommon.register(dispatcher);
            CommandDanmaku.register(dispatcher);
        });
        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            var props = new Properties();
            var cookies = new Properties();
            if (Files.exists(CONFIG_PATH)) {
                try {
                    props.load(Files.newInputStream(CONFIG_PATH));

                    Configs.fromProperties(props);

                    BilibiliAuthManager.getInstance().checkAndUpdateLoginStatusAsync();
                } catch (IOException e) {
                    Mcedia.LOGGER.error("读取配置失败", e);
                }
                return;
            }
            if (Files.exists(getCookieConfig())) {
                try {
                    cookies.load(Files.newInputStream(getCookieConfig()));

                    BilibiliCookie.fromProperties(cookies);

                    BilibiliAuthManager.getInstance().checkAndUpdateLoginStatusAsync();
                } catch (IOException e) {
                    Mcedia.LOGGER.error("读取Cookie失败", e);
                }
            }


        });
        ClientLifecycleEvents.CLIENT_STOPPING.register((client) -> SaveConfig());
    }
}

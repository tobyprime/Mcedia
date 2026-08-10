package top.tobyprime.mcedia_platforms.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import top.tobyprime.mcedia_platforms.auth.BilibiliAuthManager;
import top.tobyprime.mcedia_platforms.auth.BilibiliCookie;
import top.tobyprime.mcedia_platforms.auth.NeteaseAuthManager;
import top.tobyprime.mcedia_platforms.auth.NeteaseCookie;
import top.tobyprime.mcedia_platforms.commands.CommandBilibili;
import top.tobyprime.mcedia_platforms.commands.CommandNetease;
import top.tobyprime.mcedia_platforms.media.PlatformResolverBootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class McediaPlatformsClient implements ClientModInitializer {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia_platforms.properties");
    private static final Path COOKIE_PATH = Path.of(System.getProperty("user.home"), ".mcedia", "cookie.properties");

    @Override
    public void onInitializeClient() {
        PlatformResolverBootstrap.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CommandBilibili.register(dispatcher);
            CommandNetease.register(dispatcher);
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            loadConfig();
            BilibiliAuthManager.getInstance().checkAndUpdateLoginStatusAsync();
            NeteaseAuthManager.getInstance().checkAndUpdateLoginStatusAsync();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());
    }

    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                var props = new Properties();
                try (var input = Files.newInputStream(CONFIG_PATH)) {
                    props.load(input);
                }
            }
            if (Files.exists(COOKIE_PATH)) {
                var cookies = new Properties();
                try (var input = Files.newInputStream(COOKIE_PATH)) {
                    cookies.load(input);
                }
                BilibiliCookie.fromProperties(cookies);
                NeteaseCookie.fromProperties(cookies);
            }
        } catch (IOException ignored) {
        }
    }

    public static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.createDirectories(COOKIE_PATH.getParent());
            var props = new Properties();
            var cookies = new Properties();
            BilibiliCookie.writeToProperties(cookies);
            NeteaseCookie.writeToProperties(cookies);
            try (var output = Files.newOutputStream(CONFIG_PATH)) {
                props.store(output, "Mcedia Platforms config");
            }
            try (var output = Files.newOutputStream(COOKIE_PATH)) {
                cookies.store(output, "Mcedia Platforms cookies");
            }
        } catch (IOException ignored) {
        }
    }
}

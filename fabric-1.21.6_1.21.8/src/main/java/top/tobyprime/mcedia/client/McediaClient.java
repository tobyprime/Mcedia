package top.tobyprime.mcedia.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.bilibili.BilibiliAuthManager;
import top.tobyprime.mcedia.bilibili.BilibiliConfigs;
import top.tobyprime.mcedia.commands.CommandLogin;
import top.tobyprime.mcedia.entities.MediaPlayerAgentEntity;
import top.tobyprime.mcedia.renderers.MediaPlayerAgentEntityRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class McediaClient implements ClientModInitializer {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mcedia.properties");


    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MediaPlayerAgentEntity.TYPE, MediaPlayerAgentEntityRenderer::new);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> CommandLogin.register(dispatcher));
        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            var props = new Properties();
            if (!Files.exists(CONFIG_PATH)) {
                return;
            }
            try {
                props.load(Files.newInputStream(CONFIG_PATH));

                Configs.fromProperties(props);
                BilibiliConfigs.fromProperties(props);

                BilibiliAuthManager.getInstance().checkAndUpdateLoginStatusAsync();
            } catch (IOException e) {
                Mcedia.LOGGER.error("读取配置失败", e);
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register((client) -> {
            var props = new Properties();
            try {
                if (!Files.exists(CONFIG_PATH)) {
                    Files.createFile(CONFIG_PATH);
                }

                Configs.writeToProperties(props);
                BilibiliConfigs.writeToProperties(props);

                props.store(Files.newOutputStream(CONFIG_PATH), "Mcedia props");

            }
            catch (IOException e) {
                Mcedia.LOGGER.error("保存配置失败",e);
            }

        });
    }
}

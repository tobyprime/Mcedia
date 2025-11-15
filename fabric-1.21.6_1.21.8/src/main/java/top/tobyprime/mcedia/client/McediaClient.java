package top.tobyprime.mcedia.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import top.tobyprime.mcedia.Command.*;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.manager.BilibiliAuthManager;
import top.tobyprime.mcedia.manager.PipManager;

public class McediaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        registerCommands();
        registerClientSideEvents();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CommandHelp.register(dispatcher);
            CommandLogin.register(dispatcher);
            CommandControl.register(dispatcher);
            CommandInfo.register(dispatcher);
            CommandConfig.register(dispatcher);
            CommandPreset.register(dispatcher);
            CommandPlaylist.register(dispatcher);
            CommandPip.register(dispatcher);
        });
    }

    private void registerClientSideEvents() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                Mcedia.getInstance().cleanupAllAgents();
                PipManager.getInstance().close();
                });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                BilibiliAuthManager.getInstance().checkCookieValidityAndNotifyPlayer();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
                Mcedia.getInstance().cleanupCacheDirectory();
                PipManager.getInstance().onGameShutdown();
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            PipManager.getInstance().render(guiGraphics, tickDelta);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PipManager.getInstance().tick();
        });
    }
}
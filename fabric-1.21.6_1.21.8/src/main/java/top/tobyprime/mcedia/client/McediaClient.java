package top.tobyprime.mcedia.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import top.tobyprime.mcedia.Command.*;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.manager.BilibiliAuthManager;

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
        });
    }
}

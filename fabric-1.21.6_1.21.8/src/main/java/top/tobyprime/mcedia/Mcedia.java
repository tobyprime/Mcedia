package top.tobyprime.mcedia;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import top.tobyprime.mcedia.auth_manager.BilibiliAuthManager;
import top.tobyprime.mcedia.mixin_bridge.ISoundEngineBridge;
import top.tobyprime.mcedia.mixin_bridge.ISoundManagerBridge;
import top.tobyprime.mcedia.provider.BilibiliBangumiProvider;
import top.tobyprime.mcedia.provider.BilibiliLiveProvider;
import top.tobyprime.mcedia.provider.BilibiliVideoProvider;
import top.tobyprime.mcedia.provider.MediaProviderRegistry;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Mcedia implements ModInitializer {
    private static final int MAX_PLAYER_COUNT = 5;

    private static Mcedia INSTANCE;
    private final ConcurrentHashMap<Entity, PlayerAgent> entityToPlayer = new ConcurrentHashMap<>();
    private final Queue<ArmorStand> pendingAgents = new ConcurrentLinkedQueue<>();

    public static Mcedia getInstance() {
        return INSTANCE;
    }

    public ConcurrentHashMap<Entity, PlayerAgent> getEntityToPlayerMap() {
        return entityToPlayer;
    }

    public SoundEngineExecutor getAudioExecutor() {
        var manager = (ISoundManagerBridge) Minecraft.getInstance().getSoundManager();
        var engine = (ISoundEngineBridge) manager.mcdia$getSoundManager();
        return engine.mcdia$getExecutor();
    }

    private void clearMap() {
        for (var entry : entityToPlayer.entrySet()) {
            entry.getValue().close();
            entityToPlayer.remove(entry.getKey());
        }
    }

    @Override
    public void onInitialize() {
        INSTANCE = this;

        McediaConfig.load();

        initializeProviders();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            processPendingAgents();
            for (var pair : entityToPlayer.entrySet()) {
                if (pair.getKey().isRemoved()) {
                    pair.getValue().close();
                    entityToPlayer.remove(pair.getKey());
                    return;
                }
                pair.getValue().tick();
            }
        });

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((mc, level) -> clearMap());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearMap());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // 一个小的延迟，确保聊天框已经完全准备好接收消息
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // 延迟3秒
                    BilibiliAuthManager.getInstance().checkCookieValidityAndNotifyPlayer();
                } catch (InterruptedException e) {
                    // ignore
                }
            }).start();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CommandLogin.register(dispatcher);
        });
    }

    private void initializeProviders() {
        MediaProviderRegistry registry = MediaProviderRegistry.getInstance();
        registry.register(new BilibiliVideoProvider());
        registry.register(new BilibiliBangumiProvider());
        registry.register(new BilibiliLiveProvider());
    }

    private void processPendingAgents() {
        ArmorStand entity;
        while ((entity = pendingAgents.poll()) != null) {
            if (!entity.isRemoved() && !entityToPlayer.containsKey(entity)) {
                if (getEntityToPlayerMap().size() >= MAX_PLAYER_COUNT) {
                    return;
                }
                PlayerAgent agent = new PlayerAgent(entity);
                agent.initializeGraphics();
                entityToPlayer.put(entity, agent);
            }
        }
    }

    public void HandleMcdiaPlayerEntity(ArmorStand entity) {
        boolean isMcdiaPlayer = entity.getCustomName() != null && (entity.getCustomName().getString().contains("mcdia") || entity.getCustomName().getString().contains("mcedia"));
        if (isMcdiaPlayer && !entityToPlayer.containsKey(entity) && !pendingAgents.contains(entity)) {
            pendingAgents.add(entity);
        }
    }

    public static void msgToPlayer(String msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(msg), false);
        }
    }

    public static void msgToPlayer(Component component) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            // displayClientMessage 方法原生就支持 Component
            player.displayClientMessage(component, false);
        }
    }
}
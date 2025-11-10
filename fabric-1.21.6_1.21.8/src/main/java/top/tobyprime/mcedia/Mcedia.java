package top.tobyprime.mcedia;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.pathfinder.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.auth_manager.BilibiliAuthManager;
import top.tobyprime.mcedia.provider.*;
import top.tobyprime.mcedia.video_fetcher.BiliBiliVideoFetcher;
import top.tobyprime.mcedia.video_fetcher.BilibiliBangumiFetcher;
import top.tobyprime.mcedia.mixin_bridge.ISoundEngineBridge;
import top.tobyprime.mcedia.mixin_bridge.ISoundManagerBridge;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

public class Mcedia implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(Mcedia.class);
    private static final int MAX_PLAYER_COUNT = 5;
    private static final java.nio.file.Path CACHE_DIR = new File(Minecraft.getInstance().gameDirectory, "mcedia_cache").toPath();

    private static Mcedia INSTANCE;
    private final ConcurrentHashMap<ArmorStand, PlayerAgent> entityToPlayer = new ConcurrentHashMap<>();
    private final Queue<ArmorStand> pendingAgents = new ConcurrentLinkedQueue<>();
    private VideoCacheManager globalCacheManager;
    private HttpClient globalHttpClient;
    private ExecutorService httpExecutor;

    public VideoCacheManager getCacheManager() {
        return this.globalCacheManager;
    }

    public static Mcedia getInstance() {
        return INSTANCE;
    }

    public static java.nio.file.Path getCacheDirectory() {
        return CACHE_DIR;
    }

    public ConcurrentHashMap<ArmorStand, PlayerAgent> getEntityToPlayerMap() {
        return entityToPlayer;
    }
    public HttpClient getHttpClient() {
        return this.globalHttpClient;
    }

    public SoundEngineExecutor getAudioExecutor() {
        var manager = (ISoundManagerBridge) Minecraft.getInstance().getSoundManager();
        var engine = (ISoundEngineBridge) manager.mcdia$getSoundManager();
        return engine.mcdia$getExecutor();
    }

    @Override
    public void onInitialize() {
        INSTANCE = this;
        this.httpExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "Mcedia-Http-Client-Thread");
            thread.setDaemon(true);
            return thread;
        });
        this.globalHttpClient = HttpClient.newBuilder()
                .executor(this.httpExecutor)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        McediaConfig.load();
        try {
            java.nio.file.Path cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve("mcedia_cache");
            java.nio.file.Files.createDirectories(cacheDir);
            this.globalCacheManager = new VideoCacheManager(cacheDir, this.globalHttpClient);
            LOGGER.info("Mcedia 全局缓存管理器已初始化。");
        } catch (IOException e) {
            LOGGER.error("无法创建或访问Mcedia缓存目录！缓存功能将不可用。", e);
            this.globalCacheManager = new VideoCacheManager(null, this.globalHttpClient);
        }
        BiliBiliVideoFetcher.setAuthStatusSupplier(BilibiliAuthManager.getInstance()::isLoggedIn);
        BilibiliBangumiFetcher.setAuthStatusSupplier(BilibiliAuthManager.getInstance()::isLoggedIn);
        BilibiliAuthManager.getInstance().checkCookieValidityAndNotifyPlayer();
        initializeProviders();
        registerCommands();
        registerEvents();
    }

    private void initializeProviders() {
        MediaProviderRegistry registry = MediaProviderRegistry.getInstance();
        registry.register(new BilibiliVideoProvider());
        registry.register(new BilibiliBangumiProvider());
        registry.register(new BilibiliLiveProvider());
        registry.register(new YhdmProvider());
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CommandLogin.register(dispatcher);
        });
    }

    private void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) return;

            processPendingAgents();

            // 只负责 tick 存活的 agent。清理工作已移交给 Mixin 和 disconnect 事件。
            for (PlayerAgent agent : entityToPlayer.values()) {
                if (agent.getEntity().isRemoved()) {
                    removePlayerAgent(agent.getEntity());
                } else {
                    agent.tick();
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> cleanupAllAgents());

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!BilibiliAuthManager.getInstance().isLoggedIn()) {
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        Mcedia.msgToPlayer("§e[Mcedia] §f提示: 部分视频需要登录B站才能播放，可以使用 §a/mcedia login 登录");
                    } catch (InterruptedException ignored) {}
                }).start();
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (this.globalCacheManager != null) {
                this.globalCacheManager.cleanup();
            }
            if (this.httpExecutor != null) {
                LOGGER.info("正在关闭 Mcedia 的 Http 客户端...");
                this.httpExecutor.shutdownNow();
            }
        });
    }

    private void cleanupCacheDirectory() {
        LOGGER.info("正在清理 Mcedia 缓存目录...");
        if (java.nio.file.Files.isDirectory(CACHE_DIR)) {
            try (Stream<java.nio.file.Path> files = java.nio.file.Files.list(CACHE_DIR)) {
                files.forEach(file -> {
                    try {
                        java.nio.file.Files.delete(file);
                    } catch (IOException e) {
                        LOGGER.warn("删除缓存文件失败: {}", file, e);
                    }
                });
                LOGGER.info("Mcedia 缓存目录已清理。");
            } catch (IOException e) {
                LOGGER.error("无法列出缓存目录中的文件", e);
            }
        }
    }

    private void processPendingAgents() {
        ArmorStand entity;
        while ((entity = pendingAgents.poll()) != null) {
            if (!entity.isRemoved() && !entityToPlayer.containsKey(entity)) {
                if (entityToPlayer.size() >= MAX_PLAYER_COUNT) {
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

    /**
     * 安全地关闭并移除与指定盔甲架关联的 PlayerAgent。
     * 这个方法由 MixinArmorStand_Removal 调用。
     * @param entity 即将被移除的盔甲架实体
     */
    public void removePlayerAgent(ArmorStand entity) {
        PlayerAgent agent = this.entityToPlayer.remove(entity);
        if (agent != null) {
            LOGGER.info("通过移除事件清理 Mcedia Player 实例，位于 {}", entity.position());
            agent.closeSync();
        }
    }

    /**
     * 清理所有活动的 PlayerAgent 实例，通常在退出世界时调用。
     */
    private void cleanupAllAgents() {
        LOGGER.info("正在清理所有 Mcedia Player 实例...");
        for (PlayerAgent agent : this.entityToPlayer.values()) {
            agent.closeSync();
        }
        this.entityToPlayer.clear();
        LOGGER.info("所有 Mcedia Player 实例已清理完毕。");
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
            player.displayClientMessage(component, false);
        }
    }
}
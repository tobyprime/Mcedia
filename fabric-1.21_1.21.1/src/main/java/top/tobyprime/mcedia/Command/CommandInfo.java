package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.PlayerAgent;
import top.tobyprime.mcedia.manager.VideoCacheManager;
import top.tobyprime.mcedia.provider.MediaProviderRegistry;
import top.tobyprime.mcedia.provider.VideoInfo;
import top.tobyprime.mcedia.video_fetcher.UrlExpander;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandInfo {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("mcedia")
                // --- /mcedia status ---
                .then(literal("status")
                        .executes(CommandInfo::executeStatus)
                )
                // --- /mcedia cache ---
                .then(literal("cache")
                        .then(literal("list")
                                .executes(CommandInfo::executeCacheList)
                        )
                        .then(literal("clear")
                                .executes(CommandInfo::executeCacheClear)
                        )
                        .then(literal("prefetch")
                                .then(argument("url", StringArgumentType.greedyString())
                                        .executes(CommandInfo::executeCachePrefetch)
                                )
                        )
                )
        );
    }

    private static int executeStatus(CommandContext<FabricClientCommandSource> context) {
        return executeOnTargetedAgent(context, agent -> {
            context.getSource().sendFeedback(agent.getStatusComponent());
        });
    }

    private static int executeCacheList(CommandContext<FabricClientCommandSource> context) {
        if (!McediaConfig.isCachingEnabled()) {
            context.getSource().sendError(Component.literal("§c缓存功能未启用，请在配置文件中开启。"));
            return 0;
        }

        VideoCacheManager cacheManager = Mcedia.getInstance().getCacheManager();
        Map<String, Long> cacheInfo = cacheManager.getCacheInfo();

        if (cacheInfo.isEmpty()) {
            context.getSource().sendFeedback(Component.literal("§a[Mcedia] §f缓存为空。"));
            return 1;
        }

        context.getSource().sendFeedback(Component.literal("§6--- Mcedia 缓存列表 ---"));
        cacheInfo.forEach((hash, size) -> {
            String sizeStr = String.format("%.2f MB", size / (1024.0 * 1024.0));
            context.getSource().sendFeedback(Component.literal("§7- " + hash + " §f(" + sizeStr + ")"));
        });
        return 1;
    }

    private static int executeCacheClear(CommandContext<FabricClientCommandSource> context) {
        if (!McediaConfig.isCachingEnabled()) {
            context.getSource().sendError(Component.literal("§c缓存功能未启用。"));
            return 0;
        }
        Mcedia.getInstance().getCacheManager().clearCache();
        context.getSource().sendFeedback(Component.literal("§a[Mcedia] §f所有视频缓存已被清空。"));
        return 1;
    }

    private static int executeCachePrefetch(CommandContext<FabricClientCommandSource> context) {
        if (!McediaConfig.isCachingEnabled()) {
            context.getSource().sendError(Component.literal("§c缓存功能未启用。"));
            return 0;
        }
        String url = StringArgumentType.getString(context, "url");
        Mcedia.msgToPlayer("§a[Mcedia] §f已开始在后台预缓存: " + url);

        CompletableFuture.runAsync(() -> {
            try {
                String expandedUrl = UrlExpander.expand(url).join();
                VideoInfo info = MediaProviderRegistry.getInstance().resolve(expandedUrl, McediaConfig.getBilibiliCookie(), "自动");
                String cookie = expandedUrl.contains("bilibili.com") ? McediaConfig.getBilibiliCookie() : null;

                Mcedia.getInstance().getCacheManager().cacheVideoAsync(expandedUrl, info, cookie)
                        .thenRun(() -> Mcedia.msgToPlayer("§a[Mcedia] §f预缓存成功: " + url))
                        .exceptionally(ex -> {
                            Mcedia.msgToPlayer("§c[Mcedia] §f预缓存失败: " + url);
                            Mcedia.LOGGER.error("预缓存失败", ex);
                            return null;
                        });
            } catch (Exception e) {
                Mcedia.msgToPlayer("§c[Mcedia] §f预缓存失败: 无法解析链接 " + url);
                Mcedia.LOGGER.error("预缓存时解析链接失败", e);
            }
        });

        return 1;
    }

    private static int executeOnTargetedAgent(CommandContext<FabricClientCommandSource> context, Consumer<PlayerAgent> action) {
        Minecraft client = context.getSource().getClient();
        HitResult hitResult = client.hitResult;

        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity targetEntity = ((EntityHitResult) hitResult).getEntity();
            if (targetEntity instanceof ArmorStand) {
                PlayerAgent agent = Mcedia.getInstance().getEntityToPlayerMap().get(targetEntity);
                if (agent != null) {
                    action.accept(agent);
                    return 1;
                } else {
                    context.getSource().sendError(Component.literal("§c你看着的盔甲架不是一个有效的 Mcedia 播放器。"));
                    return 0;
                }
            }
        }

        context.getSource().sendError(Component.literal("§c请将你的准星对准一个 Mcedia 播放器盔甲架。"));
        return 0;
    }
}

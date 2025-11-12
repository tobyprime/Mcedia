package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.auth_manager.BilibiliAuthManager;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandLogin {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("mcedia")
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal("§6--- Mcedia Mod 命令帮助 ---"));
                    context.getSource().sendFeedback(Component.literal("§e/mcedia login [force] §7- 登录Bilibili。"));
                    context.getSource().sendFeedback(Component.literal("§e/mcedia logout §7- 登出Bilibili。"));
                    context.getSource().sendFeedback(Component.literal("§e/mcedia reload §7- 从文件重载配置。"));
                    context.getSource().sendFeedback(Component.literal("§e/mcedia status §7- 查看当前播放状态。"));
                    context.getSource().sendFeedback(Component.literal("§e/mcedia cache <list|clear|prefetch> §7- 管理缓存。"));
                    context.getSource().sendFeedback(Component.literal("§e/mcedia config <get|set|save|load> §7- 实时读写配置。"));
                    context.getSource().sendFeedback(Component.literal("§e/mcedia control <action> §7- 远程控制播放器。"));
                    context.getSource().sendFeedback(Component.literal("§7输入子命令并按Tab键可查看更多用法。"));
                    return 1;
                })
                // `/mcedia login` 指令
                .then(literal("login")
                        .executes(context -> {
                            if (BilibiliAuthManager.getInstance().isLoggedIn()) {
                                Mcedia.msgToPlayer("§e[Mcedia] §f你已经登录为: §b" + BilibiliAuthManager.getInstance().getUsername());
                                Mcedia.msgToPlayer("§7如需更换账号，请使用 §a/mcedia login force");
                            } else {
                                BilibiliAuthManager.getInstance().startLoginFlow();
                            }
                            return 1;
                        })
                        .then(literal("force")
                                .executes(context -> {
                                    Mcedia.msgToPlayer("§e[Mcedia] §f已强制启动新的登录流程...");
                                    BilibiliAuthManager.getInstance().startLoginFlow();
                                    return 1;
                                })
                        )
                )
                // `/mcedia logout` 指令
                .then(literal("logout")
                        .executes(context -> {
                            if (BilibiliAuthManager.getInstance().isLoggedIn()) {
                                BilibiliAuthManager.getInstance().logout();
                            } else {
                                Mcedia.msgToPlayer("§e[Mcedia] §f还没登录呢怎么登出呀。");
                            }
                            return 1;
                        })
                )
                // `/mcedia reload` 指令
                .then(literal("reload")
                        .executes(context -> {
                            McediaConfig.load();
                            context.getSource().sendFeedback(Component.literal("§a[Mcedia] §f配置文件已成功重载。"));
                            Mcedia.LOGGER.info("Mcedia configuration reloaded by command.");
                            return 1;
                        })
                )
        );
    }
}
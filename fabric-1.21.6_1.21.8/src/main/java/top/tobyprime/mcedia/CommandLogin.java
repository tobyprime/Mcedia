package top.tobyprime.mcedia;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import top.tobyprime.mcedia.auth_manager.BilibiliAuthManager;

public class CommandLogin {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("mcedia")
                // `/mcedia login` 指令
                .then(ClientCommandManager.literal("login")
                        // /mcedia login
                        .executes(context -> {
                            if (McediaConfig.BILIBILI_COOKIE != null && !McediaConfig.BILIBILI_COOKIE.isEmpty()) {
                                Mcedia.msgToPlayer("§e[Mcedia] §f已经登录了哦。");
                                Mcedia.msgToPlayer("§7如果需要更换账号或登录已失效，请使用 §a/mcedia login force");
                            } else {
                                BilibiliAuthManager.getInstance().startLoginFlow();
                            }
                            return 1;
                        })
                        // /mcedia login force
                        .then(ClientCommandManager.literal("force")
                                .executes(context -> {
                                    Mcedia.msgToPlayer("§e[Mcedia] §fOwO正在殴打b站服务器以启动新的登录流程...");
                                    BilibiliAuthManager.getInstance().startLoginFlow();
                                    return 1;
                                })
                        )
                )
                // `/mcedia logout` 指令
                .then(ClientCommandManager.literal("logout")
                        .executes(context -> {
                            McediaConfig.saveCookie(""); // 清空Cookie并保存
                            Mcedia.msgToPlayer("§a[Mcedia] §f好耶！成功退出登录惹。");
                            return 1;
                        })
                )
        );
    }
}
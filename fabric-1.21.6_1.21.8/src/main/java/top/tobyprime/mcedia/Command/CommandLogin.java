// CommandLogin.java
package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.auth_manager.BilibiliAuthManager;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandLogin {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> bilibiliNode = literal("bilibili")
                .executes(ctx -> {
                    CommandHelp.sendHelpPage(ctx.getSource(), "bilibili");
                    return 1;
                })
                .then(literal("login")
                        .executes(context -> {
                            if (BilibiliAuthManager.getInstance().isLoggedIn()) {
                                Mcedia.msgToPlayer("§e[Mcedia] §f你已经登录为: §b" + BilibiliAuthManager.getInstance().getUsername());
                                Mcedia.msgToPlayer("§7如需更换账号，请使用 §a/mcedia bilibili login force");
                            } else {
                                BilibiliAuthManager.getInstance().startLoginFlow();
                            }
                            return 1;
                        })
                        .then(literal("force")
                                .executes(context -> {
                                    Mcedia.msgToPlayer("§e[Mcedia] §f已殴打b站服务器强制启动新的登录流程OwO...");
                                    BilibiliAuthManager.getInstance().startLoginFlow();
                                    return 1;
                                })
                        )
                )
                .then(literal("logout")
                        .executes(context -> {
                            if (BilibiliAuthManager.getInstance().isLoggedIn()) {
                                BilibiliAuthManager.getInstance().logout();
                            } else {
                                Mcedia.msgToPlayer("§e[Mcedia] §f还没登录呢登出什么呀。");
                            }
                            return 1;
                        })
                );
        dispatcher.register(literal("mcedia").then(bilibiliNode));
        dispatcher.register(literal("mcedia").then(literal("b").redirect(bilibiliNode.build())));
    }
}
package top.tobyprime.mcedia.commands;// CommandLogin.java

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import top.tobyprime.mcedia.Utils;
import top.tobyprime.mcedia.bilibili.BilibiliAuthManager;
import top.tobyprime.mcedia.client.McediaClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandBilibili {

    public static void login() {
        BilibiliAuthManager.getInstance().loginAsync((qrCodeUrl -> {
            Utils.msgToPlayer("请在浏览器打开并使用手机端 bilibili 扫码:");
            Style style = Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, qrCodeUrl))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("在浏览器中打开二维码，并使用B站手机App扫描")));

            Utils.msgToPlayer(Component.literal("§b§n[点我打开二维码]").setStyle(style));
        })).thenAccept(Utils::msgToPlayer).thenRun(McediaClient::SaveConfig).thenRun(()->{
            Utils.msgToPlayer("登录成功: §b" + BilibiliAuthManager.getInstance().getAccountStatus().username);
        });

    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> bilibiliNode = literal("bilibili")
                .then(literal("login")
                        .executes(context -> {
                            if (BilibiliAuthManager.getInstance().getAccountStatus().isLoggedIn) {
                                Utils.msgToPlayer("你已经登录为: §b" + BilibiliAuthManager.getInstance().getAccountStatus().username);
                                Utils.msgToPlayer("如需登出，请使用 §a/mcedia bilibili logout");
                            } else {
                                login();
                            }
                            return 1;
                        })
                        .then(literal("force")
                                .executes(context -> {
                                    login();
                                    return 1;
                                })
                        )
                ).then(literal("account").executes(ctx -> {

                    var status = BilibiliAuthManager.getInstance().getAccountStatus();
                    if (status.isLoggedIn) {
                        Utils.msgToPlayer("你是 " + status.username);
                    } else {
                        Utils.msgToPlayer("未登录");
                    }
                    return 1;
                }))
                .then(literal("logout")
                        .executes(context -> {
                            if (BilibiliAuthManager.getInstance().getAccountStatus().isLoggedIn) {
                                BilibiliAuthManager.getInstance().logout();
                                McediaClient.SaveConfig();
                                Utils.msgToPlayer("登出成功");

                            } else {
                                Utils.msgToPlayer("无 bilibili 登录记录");
                            }
                            return 1;
                        })
                );
        dispatcher.register(literal("mcedia").then(bilibiliNode));
    }
}
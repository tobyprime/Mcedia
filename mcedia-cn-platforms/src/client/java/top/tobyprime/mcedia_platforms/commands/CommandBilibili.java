package top.tobyprime.mcedia_platforms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_platforms.auth.BilibiliAuthManager;
import top.tobyprime.mcedia_platforms.client.McediaPlatformsClient;

import java.net.URI;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class CommandBilibili {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandBilibili.class);

    private CommandBilibili() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LOGGER.info("CommandBilibili.register: 注册命令");
        LiteralArgumentBuilder<FabricClientCommandSource> bilibiliNode = literal("bilibili")
                .then(literal("login").executes(context -> {
                    LOGGER.info("执行 /mcedia platforms bilibili login 命令");
                    if (BilibiliAuthManager.getInstance().getAccountStatus().isLoggedIn) {
                        LOGGER.info("用户已登录，提示已登录信息");
                        send(context.getSource(), "你已经登录为: §b" + BilibiliAuthManager.getInstance().getAccountStatus().username);
                        send(context.getSource(), "如需登出，请使用 §a/mcedia platforms bilibili logout");
                    } else {
                        LOGGER.info("用户未登录，启动登录流程");
                        login(context.getSource());
                    }
                    return 1;
                }))
                .then(literal("account").executes(ctx -> {
                    var status = BilibiliAuthManager.getInstance().getAccountStatus();
                    if (status.isLoggedIn) {
                        send(ctx.getSource(), "你是 " + status.username);
                    } else {
                        send(ctx.getSource(), "未登录");
                    }
                    return 1;
                }))
                .then(literal("logout").executes(context -> {
                    if (BilibiliAuthManager.getInstance().getAccountStatus().isLoggedIn) {
                        BilibiliAuthManager.getInstance().logout();
                        McediaPlatformsClient.saveConfig();
                        send(context.getSource(), "登出成功");
                    } else {
                        send(context.getSource(), "无 bilibili 登录记录");
                    }
                    return 1;
                }));
        var platformsNode = literal("platforms").then(bilibiliNode).build();
        var existingMcedia = dispatcher.getRoot().getChild("mcedia");
        if (existingMcedia != null) {
            existingMcedia.addChild(platformsNode);
        } else {
            dispatcher.register(literal("mcedia").then(platformsNode));
        }
    }

    private static void login(FabricClientCommandSource source) {
        LOGGER.info("login: 调用BilibiliAuthManager.loginAsync");
        BilibiliAuthManager.getInstance().loginAsync(qrCodeUrl -> {
            LOGGER.info("loginAsync回调: 收到QR码URL, url长度={}", qrCodeUrl.length());
            send(source, "请在浏览器打开并使用手机端 bilibili 扫码:");
            Style style = Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(qrCodeUrl)))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("在浏览器中打开二维码，并使用B站手机App扫描")));
            send(source, Component.literal("§b§n[点我打开二维码]").setStyle(style));
            LOGGER.info("loginAsync回调: 二维码消息已发送给玩家");
        }).thenAccept(message -> {
            LOGGER.info("loginAsync完成: message={}", message);
            send(source, message);
            if ("登录成功".equals(message)) {
                McediaPlatformsClient.saveConfig();
                send(source, "登录成功: §b" + BilibiliAuthManager.getInstance().getAccountStatus().username);
            }
        });
    }

    private static void send(FabricClientCommandSource source, String message) {
        send(source, Component.literal(message));
    }

    private static void send(FabricClientCommandSource source, Component component) {
        Minecraft.getInstance().execute(() -> source.sendFeedback(component));
    }
}

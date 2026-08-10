package top.tobyprime.mcedia_platforms.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_platforms.auth.NeteaseAuthManager;
import top.tobyprime.mcedia_platforms.client.McediaPlatformsClient;

import java.net.URI;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * 网易云账号命令。支持手机 App 扫码登录与手动 Cookie 导入两种方式，
 * 登录后解析器可请求更高音质（VIP 无损 / 非 VIP 高码率）。
 */
public final class CommandNetease {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandNetease.class);

    private CommandNetease() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LOGGER.info("CommandNetease.register: 注册命令");
        var neteaseNode = literal("netease")
                .then(literal("login").executes(context -> {
                    if (NeteaseAuthManager.getInstance().getAccountStatus().isLoggedIn) {
                        send(context.getSource(), "你已经登录为: §b" + NeteaseAuthManager.getInstance().getAccountStatus().nickname);
                        send(context.getSource(), "如需登出，请使用 §a/mcedia platforms netease logout");
                    } else {
                        login(context.getSource());
                    }
                    return 1;
                }))
                .then(literal("account").executes(ctx -> {
                    var status = NeteaseAuthManager.getInstance().getAccountStatus();
                    if (status.isLoggedIn) {
                        send(ctx.getSource(), "你是 " + status.nickname);
                    } else {
                        send(ctx.getSource(), "未登录");
                    }
                    return 1;
                }))
                .then(literal("cookie")
                        .then(argument("value", StringArgumentType.greedyString()).executes(context -> {
                            var value = StringArgumentType.getString(context, "value");
                            LOGGER.info("导入网易云 Cookie，长度={}", value.length());
                            NeteaseAuthManager.getInstance().saveCookies(value);
                            McediaPlatformsClient.saveConfig();
                            send(context.getSource(), "已导入网易云 Cookie，正在校验登录态...");
                            return 1;
                        })))
                .then(literal("logout").executes(context -> {
                    if (NeteaseAuthManager.getInstance().getAccountStatus().isLoggedIn) {
                        NeteaseAuthManager.getInstance().logout();
                        McediaPlatformsClient.saveConfig();
                        send(context.getSource(), "登出成功");
                    } else {
                        send(context.getSource(), "无网易云登录记录");
                    }
                    return 1;
                }));
        attachToPlatforms(dispatcher, neteaseNode.build());
    }

    private static void login(FabricClientCommandSource source) {
        LOGGER.info("login: 调用NeteaseAuthManager.loginAsync");
        NeteaseAuthManager.getInstance().loginAsync(qrCodeUrl -> {
            LOGGER.info("loginAsync回调: 收到QR码URL, url长度={}", qrCodeUrl.length());
            send(source, "请在浏览器打开并使用手机端网易云音乐扫码:");
            Style style = Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(qrCodeUrl)))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("在浏览器中打开二维码，并使用手机网易云App扫描")));
            send(source, Component.literal("§b§n[点我打开二维码]").setStyle(style));
            LOGGER.info("loginAsync回调: 二维码消息已发送给玩家");
        }).thenAccept(message -> {
            LOGGER.info("loginAsync完成: message={}", message);
            send(source, message);
            if ("登录成功".equals(message)) {
                McediaPlatformsClient.saveConfig();
                send(source, "登录成功: §b" + NeteaseAuthManager.getInstance().getAccountStatus().nickname);
            }
        });
    }

    /** 将子命令挂到已存在的 /mcedia platforms 节点下；节点不存在时创建，避免覆盖其他平台命令。 */
    private static void attachToPlatforms(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandNode<FabricClientCommandSource> node) {
        var existingMcedia = dispatcher.getRoot().getChild("mcedia");
        if (existingMcedia == null) {
            dispatcher.register(literal("mcedia").then(literal("platforms").then(node)));
            return;
        }
        var existingPlatforms = existingMcedia.getChild("platforms");
        if (existingPlatforms != null) {
            existingPlatforms.addChild(node);
        } else {
            existingMcedia.addChild(literal("platforms").then(node).build());
        }
    }

    private static void send(FabricClientCommandSource source, String message) {
        send(source, Component.literal(message));
    }

    private static void send(FabricClientCommandSource source, Component component) {
        Minecraft.getInstance().execute(() -> source.sendFeedback(component));
    }
}

package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.manager.BilibiliAuthManager;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandLogin {
    private static final String[] SUPPORTED_BROWSERS = {
            "none", "brave", "chrome", "chromium", "edge", "firefox", "opera", "safari", "vivaldi"
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var bilibiliNode = literal("bilibili")
                .executes(context -> {
                    if (BilibiliAuthManager.getInstance().isLoggedIn()) {
                        Mcedia.msgToPlayer("§e[Mcedia] §fBilibili 已登录为: §b" + BilibiliAuthManager.getInstance().getUsername());
                        Mcedia.msgToPlayer("§7如需更换账号，请重新执行此指令或扫码。");
                    } else {
                        Mcedia.msgToPlayer("§e[Mcedia] §fBilibili 未登录，正在启动扫码登录流程...");
                        BilibiliAuthManager.getInstance().startLoginFlow();
                    }
                    return 1;
                })
                .then(literal("logout")
                        .executes(context -> {
                            if (BilibiliAuthManager.getInstance().isLoggedIn()) {
                                BilibiliAuthManager.getInstance().logout();
                                Mcedia.msgToPlayer("§a[Mcedia] §fBilibili 已登出。");
                            } else {
                                Mcedia.msgToPlayer("§e[Mcedia] §fBilibili 账号未登录。");
                            }
                            return 1;
                        })
                );
        var browserNode = literal("browser")
                .executes(c -> {
                    Mcedia.msgToPlayer("§e当前 yt-dlp 浏览器 Cookie 设置: §f" + McediaConfig.getYtdlpBrowserCookie());
                    Mcedia.msgToPlayer("§7用法: /mcedia login browser <浏览器名称>");
                    return 1;
                })
                .then(argument("browser_name", StringArgumentType.string())
                        .suggests(CommandLogin::getBrowserSuggestions)
                        .executes(c -> {
                            String browser = StringArgumentType.getString(c, "browser_name");
                            Mcedia.LOGGER.info("指令收到浏览器名称: '{}'", browser);
                            if (Arrays.asList(SUPPORTED_BROWSERS).contains(browser.toLowerCase())) {
                                McediaConfig.setYtdlpBrowserCookie(browser);
                                Mcedia.LOGGER.info("McediaConfig.YTDLP_BROWSER_COOKIE 静态字段已更新为: '{}'", McediaConfig.getYtdlpBrowserCookie());
                                McediaConfig.saveCookieConfig();
                                Mcedia.LOGGER.info("已调用 McediaConfig.saveCookieConfig()");
                                if (browser.equalsIgnoreCase("none")) {
                                    Mcedia.msgToPlayer("§a[Mcedia] §f已禁用浏览器 Cookie。");
                                } else {
                                    Mcedia.msgToPlayer("§a[Mcedia] §fyt-dlp 已设置为从 §e" + browser + "§f 读取 Cookie。");
                                }
                            } else {
                                Mcedia.msgToPlayer("§c[Mcedia] §f不支持的浏览器: " + browser);
                            }
                            return 1;
                        })
                );
        var loginNode = literal("login")
                .executes(c -> {
                    Mcedia.msgToPlayer("§e用法: /mcedia login <bilibili|browser>");
                    return 1;
                })
                .then(bilibiliNode)
                .then(browserNode);

        var mcediaRootNode = dispatcher.getRoot().getChild("mcedia");
        if (mcediaRootNode == null) {
            dispatcher.register(literal("mcedia").then(loginNode));
        } else {
            mcediaRootNode.addChild(loginNode.build());
        }
    }

    private static CompletableFuture<Suggestions> getBrowserSuggestions(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(SUPPORTED_BROWSERS, builder);
    }
}
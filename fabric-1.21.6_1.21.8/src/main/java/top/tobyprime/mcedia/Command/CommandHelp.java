package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandHelp {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("mcedia")
                .executes(ctx -> {
                    sendHelpPage(ctx.getSource(), "main");
                    return 1;
                })
                .then(literal("help")
                        .executes(ctx -> {
                            sendHelpPage(ctx.getSource(), "main");
                            return 1;
                        })
                        .then(argument("category", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("control");
                                    builder.suggest("config");
                                    builder.suggest("cache");
                                    builder.suggest("account");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String category = StringArgumentType.getString(ctx, "category");
                                    sendHelpPage(ctx.getSource(), category);
                                    return 1;
                                })
                        )
                )
        );
    }

    private static void sendHelpPage(FabricClientCommandSource source, String category) {
        source.sendFeedback(Component.literal("§6--- Mcedia Mod Help ---"));

        switch (category.toLowerCase()) {
            case "control":
                source.sendFeedback(Component.literal("§e/mcedia control <action>"));
                source.sendFeedback(Component.literal("  §7控制你准星对准的播放器。"));
                source.sendFeedback(Component.literal("  §bActions: §fpause, resume, stop, skip, seek <time>"));

                source.sendFeedback(Component.literal("§e/mcedia control set <property> <value>"));
                source.sendFeedback(Component.literal("  §7设置播放器参数。"));
                source.sendFeedback(Component.literal("  §bProperties: §furl, volume, loop, offset, audio"));

                source.sendFeedback(Component.literal("§e/mcedia control audio secondary_enabled <true|false>"));
                source.sendFeedback(Component.literal("  §7启用或禁用副声源。"));
                break;

            case "config":
                source.sendFeedback(Component.literal("§e/mcedia config <parameter>"));
                source.sendFeedback(Component.literal("  §7查询一个全局配置的值。"));

                source.sendFeedback(Component.literal("§e/mcedia config set <parameter> <value>"));
                source.sendFeedback(Component.literal("  §7设置一个全局配置的值并保存。"));

                source.sendFeedback(Component.literal("§e/mcedia config load|save"));
                source.sendFeedback(Component.literal("  §7从文件加载或保存配置。"));
                source.sendFeedback(Component.literal("§7(输入 /mcedia config set 后按Tab查看所有可用参数)"));
                break;

            case "cache":
                source.sendFeedback(Component.literal("§e/mcedia cache <action>"));
                source.sendFeedback(Component.literal("  §7管理视频缓存。"));
                source.sendFeedback(Component.literal("  §bActions: §flist, clear, prefetch <url>"));
                break;

            case "account":
                source.sendFeedback(Component.literal("§e/mcedia login [force]"));
                source.sendFeedback(Component.literal("  §7登录或强制重新登录Bilibili账号。"));

                source.sendFeedback(Component.literal("§e/mcedia logout"));
                source.sendFeedback(Component.literal("  §7登出Bilibili账号。"));
                break;

            default: // main page
                source.sendFeedback(Component.literal("§7输入 §a/mcedia help <category> §7查看详细信息。"));
                source.sendFeedback(Component.literal("§7可用分类: §bcontrol, config, cache, account"));
                source.sendFeedback(Component.literal("§6-------------------------"));
                source.sendFeedback(Component.literal("§e/mcedia init §7- (看着盔甲架) 初始化为播放器。"));
                source.sendFeedback(Component.literal("§e/mcedia status §7- (看着播放器) 显示当前状态。"));
                break;
        }
    }
}
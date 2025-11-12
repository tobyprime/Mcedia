package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

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
                                    builder.suggest("main");
                                    builder.suggest("control");
                                    builder.suggest("playlist");
                                    builder.suggest("preset");
                                    builder.suggest("config");
                                    builder.suggest("cache");
                                    builder.suggest("bilibili");
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

    public static void sendHelpPage(FabricClientCommandSource source, String category) {
        source.sendFeedback(Component.literal("§6--- Mcedia Mod 帮助 ---"));

        switch (category.toLowerCase()) {
            case "control" -> {
                source.sendFeedback(Component.literal("§e/mcedia init §7- (看着盔甲架) 初始化为播放器。"));
                source.sendFeedback(Component.literal("§e/mcedia status §7- (看着播放器) 显示当前播放状态。"));
                source.sendFeedback(Component.literal("§6--- 播放控制 (单体) ---"));
                source.sendFeedback(Component.literal("§b/mcedia control <操作>"));
                source.sendFeedback(Component.literal("  §fpause§7, §fresume§7, §fstop§7, §fskip§7, §fseek <时间>"));
                source.sendFeedback(Component.literal("§6--- 参数设置 (单体) ---"));
                source.sendFeedback(Component.literal("§b/mcedia control set <属性> [值]"));
                source.sendFeedback(Component.literal("  §furl§7, §fvolume§7, §floop§7, §fscreen offset§7, §faudio ..."));
                source.sendFeedback(Component.literal("§6--- 全局控制 (全体) ---"));
                source.sendFeedback(Component.literal("§b/mcedia control all <操作>"));
                source.sendFeedback(Component.literal("  §7同时控制世界中所有的播放器。"));
                source.sendFeedback(Component.literal("  §fActions: §fpause, resume, stop"));
            }
            case "playlist" -> {
                source.sendFeedback(Component.literal("§7(以下指令均作用于你准星对准的播放器)"));
                source.sendFeedback(Component.literal("§e/mcedia playlist list [page] §7- 显示交互式播放列表。"));
                source.sendFeedback(Component.literal("  §b(悬浮查看详情，点击可获得移除指令)"));
                source.sendFeedback(Component.literal("§e/mcedia playlist add <URL> §7- 在列表末尾添加一个视频。"));
                source.sendFeedback(Component.literal("§e/mcedia playlist insert <序号> <URL> §7- 在指定位置插入视频。"));
                source.sendFeedback(Component.literal("§e/mcedia playlist remove <序号> §7- 移除指定位置的视频。"));
                source.sendFeedback(Component.literal("§e/mcedia playlist clear §7- 清空整个播放列表。"));
                source.sendFeedback(Component.literal("§8(使用指令修改列表会自动切换到指令播放模式)"));
                source.sendFeedback(Component.literal("§6--- 书本高级配置 ---"));
                source.sendFeedback(Component.literal("§7在主手书本中，你可以在URL下方添加参数来控制播放："));
                source.sendFeedback(Component.literal("  §b时间戳: §e[HH:]MM:SS §7或纯秒数"));
                source.sendFeedback(Component.literal("  §bB站分P: §ep<数字> §7(例如: p3)"));
                source.sendFeedback(Component.literal("  §b清晰度: §e1080P§7, §e720P §7等..."));
                source.sendFeedback(Component.literal("  §b ps：可用清晰度可在成功播放后视频信息的悬浮事件中查看"));
                source.sendFeedback(Component.literal("§7这些参数可以写在§b多行§7、§b顺序打乱§7，也可以写在§b同一行§7！"));
                source.sendFeedback(Component.literal("  §b例如: §eURL下接一行 §b'p3 720P 01:30'"));
            }
            case "preset" -> {
                source.sendFeedback(Component.literal("§7(以下指令均作用于你准星对准的播放器)"));
                source.sendFeedback(Component.literal("§e/mcedia preset save <名称> §7- 保存当前播放器的位置/音频设置为预设。"));
                source.sendFeedback(Component.literal("§e/mcedia preset load <名称> §7- 应用一个预设到当前播放器。"));
                source.sendFeedback(Component.literal("§e/mcedia preset delete <名称> §7- 删除一个已保存的预设。"));
                source.sendFeedback(Component.literal("§e/mcedia preset list [page] §7- 列出所有已保存的预设。"));
                source.sendFeedback(Component.literal("  §b(悬浮查看参数，点击打开预设文件)"));
                source.sendFeedback(Component.literal("§e/mcedia preset reload §7- §b(热更新)§7 从文件重载预设。"));
            }
            case "config" -> {
                source.sendFeedback(Component.literal("§e/mcedia config get <参数名> §7- 获取参数当前值。"));
                source.sendFeedback(Component.literal("§e/mcedia config set <参数名> <值> §7- 设置参数值并自动保存。"));
                source.sendFeedback(Component.literal("  §b(输入set后按Tab键可查看所有可用参数)"));
                source.sendFeedback(Component.literal("§e/mcedia config save §7- 手动保存当前配置到文件。"));
                source.sendFeedback(Component.literal("§e/mcedia config load §7- 从文件重载配置。"));
            }
            case "cache" -> {
                source.sendFeedback(Component.literal("§e/mcedia cache list §7- 列出所有已缓存的文件。"));
                source.sendFeedback(Component.literal("§e/mcedia cache clear §7- 清空所有视频缓存。"));
                source.sendFeedback(Component.literal("§e/mcedia cache prefetch <URL> §7- 在后台预先缓存一个视频。"));
            }
            case "bilibili" -> {
                source.sendFeedback(Component.literal("§e/mcedia bilibili login [force] §7- (或用/mcedia b ...) 登录B站账号。"));
                source.sendFeedback(Component.literal("  §fforce §7- 强制重新登录，用于更换账号。"));
                source.sendFeedback(Component.literal("§e/mcedia bilibili logout §7- 登出当前B站账号。"));
            }
            default -> {
                source.sendFeedback(Component.literal("§7欢迎使用 Mcedia! 输入 §a/mcedia help <分类> §7查看详细信息。"));
                source.sendFeedback(Component.literal("§7可用分类: §bcontrol, playlist, preset, config, cache, bilibili"));
                source.sendFeedback(Component.literal("§6-------------------------"));
                source.sendFeedback(Component.literal("§e/mcedia init §7- §b核心指令! §7将你看着的盔甲架变为播放器。"));
                source.sendFeedback(Component.literal("§e/mcedia control set url <链接> §7- §b快速开始! §7让播放器播放链接。"));
                source.sendFeedback(Component.literal("§e/mcedia status §7- 查看播放器当前状态。"));
            }
        }
    }
}
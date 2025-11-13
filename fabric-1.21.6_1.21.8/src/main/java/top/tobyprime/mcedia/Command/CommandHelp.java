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
                                    // 指南部分
                                    builder.suggest("guide-setup");
                                    builder.suggest("guide-playlist-book");
                                    builder.suggest("guide-config-book");
                                    // 指令参考部分
                                    builder.suggest("control");
                                    builder.suggest("playlist");
                                    builder.suggest("danmaku");
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
            // =================================================================
            // 指南部分 (GUIDES)
            // =================================================================
            case "guide-setup" -> {
                source.sendFeedback(Component.literal("§a>> 快速入门指南 <<"));
                source.sendFeedback(Component.literal("§e1. §f放置一个盔甲架。"));
                source.sendFeedback(Component.literal("§e2. §f将准星对准盔甲架，输入指令 §b/mcedia init§f。"));
                source.sendFeedback(Component.literal("§e3. §f准备一本§b书与笔§f。"));
                source.sendFeedback(Component.literal("§e4. §f在书的第一页写入一个视频链接 (例如B站链接)。"));
                source.sendFeedback(Component.literal("§e5. §f将这本书放入盔甲架的§b主手§f (拿剑/工具的手)。"));
                source.sendFeedback(Component.literal("§6咻咻咻！视频就开始播放了。"));
                source.sendFeedback(Component.literal("§7想了解更多关于播放列表书的用法？请输入:"));
                source.sendFeedback(Component.literal("  §a/mcedia help guide-playlist-book"));
                source.sendFeedback(Component.literal("§7想进行高级设置 (如调整屏幕位置)？请输入:"));
                source.sendFeedback(Component.literal("  §a/mcedia help guide-config-book"));
            }
            case "guide-playlist-book" -> {
                source.sendFeedback(Component.literal("§a>> 指南: 主手播放列表书 <<"));
                source.sendFeedback(Component.literal("§7放在盔甲架§b主手§7的“书与笔”或“成书”用于控制§b播放内容§7。"));
                source.sendFeedback(Component.literal("§6基础用法:"));
                source.sendFeedback(Component.literal("§7- 每一行写一个视频链接，构成一个播放列表。"));
                source.sendFeedback(Component.literal("§7- 放到播放实例的主手上后播放列表会自动更新。"));
                source.sendFeedback(Component.literal("§6高级用法 (参数):"));
                source.sendFeedback(Component.literal("§7在视频链接§b下方§7的一行或多行中，可以添加参数，无需特定顺序:"));
                source.sendFeedback(Component.literal("  §b- §e分P§7: §f p<数字>  (例如: p3)"));
                source.sendFeedback(Component.literal("  §b- §e时间戳§7: §f [HH:]MM:SS 或 纯秒数 (例如: 01:30)"));
                source.sendFeedback(Component.literal("  §b- §e清晰度§7: §f 清晰度名称 (例如: 1080P 高码率)"));
                source.sendFeedback(Component.literal("§7所有的可用清晰度都会显示在播放成功后的聊天框提示的悬浮事件上§7"));
                source.sendFeedback(Component.literal("§b示例:"));
                source.sendFeedback(Component.literal("  §7https://www.bilibili.com/video/BV1xx411c79H"));
                source.sendFeedback(Component.literal("  §ep2 360P 流畅 00:35"));
            }
            case "guide-config-book" -> {
                source.sendFeedback(Component.literal("§a>> 指南: 副手配置书 <<"));
                source.sendFeedback(Component.literal("§7放在盔甲架§b副手§7的“书与笔”或“成书”用于§b高级设置§7。"));
                source.sendFeedback(Component.literal("§7每一页控制不同的设置，§b严格按顺序§7："));
                source.sendFeedback(Component.literal("§6第1页: 屏幕位置 (4行)"));
                source.sendFeedback(Component.literal("  §eX偏移, Y偏移, Z偏移, 整体缩放"));
                source.sendFeedback(Component.literal("§6第2页: 音源设置 (主/副)"));
                source.sendFeedback(Component.literal("  §7(6行为主音源，空一行后，再6行为副音源)"));
                source.sendFeedback(Component.literal("  §eX偏移, Y偏移, Z偏移, 最大音量, 最小范围, 最大范围"));
                source.sendFeedback(Component.literal("§6第3页: 其他设置"));
                source.sendFeedback(Component.literal("  §e第一行: §flooping §7(开启列表/单项循环)"));
                source.sendFeedback(Component.literal("  §e第二行: §fautoplay §7(开启B站分P/番剧连播)"));
                source.sendFeedback(Component.literal("§6第4页: 全局清晰度"));
                source.sendFeedback(Component.literal("  §7(只写清晰度名称，例如: §e1080P 高清§7, 或 §e自动§7)"));
                source.sendFeedback(Component.literal("§6第5页: 弹幕设置"));
                source.sendFeedback(Component.literal("  §e第一行包含“§f弹幕§e”二字即开启。"));
                source.sendFeedback(Component.literal("  §e后续行: 区域(%), 不透明度(%), 字体缩放, 速度缩放, 屏蔽<类型>"));
            }

            // =================================================================
            // 指令参考部分 (COMMANDS)
            // =================================================================
            case "control" -> {
                source.sendFeedback(Component.literal("§a>> 指令参考: 控制 <<"));
                source.sendFeedback(Component.literal("§6--- 播放控制 ---"));
                source.sendFeedback(Component.literal("§e/mcedia control <action>"));
                source.sendFeedback(Component.literal("  §fActions: §fpause, resume, stop, skip, seek <时间>"));
                source.sendFeedback(Component.literal("§6--- 参数设置 ---"));
                source.sendFeedback(Component.literal("§e/mcedia control set <property> [value]"));
                source.sendFeedback(Component.literal("  §fProperties: §furl, volume, loop, autoplay, danmaku..."));
                source.sendFeedback(Component.literal("§7输入 §a/mcedia help danmaku §7查看详细弹幕指令。"));
            }
            case "playlist" -> {
                source.sendFeedback(Component.literal("§a>> 指令参考: 播放列表 <<"));
                source.sendFeedback(Component.literal("§e/mcedia playlist list [page] §7- 显示播放列表。"));
                source.sendFeedback(Component.literal("§e/mcedia playlist add <URL> §7- 添加视频。"));
                source.sendFeedback(Component.literal("§e/mcedia playlist insert <序号> <URL> §7- 插入视频。"));
                source.sendFeedback(Component.literal("§e/mcedia playlist remove <序号> §7- 移除视频。"));
                source.sendFeedback(Component.literal("§e/mcedia playlist clear §7- 清空列表。"));
                source.sendFeedback(Component.literal("§7§o要了解如何用书本编辑播放列表，请看:"));
                source.sendFeedback(Component.literal("  §a/mcedia help guide-playlist-book"));
            }
            case "danmaku" -> {
                source.sendFeedback(Component.literal("§a>> 指令参考: 弹幕 <<"));
                source.sendFeedback(Component.literal("§6--- 弹幕主开关 ---"));
                source.sendFeedback(Component.literal("§e/mcedia control set danmaku <true|false>"));
                source.sendFeedback(Component.literal("§6--- 弹幕显示设置 ---"));
                source.sendFeedback(Component.literal("§e/mcedia control set danmaku area <0-100>"));
                source.sendFeedback(Component.literal("§e/mcedia control set danmaku opacity <0-100>"));
                source.sendFeedback(Component.literal("§e/mcedia control set danmaku font_scale <数值>"));
                source.sendFeedback(Component.literal("§e/mcedia control set danmaku speed_scale <数值>"));
                source.sendFeedback(Component.literal("§6--- 弹幕类型屏蔽 ---"));
                source.sendFeedback(Component.literal("§e/mcedia control set danmaku type <类型> <true|false>"));
                source.sendFeedback(Component.literal("  §b类型: §fscrolling§7, §ftop§7, §fbottom"));
                source.sendFeedback(Component.literal("§7§o要了解如何用书本配置弹幕，请看:"));
                source.sendFeedback(Component.literal("  §a/mcedia help guide-config-book"));
            }
            case "preset" -> {
                source.sendFeedback(Component.literal("§a>> 指令参考: 预设 <<"));
                source.sendFeedback(Component.literal("§e/mcedia preset save <名称> §7- 保存当前配置为预设。"));
                source.sendFeedback(Component.literal("§e/mcedia preset load <名称> §7- 应用预设。"));
                source.sendFeedback(Component.literal("§e/mcedia preset delete <名称> §7- 删除预设。"));
                source.sendFeedback(Component.literal("§e/mcedia preset list [page] §7- 列出所有预设。"));
                source.sendFeedback(Component.literal("§e/mcedia preset reload §7- 从文件重载所有预设。"));
            }
            case "config" -> {
                source.sendFeedback(Component.literal("§a>> 指令参考: 全局配置 <<"));
                source.sendFeedback(Component.literal("§e/mcedia config set <参数> <值> §7- 修改全局配置文件。"));
                source.sendFeedback(Component.literal("§e/mcedia config get <参数> §7- 查看全局配置项。"));
                source.sendFeedback(Component.literal("§e/mcedia config save §7 | §eload §7- 手动保存/重载配置。"));
            }
            case "cache" -> {
                source.sendFeedback(Component.literal("§a>> 指令参考: 缓存 <<"));
                source.sendFeedback(Component.literal("§e/mcedia cache list §7- 列出已缓存的文件。"));
                source.sendFeedback(Component.literal("§e/mcedia cache clear §7- 清空所有视频缓存。"));
                source.sendFeedback(Component.literal("§e/mcedia cache prefetch <URL> §7- 在后台预缓存一个视频。"));
            }
            case "bilibili" -> {
                source.sendFeedback(Component.literal("§a>> 指令参考: Bilibili <<"));
                source.sendFeedback(Component.literal("§e/mcedia bilibili login [force] §7- 登录B站以观看高清/会员内容。"));
                source.sendFeedback(Component.literal("§e/mcedia bilibili logout §7- 登出当前B站账号。"));
            }
            default -> { // main page
                source.sendFeedback(Component.literal("§7欢迎使用 Mcedia!"));
                source.sendFeedback(Component.literal("§6--- 新手入门 ---"));
                source.sendFeedback(Component.literal("§f第一次使用？请输入 §a/mcedia help guide-setup"));
                source.sendFeedback(Component.literal("§6--- 功能指南 ---"));
                source.sendFeedback(Component.literal("§f想学习如何用书本控制播放？请输入 §a/mcedia help guide-playlist-book"));
                source.sendFeedback(Component.literal("§f想学习如何调整屏幕、音频和弹幕？请输入 §a/mcedia help guide-config-book"));
                source.sendFeedback(Component.literal("§6--- 指令速查 ---"));
                source.sendFeedback(Component.literal("§f需要查找具体指令？请输入 §a/mcedia help <分类>"));
                source.sendFeedback(Component.literal("  §7分类: §bcontrol, playlist, danmaku, preset, config, cache, bilibili"));
            }
        }
    }
}
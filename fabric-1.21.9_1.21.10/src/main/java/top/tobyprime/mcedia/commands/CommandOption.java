package top.tobyprime.mcedia.commands;// CommandLogin.java

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.Utils;
import top.tobyprime.mcedia.client.McediaClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandOption {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> qualityNode = literal("quality").then(literal("8k").executes(context -> {
            Configs.QUALITY = 3;
            McediaClient.SaveConfig();
            Utils.msgToPlayer("已设置为优先播放 8k 视频");

            return 1;
        })).then(literal("4k").executes(context -> {
            Configs.QUALITY = 2;
            McediaClient.SaveConfig();
            Utils.msgToPlayer("已设置为优先播放 4k 视频");


            return 1;
        })).then(literal("common").executes(context -> {
            Configs.QUALITY = 1;
            McediaClient.SaveConfig();
            Utils.msgToPlayer("已设置为优先播放 1080P 视频");

            return 1;
        })).then(literal("min").executes(context -> {
            Configs.QUALITY = 0;
            McediaClient.SaveConfig();
            Utils.msgToPlayer("已设置为优先最低画质");

            return 1;
        }));
        LiteralArgumentBuilder<FabricClientCommandSource> directLinkNode = literal("direct_link").then(literal("toggle").executes(ctx->{
            Configs.ALLOW_DIRECT_LINK = !Configs.ALLOW_DIRECT_LINK;
            McediaClient.SaveConfig();
            if (Configs.ALLOW_DIRECT_LINK) {
                Utils.msgToPlayer("已允许播放直链");
            }else {
                Utils.msgToPlayer("已禁止播放直链");
            }

            return 1;
        }));
        LiteralArgumentBuilder<FabricClientCommandSource> yhdmNode = literal("yhdm").then(literal("toggle").executes(ctx -> {
            Configs.ALLOW_YHDM = !Configs.ALLOW_YHDM;
            McediaClient.SaveConfig();
            if (Configs.ALLOW_YHDM) {
                Utils.msgToPlayer("已允许播放樱花动漫");
            } else {
                Utils.msgToPlayer("已禁止播放樱花动漫");
            }

            return 1;
        }));

        LiteralArgumentBuilder<FabricClientCommandSource> maxCountNode = literal("max").then(argument("count", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
            Configs.MAX_PLAYER_COUNT = IntegerArgumentType.getInteger(ctx, "count");
            McediaClient.SaveConfig();
            Utils.msgToPlayer("已设置最大播放器数量为:" + Configs.MAX_PLAYER_COUNT);

            return 1;
        }));

        LiteralArgumentBuilder<FabricClientCommandSource> maxNonLowOverheadCountNode = literal("max_non_low_overhead").then(argument("count", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
            Configs.MAX_NON_LOW_OVERHEAD_PLAYER_COUNT = IntegerArgumentType.getInteger(ctx, "count");
            McediaClient.SaveConfig();
            Utils.msgToPlayer("已设置最大高开销播放器数量为:" + Configs.MAX_NON_LOW_OVERHEAD_PLAYER_COUNT);

            return 1;
        }));
        LiteralArgumentBuilder<FabricClientCommandSource> volumeNode = literal("volume").then(argument("volume", FloatArgumentType.floatArg(0, 10)).executes(ctx -> {
            Configs.VOLUME_FACTOR = FloatArgumentType.getFloat(ctx, "volume");
            McediaClient.SaveConfig();
            Utils.msgToPlayer("已设置音量系数:" + Configs.VOLUME_FACTOR);

            return 1;
        }));
        LiteralArgumentBuilder<FabricClientCommandSource> loadInfoNode = literal("load_info").then(literal("toggle").executes(ctx -> {
            Configs.SHOW_LOAD_INFO = !Configs.SHOW_LOAD_INFO;

            McediaClient.SaveConfig();

            if (Configs.SHOW_LOAD_INFO) {
                Utils.msgToPlayer("启用加载信息提示");
            } else {
                Utils.msgToPlayer("关闭加载信息提示");
            }
            return 1;
        }));

        LiteralArgumentBuilder<FabricClientCommandSource> pyhsicsNode = literal("pyhsics").then(literal("toggle").executes(ctx -> {
            Configs.PHYSICS = !Configs.PHYSICS;

            McediaClient.SaveConfig();

            if (Configs.PHYSICS) {
                Utils.msgToPlayer("启用物理声效 (需安装物理声效mod，且极大的增加开销)");
            } else {
                Utils.msgToPlayer("关闭物理声效");
            }
            return 1;
        }));
        var optionNode = literal("option");

        optionNode.then(qualityNode);
        optionNode.then(maxCountNode);
        optionNode.then(maxNonLowOverheadCountNode);
        optionNode.then(directLinkNode);
        optionNode.then(yhdmNode);
        optionNode.then(volumeNode);
        optionNode.then(loadInfoNode);
        optionNode.then(pyhsicsNode);

        dispatcher.register(literal("mcedia").then(optionNode));
    }
}
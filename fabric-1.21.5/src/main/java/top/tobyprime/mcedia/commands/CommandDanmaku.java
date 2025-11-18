package top.tobyprime.mcedia.commands;// CommandLogin.java

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.Utils;
import top.tobyprime.mcedia.client.McediaClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandDanmaku {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> danmakuNode = literal("danmaku")
                .then(literal("toggle")
                        .executes(context -> {
                            Configs.DANMAKU_VISIBLE = !Configs.DANMAKU_VISIBLE;
                            if (Configs.DANMAKU_VISIBLE) {
                                Utils.msgToPlayer("已打开弹幕");
                            } else {
                                Utils.msgToPlayer("已关闭弹幕");
                            }
                            McediaClient.SaveConfig();
                            return 1;
                        })).then(literal("opacity").then(argument("opacity", FloatArgumentType.floatArg(0, 1)).executes(ctx -> {
                    Configs.DANMAKU_OPACITY = FloatArgumentType.getFloat(ctx, "opacity");
                    McediaClient.SaveConfig();
                    Utils.msgToPlayer("已设置弹幕不透明度: " + Configs.DANMAKU_OPACITY);
                    return 1;
                }))).then(literal("duration").then(argument("duration", FloatArgumentType.floatArg(0, 10)).executes(ctx -> {
                    Configs.DANMAKU_DURATION = FloatArgumentType.getFloat(ctx, "duration");
                    McediaClient.SaveConfig();

                    Utils.msgToPlayer("已设置弹幕滞留时间: " + Configs.DANMAKU_OPACITY);

                    return 1;
                })));
        dispatcher.register(literal("mcedia").then(danmakuNode));
    }
}
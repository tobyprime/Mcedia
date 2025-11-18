package top.tobyprime.mcedia.commands;// CommandLogin.java

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import top.tobyprime.mcedia.Utils;
import top.tobyprime.mcedia.core.PlayerInstanceManagerRegistry;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandControl {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> controlNode = literal("control").then(literal("pause").executes(ctx -> {
            var targetingPlayer = PlayerInstanceManagerRegistry.getInstance().getTargetingPlayer();
            if (targetingPlayer == null) {
                Utils.msgToPlayer("请先指向一个播放器");
                return 1;
            }

            targetingPlayer.getPlayer().pause();
            Utils.msgToPlayer("已暂停播放");
            return 1;
        })).then(literal("play").executes(ctx -> {
            var targetingPlayer = PlayerInstanceManagerRegistry.getInstance().getTargetingPlayer();
            if (targetingPlayer == null) {
                Utils.msgToPlayer("请先指向一个播放器");
                return 1;
            }

            targetingPlayer.getPlayer().play();
            Utils.msgToPlayer("已开始播放");
            return 1;
        })).then(literal("speed").then(argument("speed", FloatArgumentType.floatArg(0.1f, 10)).executes(ctx -> {
            var speed = FloatArgumentType.getFloat(ctx, "speed");
            var targetingPlayer = PlayerInstanceManagerRegistry.getInstance().getTargetingPlayer();
            if (targetingPlayer == null) {
                Utils.msgToPlayer("请先指向一个播放器");
                return 1;
            }
            targetingPlayer.getPlayer().setSpeed(speed);
            Utils.msgToPlayer("已设置速度为: " + speed);
            return 1;
        })));

        dispatcher.register(literal("mcedia").then(controlNode));
    }
}
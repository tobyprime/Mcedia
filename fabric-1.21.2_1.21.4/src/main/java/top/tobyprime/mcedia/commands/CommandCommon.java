package top.tobyprime.mcedia.commands;// CommandLogin.java

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.client.McediaClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandCommon {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> qualityNode = literal("quality").then(literal("8k").executes(context -> {
            Configs.QUALITY = 3;
            McediaClient.SaveConfig();

            return 1;
        })).then(literal("4k").executes(context -> {
            Configs.QUALITY = 2;
            McediaClient.SaveConfig();

            return 1;
        })).then(literal("common").executes(context -> {
            Configs.QUALITY = 1;
            McediaClient.SaveConfig();

            return 1;
        })).then(literal("min").executes(context -> {
            Configs.QUALITY = 0;
            McediaClient.SaveConfig();
            return 1;
        }));

        LiteralArgumentBuilder<FabricClientCommandSource> maxCountNode = literal("max").then(argument("count", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
            Configs.MAX_PLAYER_COUNT = IntegerArgumentType.getInteger(ctx, "count");
            McediaClient.SaveConfig();
            return 1;
        }));

        dispatcher.register(literal("mcedia").then(qualityNode));
        dispatcher.register(literal("mcedia").then(maxCountNode));
    }
}
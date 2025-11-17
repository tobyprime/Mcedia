package top.tobyprime.mcedia.commands;// CommandLogin.java

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import top.tobyprime.mcedia.Configs;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandQuality {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> qualityNode = literal("quality").then(literal("8k").executes(context -> {
            Configs.QUALITY = 3;
            return 1;
        })).then(literal("4k").executes(context -> {
            Configs.QUALITY = 2;
            return 1;
        })).then(literal("common").executes(context -> {
            Configs.QUALITY = 1;
            return 1;
        })).then(literal("min").executes(context -> {
            Configs.QUALITY = 0;
            return 1;
        }));
        dispatcher.register(literal("mcedia").then(qualityNode));
    }
}
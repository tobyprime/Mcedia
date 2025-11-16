package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.PlayerAgent;

import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandPlaylist {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("mcedia")
                .then(literal("playlist")
                        .then(literal("add")
                                .then(argument("url", StringArgumentType.greedyString())
                                        .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                            agent.commandPlaylistAdd(StringArgumentType.getString(ctx, "url"));
                                        }))
                                )
                        )
                        .then(literal("insert")
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .then(argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                                    agent.commandPlaylistInsert(
                                                            IntegerArgumentType.getInteger(ctx, "index"),
                                                            StringArgumentType.getString(ctx, "url")
                                                    );
                                                }))
                                        )
                                )
                        )
                        .then(literal("remove")
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                            agent.commandPlaylistRemove(IntegerArgumentType.getInteger(ctx, "index"));
                                        }))
                                )
                        )
                        .then(literal("clear")
                                .executes(ctx -> executeOnTargetedAgent(ctx, PlayerAgent::commandPlaylistClear))
                        )
                        .then(literal("list")
                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                    ctx.getSource().sendFeedback(agent.commandPlaylistList(1));
                                }))
                                .then(argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                            ctx.getSource().sendFeedback(agent.commandPlaylistList(
                                                    IntegerArgumentType.getInteger(ctx, "page")
                                            ));
                                        }))
                                )
                        )
                )
        );
    }

    private static int executeOnTargetedAgent(CommandContext<FabricClientCommandSource> context, Consumer<PlayerAgent> action) {
        Minecraft client = context.getSource().getClient();
        HitResult hitResult = client.hitResult;

        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity targetEntity = ((EntityHitResult) hitResult).getEntity();
            if (targetEntity instanceof ArmorStand) {
                PlayerAgent agent = Mcedia.getInstance().getEntityToPlayerMap().get(targetEntity);
                if (agent != null) {
                    action.accept(agent);
                    return 1;
                } else {
                    context.getSource().sendError(Component.literal("§c你看着的盔甲架不是一个有效的 Mcedia 播放器。"));
                    return 0;
                }
            }
        }

        context.getSource().sendError(Component.literal("§c请将你的准星对准一个 Mcedia 播放器盔甲架。"));
        return 0;
    }
}
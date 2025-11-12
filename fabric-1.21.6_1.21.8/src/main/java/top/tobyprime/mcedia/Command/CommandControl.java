package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.PlayerAgent;

import java.util.List;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandControl {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("mcedia")
                .then(literal("init")
                        .executes(ctx -> {
                            Minecraft client = ctx.getSource().getClient();
                            HitResult hitResult = client.hitResult;
                            if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                                Entity targetEntity = ((EntityHitResult) hitResult).getEntity();
                                if (targetEntity instanceof ArmorStand armorStand) {
                                    if (Mcedia.getInstance().getEntityToPlayerMap().containsKey(armorStand)) {
                                        ctx.getSource().sendError(Component.literal("§e这个盔甲架已经是 Mcedia 播放器了。"));
                                        return 0;
                                    }
                                    if (!armorStand.hasCustomName() || !armorStand.getName().getString().contains("mcedia")) {
                                        armorStand.setCustomName(Component.literal("mcedia_player_" + armorStand.getId()));
                                    }
                                    Mcedia.getInstance().HandleMcdiaPlayerEntity(armorStand);
                                    ctx.getSource().sendFeedback(Component.literal("§a成功将你看着的盔甲架初始化为 Mcedia 播放器。"));
                                    return 1;
                                }
                            }
                            ctx.getSource().sendError(Component.literal("§c请将你的准星对准一个普通的盔甲架。"));
                            return 0;
                        })
                )
                .then(literal("control")
                        .then(literal("pause").executes(ctx -> executeOnTargetedAgent(ctx, PlayerAgent::commandPause)))
                        .then(literal("resume").executes(ctx -> executeOnTargetedAgent(ctx, PlayerAgent::commandResume)))
                        .then(literal("stop").executes(ctx -> executeOnTargetedAgent(ctx, PlayerAgent::commandStop)))
                        .then(literal("skip").executes(ctx -> executeOnTargetedAgent(ctx, PlayerAgent::commandSkip)))
                        .then(literal("seek")
                                .then(argument("time", StringArgumentType.string())
                                        .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                            String timeStr = StringArgumentType.getString(ctx, "time");
                                            try {
                                                agent.commandSeek(PlayerAgent.parseToMicros(timeStr));
                                            } catch (Exception e) {
                                                ctx.getSource().sendError(Component.literal("§c时间格式错误，请使用 'MM:SS' 或 'HH:MM:SS'。"));
                                            }
                                        }))
                                )
                        )
                        .then(literal("set")
                                .then(literal("volume")
                                        .then(argument("percent", FloatArgumentType.floatArg(0, 100))
                                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                                    agent.commandSetVolume(FloatArgumentType.getFloat(ctx, "percent"));
                                                })))
                                )
                                .then(literal("url")
                                        .then(argument("link", StringArgumentType.greedyString())
                                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                                    agent.commandSetUrl(StringArgumentType.getString(ctx, "link"));
                                                }))
                                        )
                                )
                                .then(literal("loop")
                                        .then(argument("enabled", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("true");
                                                    builder.suggest("false");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                                    String boolStr = StringArgumentType.getString(ctx, "enabled");
                                                    agent.commandSetLooping(Boolean.parseBoolean(boolStr));
                                                }))
                                        )
                                )
                                .then(literal("screen")
                                        .then(literal("offset")
                                                .then(argument("x", FloatArgumentType.floatArg())
                                                        .then(argument("y", FloatArgumentType.floatArg())
                                                                .then(argument("z", FloatArgumentType.floatArg())
                                                                        .then(argument("scale", FloatArgumentType.floatArg(0.1f)) // 最小缩放0.1
                                                                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                                                                    agent.commandSetOffset(
                                                                                            FloatArgumentType.getFloat(ctx, "x"),
                                                                                            FloatArgumentType.getFloat(ctx, "y"),
                                                                                            FloatArgumentType.getFloat(ctx, "z"),
                                                                                            FloatArgumentType.getFloat(ctx, "scale")
                                                                                    );
                                                                                }))
                                                                        )
                                                                )
                                                        )
                                                ))
                                )
                                .then(literal("audio")
                                        .then(literal("primary")
                                                .then(argument("x", FloatArgumentType.floatArg())
                                                        .then(argument("y", FloatArgumentType.floatArg())
                                                                .then(argument("z", FloatArgumentType.floatArg())
                                                                        .then(argument("maxVol", FloatArgumentType.floatArg(0f))
                                                                                .then(argument("minRange", FloatArgumentType.floatArg(0f))
                                                                                        .then(argument("maxRange", FloatArgumentType.floatArg(0.1f))
                                                                                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                                                                                    agent.commandSetAudioPrimary(
                                                                                                            FloatArgumentType.getFloat(ctx, "x"),
                                                                                                            FloatArgumentType.getFloat(ctx, "y"),
                                                                                                            FloatArgumentType.getFloat(ctx, "z"),
                                                                                                            FloatArgumentType.getFloat(ctx, "maxVol"),
                                                                                                            FloatArgumentType.getFloat(ctx, "minRange"),
                                                                                                            FloatArgumentType.getFloat(ctx, "maxRange")
                                                                                                    );
                                                                                                }))
                                                                                        ))))))
                                        )
                                        .then(literal("secondary")
                                                .then(argument("x", FloatArgumentType.floatArg())
                                                        .then(argument("y", FloatArgumentType.floatArg())
                                                                .then(argument("z", FloatArgumentType.floatArg())
                                                                        .then(argument("maxVol", FloatArgumentType.floatArg(0f))
                                                                                .then(argument("minRange", FloatArgumentType.floatArg(0f))
                                                                                        .then(argument("maxRange", FloatArgumentType.floatArg(0.1f))
                                                                                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                                                                                    agent.commandSetAudioSecondary(
                                                                                                            FloatArgumentType.getFloat(ctx, "x"),
                                                                                                            FloatArgumentType.getFloat(ctx, "y"),
                                                                                                            FloatArgumentType.getFloat(ctx, "z"),
                                                                                                            FloatArgumentType.getFloat(ctx, "maxVol"),
                                                                                                            FloatArgumentType.getFloat(ctx, "minRange"),
                                                                                                            FloatArgumentType.getFloat(ctx, "maxRange")
                                                                                                    );
                                                                                                }))
                                                                                        ))))))
                                        )
                                        .then(literal("secondary_enabled")
                                                .executes(ctx -> executeOnTargetedAgent(ctx, agent -> {
                                                    boolean isActive = agent.isSecondaryAudioActive();
                                                    if (isActive) {
                                                        Mcedia.msgToPlayer("§a[Mcedia] §f副声源当前为 [启用] 状态。");
                                                    } else {
                                                        Mcedia.msgToPlayer("§e[Mcedia] §f副声源当前为 [禁用] 状态。");
                                                    }
                                                }))
                                                .then(literal("toggle")
                                                        .executes(ctx -> executeOnTargetedAgent(ctx, PlayerAgent::commandToggleAudioSecondary))
                                                )
                                        )
                                ))));
    }

    /**
     * 辅助方法，用于获取玩家视线所指的 Mcedia 盔甲架并对其执行操作
     */
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
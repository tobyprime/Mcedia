package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import top.tobyprime.mcedia.McediaConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandConfig {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> configNode = literal("config")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal("§6--- /mcedia config 用法 ---"));
                    ctx.getSource().sendFeedback(Component.literal("§e/mcedia config get <参数名> §7- 获取参数当前值。"));
                    ctx.getSource().sendFeedback(Component.literal("§e/mcedia config set <参数名> <值> §7- 设置参数值。"));
                    ctx.getSource().sendFeedback(Component.literal("§e/mcedia config save §7- 保存当前配置到文件。"));
                    ctx.getSource().sendFeedback(Component.literal("§e/mcedia config load §7- 从文件重载配置。"));
                    return 1;
                })
                        // /mcedia config save
                        .then(literal("save")
                                .executes(ctx -> {
                                    McediaConfig.save();
                                    ctx.getSource().sendFeedback(Component.literal("§a[Mcedia] §f配置已保存到文件。"));
                                    return 1;
                                })
                        )
                        // /mcedia config load
                        .then(literal("load")
                                .executes(ctx -> {
                                    McediaConfig.load();
                                    ctx.getSource().sendFeedback(Component.literal("§a[Mcedia] §f配置已从文件重载。"));
                                    return 1;
                                })
                        );

        LiteralArgumentBuilder<FabricClientCommandSource> setNode = literal("set")
                .executes(ctx -> {
                    ctx.getSource().sendError(Component.literal("§c用法: /mcedia config set <参数名> <值>"));
                    return 0;
                });

        LiteralArgumentBuilder<FabricClientCommandSource> getNode = literal("get")
                .executes(ctx -> {
                    ctx.getSource().sendError(Component.literal("§c用法: /mcedia config get <参数名>"));
                    return 0;
                });


        for (Field field : McediaConfig.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();

                ArgumentType<?> argType = null;
                if (fieldType == boolean.class) argType = BoolArgumentType.bool();
                else if (fieldType == int.class) argType = IntegerArgumentType.integer();
                else if (fieldType == long.class) argType = LongArgumentType.longArg();
                else if (fieldType == double.class) argType = DoubleArgumentType.doubleArg();

                if (argType != null) {
                    // 构建 set 子命令: set <fieldName> <value>
                    setNode.then(literal(fieldName)
                            .then(argument("value", argType)
                                    .executes(ctx -> {
                                        try {
                                            Object value = ctx.getArgument("value", fieldType);
                                            field.set(null, value);
                                            McediaConfig.save();
                                            ctx.getSource().sendFeedback(Component.literal(String.format("§a[Mcedia] §f参数 §e%s §f已更新为 §b%s", fieldName, value)));
                                            return 1;
                                        } catch (Exception e) {
                                            ctx.getSource().sendError(Component.literal("§c无法设置参数: " + e.getMessage()));
                                            return 0;
                                        }
                                    })
                            )
                    );
                    // 构建 get 子命令: get <fieldName>
                    getNode.then(literal(fieldName)
                            .executes(ctx -> {
                                try {
                                    Object value = field.get(null);
                                    ctx.getSource().sendFeedback(Component.literal(String.format("§a[Mcedia] §f参数 §e%s §f当前值为: §b%s", fieldName, value)));
                                    return 1;
                                } catch (Exception e) {
                                    ctx.getSource().sendError(Component.literal("§c无法获取参数值: " + e.getMessage()));
                                    return 0;
                                }
                            })
                    );
                }
            }
        }

        dispatcher.register(literal("mcedia")
                .then(literal("config")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal("§6--- /mcedia config 用法 ---"));
                            ctx.getSource().sendFeedback(Component.literal("§e/mcedia config get <参数名> §7- 获取参数当前值。"));
                            ctx.getSource().sendFeedback(Component.literal("§e/mcedia config set <参数名> <值> §7- 设置参数值。"));
                            ctx.getSource().sendFeedback(Component.literal("§e/mcedia config save §7- 保存当前配置到文件。"));
                            ctx.getSource().sendFeedback(Component.literal("§e/mcedia config load §7- 从文件重载配置。"));
                            return 1;
                        })
                        .then(literal("save").executes(ctx -> {
                            McediaConfig.save();
                            ctx.getSource().sendFeedback(Component.literal("§a[Mcedia] §f配置已保存到文件。"));
                            return 1;
                        }))
                        .then(literal("load").executes(ctx -> {
                            McediaConfig.load();
                            ctx.getSource().sendFeedback(Component.literal("§a[Mcedia] §f配置已从文件重载。"));
                            return 1;
                        }))
                        .then(setNode)
                        .then(getNode)
                )
        );
    }
}
package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.PlayerAgent;
import top.tobyprime.mcedia.PresetData;
import top.tobyprime.mcedia.manager.PresetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.minecraft.client.gui.screens.recipebook.RecipeBookPage.ITEMS_PER_PAGE;

public class CommandPreset {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("mcedia")
                .then(literal("preset")
                        .then(literal("reload")
                                .executes(ctx -> {
                                    PresetManager.getInstance().reloadPresets();
                                    ctx.getSource().sendFeedback(Component.literal("§a[Mcedia] §f预设已从文件重新加载。"));
                                    return 1;
                                })
                        )
                        .then(literal("save")
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            return executeOnTargetedAgent(ctx, agent -> {
                                                PresetManager.getInstance().savePreset(name, agent.getPresetData());
                                                ctx.getSource().sendFeedback(Component.literal("§a[Mcedia] §f预设 '§e" + name + "§f' 已保存。"));
                                            });
                                        })
                                )
                        )
                        .then(literal("load")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            PresetManager.getInstance().getAllPresets().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            PresetData data = PresetManager.getInstance().getPreset(name);
                                            if (data == null) {
                                                ctx.getSource().sendError(Component.literal("§c预设 '§e" + name + "§c' 不存在。"));
                                                return 0;
                                            }
                                            return executeOnTargetedAgent(ctx, agent -> {
                                                agent.applyPreset(data);
                                                ctx.getSource().sendFeedback(Component.literal("§a[Mcedia] §f已应用预设 '§e" + name + "§f'。"));
                                            });
                                        })
                                )
                        )
                        .then(literal("delete")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            PresetManager.getInstance().getAllPresets().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            if (PresetManager.getInstance().deletePreset(name)) {
                                                ctx.getSource().sendFeedback(Component.literal("§a[Mcedia] §f预设 '§e" + name + "§f' 已删除。"));
                                            } else {
                                                ctx.getSource().sendError(Component.literal("§c预设 '§e" + name + "§c' 不存在。"));
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("list")
                                .executes(ctx -> listPresets(ctx.getSource(), 1))
                                .then(argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> listPresets(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))
                                )
                        )
                )
        );
    }

    private static int listPresets(FabricClientCommandSource source, int page) {
        Map<String, PresetData> presets = PresetManager.getInstance().getAllPresets();
        if (presets.isEmpty()) {
            source.sendFeedback(Component.literal("§e[Mcedia] §f当前没有已保存的预设。"));
            return 1;
        }
        List<String> names = new ArrayList<>(presets.keySet());
        int totalPages = (int) Math.ceil((double) names.size() / ITEMS_PER_PAGE);
        if (page > totalPages) {
            page = totalPages;
        }
        source.sendFeedback(Component.literal("§6--- Mcedia 预设列表 (第 " + page + " / " + totalPages + " 页) ---"));
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < names.size(); i++) {
            String name = names.get(startIndex + i);
            PresetData data = presets.get(name);
            MutableComponent hoverText = Component.literal("§b屏幕偏移: §f" + String.format("%.2f, %.2f, %.2f\n", data.screenX, data.screenY, data.screenZ));
            hoverText.append("§b屏幕缩放: §f" + String.format("%.2f\n", data.screenScale));
            hoverText.append("§a主声源: §f" + String.format("%.1f, %.1f, %.1f §7(Vol:%.1f, Range:%.1f-%.1f)\n", data.primaryAudio.x, data.primaryAudio.y, data.primaryAudio.z, data.primaryAudio.maxVol, data.primaryAudio.minRange, data.primaryAudio.maxRange));
            if (data.secondaryAudioEnabled) {
                hoverText.append("§a副声源 (启用): §f" + String.format("%.1f, %.1f, %.1f §7(Vol:%.1f, Range:%.1f-%.1f)", data.secondaryAudio.x, data.secondaryAudio.y, data.secondaryAudio.z, data.secondaryAudio.maxVol, data.secondaryAudio.minRange, data.secondaryAudio.maxRange));
            } else {
                hoverText.append("§8副声源 (禁用)");
            }
            String filePath = PresetManager.getInstance().getPresetFile().toAbsolutePath().toString();
            ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.OPEN_FILE, filePath);
            HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT,hoverText);
            MutableComponent line = Component.literal("§7- §e" + name)
                    .setStyle(Style.EMPTY
                            .withHoverEvent(hoverEvent)
                            .withClickEvent(clickEvent));

            source.sendFeedback(line);
        }
        return 1;
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
package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import top.tobyprime.mcedia.manager.PipManager;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandPip {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("mcedia")
                .then(literal("pip")
                        .then(literal("open")
                                .then(argument("url", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String url = StringArgumentType.getString(ctx, "url");
                                            PipManager.getInstance().open(url);
                                            ctx.getSource().sendFeedback(Component.literal("§a[PiP] 正在开启画中画..."));
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("close")
                                .executes(ctx -> {
                                    PipManager.getInstance().close();
                                    ctx.getSource().sendFeedback(Component.literal("§e[PiP] 画中画已关闭。"));
                                    return 1;
                                })
                        )
                        .then(literal("toggle_pause")
                                .executes(ctx -> {
                                    PipManager.getInstance().togglePause();
                                    ctx.getSource().sendFeedback(Component.literal("§f[PiP] 已切换播放/暂停状态。"));
                                    return 1;
                                })
                        )
                        .then(literal("config")
                                .then(literal("pos")
                                        .then(argument("x", IntegerArgumentType.integer())
                                                .then(argument("y", IntegerArgumentType.integer())
                                                        .executes(ctx -> {
                                                            PipManager.getInstance().x = IntegerArgumentType.getInteger(ctx, "x");
                                                            PipManager.getInstance().y = IntegerArgumentType.getInteger(ctx, "y");
                                                            ctx.getSource().sendFeedback(Component.literal(String.format("§f[PiP] 位置已设置为 (%d, %d)。", PipManager.getInstance().x, PipManager.getInstance().y)));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                                .then(literal("size")
                                        .then(argument("width", IntegerArgumentType.integer(1))
                                                .then(argument("height", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> {
                                                            PipManager.getInstance().width = IntegerArgumentType.getInteger(ctx, "width");
                                                            PipManager.getInstance().height = IntegerArgumentType.getInteger(ctx, "height");
                                                            ctx.getSource().sendFeedback(Component.literal(String.format("§f[PiP] 尺寸已设置为 %d x %d。", PipManager.getInstance().width, PipManager.getInstance().height)));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                                .then(literal("opacity")
                                        .then(argument("percent", FloatArgumentType.floatArg(0, 100))
                                                .executes(ctx -> {
                                                    PipManager.getInstance().opacity = FloatArgumentType.getFloat(ctx, "percent") / 100.0f;
                                                    ctx.getSource().sendFeedback(Component.literal(String.format("§f[PiP] 不透明度已设置为 %.0f%%。", PipManager.getInstance().opacity * 100)));
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }
}
package top.tobyprime.mcedia.Command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.PlayerAgent;
import top.tobyprime.mcedia.core.Media;
import top.tobyprime.mcedia.util.ScreenInteractionHelper;

import java.util.Comparator;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandDebug {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("mcedia")
                .then(literal("debug_target")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;

                            Vec3 pos = mc.player.position();

                            // 寻找半径 10 格内最近的 Mcedia 播放器实体
                            List<ArmorStand> candidates = mc.player.level().getEntitiesOfClass(ArmorStand.class,
                                    new AABB(pos.x - 10, pos.y - 10, pos.z - 10, pos.x + 10, pos.y + 10, pos.z + 10),
                                    entity -> Mcedia.getInstance().getEntityToPlayerMap().containsKey(entity)
                            );

                            ArmorStand target = candidates.stream()
                                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)))
                                    .orElse(null);

                            if (target == null) {
                                ctx.getSource().sendError(Component.literal("§c附近 10 格内没有找到 Mcedia 播放器！"));
                                return 0;
                            }

                            PlayerAgent agent = Mcedia.getInstance().getEntityToPlayerMap().get(target);
                            ctx.getSource().sendFeedback(Component.literal("§6--- Debugging Nearest Player ---"));
                            ctx.getSource().sendFeedback(Component.literal("§7Target UUID: " + target.getUUID()));

                            // 1. 媒体信息
                            Media media = agent.getPlayer().getMedia();
                            float ratio = 1.777f;
                            if (media != null) {
                                ratio = media.getAspectRatio();
                                ctx.getSource().sendFeedback(Component.literal("§eMedia: §aPlaying §7(Live=" + media.isLiveStream() + ", W=" + media.getWidth() + ", H=" + media.getHeight() + ")"));
                            } else {
                                ctx.getSource().sendFeedback(Component.literal("§eMedia: §cNull"));
                            }

                            // 2. 强制运行射线检测
                            String raycastResult = ScreenInteractionHelper.debugRaycast(target, agent.getConfigManager(), ratio);
                            ctx.getSource().sendFeedback(Component.literal("§eRaycast: §f" + raycastResult));

                            // 3. 实体与配置
                            ctx.getSource().sendFeedback(Component.literal("§eEntity: §7Small=" + target.isSmall() + ", Rot=" + String.format("%.1f", target.getYRot())));
                            ctx.getSource().sendFeedback(Component.literal("§eConfig: §7Offset=(" +
                                    agent.getConfigManager().offsetX + ", " +
                                    agent.getConfigManager().offsetY + ", " +
                                    agent.getConfigManager().offsetZ + "), Scale=" + agent.getConfigManager().scale));

                            return 1;
                        })
                )
        );
    }
}
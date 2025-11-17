package top.tobyprime.mcedia.commands;// CommandLogin.java

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.Utils;
import top.tobyprime.mcedia.bilibili.BilibiliAuthManager;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CommandDanmaku {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> danmakuNode = literal("danmaku")
                .then(literal("toggle")
                        .executes(context -> {
                            Configs.DANMAKU_VISIBLE = !Configs.DANMAKU_VISIBLE;
                            if (Configs.DANMAKU_VISIBLE){
                                Utils.msgToPlayer("已打开弹幕");
                            }else {
                                Utils.msgToPlayer("已关闭弹幕");
                            }
                            return 1;
                        })
                );
        dispatcher.register(literal("mcedia").then(danmakuNode));
    }
}
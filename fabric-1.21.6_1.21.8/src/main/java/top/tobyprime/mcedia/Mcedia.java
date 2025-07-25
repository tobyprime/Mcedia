package top.tobyprime.mcedia;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.mixin_bridge.ISoundEngineBridge;
import top.tobyprime.mcedia.mixin_bridge.ISoundManagerBridge;

import java.util.concurrent.ConcurrentHashMap;

public class Mcedia implements ModInitializer {
    private static final int MAX_PLAYER_COUNT = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger(Mcedia.class);

    private static Mcedia INSTANCE;
    private final ConcurrentHashMap<Entity, PlayerAgent> entityToPlayer = new ConcurrentHashMap<>();

    public static Mcedia getInstance() {
        return INSTANCE;
    }


    public ConcurrentHashMap<Entity, PlayerAgent> getEntityToPlayerMap() {
        return entityToPlayer;
    }


    public SoundEngineExecutor getAudioExecutor() {
        var manager = (ISoundManagerBridge) Minecraft.getInstance().getSoundManager();
        var engine = (ISoundEngineBridge) manager.mcdia$getSoundManager();
        return engine.mcdia$getExecutor();
    }

    @Override
    public void onInitialize() {
        INSTANCE = this;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (var pair : entityToPlayer.entrySet()) {
                if (pair.getKey().isRemoved()) {
                    pair.getValue().close();
                    entityToPlayer.remove(pair.getKey());
                    return;
                }
                pair.getValue().tick();
            }
        });
    }

    public void HandleMcdiaPlayerEntity(ArmorStand entity) {
        boolean isMcdiaPlayer = entity.getName().toString().contains("mcdia_player") || entity.getName().toString().contains("mcedia_player");
        if (!entityToPlayer.containsKey(entity) && isMcdiaPlayer) {
            if (getEntityToPlayerMap().size() >= MAX_PLAYER_COUNT) {
                return;
            }
            getEntityToPlayerMap().put(entity, new PlayerAgent(entity));
        }
    }

    public static void msgToPlayer(String msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(msg), false);
        }
    }
}

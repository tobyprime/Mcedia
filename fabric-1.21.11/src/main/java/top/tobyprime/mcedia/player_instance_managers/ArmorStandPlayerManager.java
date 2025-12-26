package top.tobyprime.mcedia.player_instance_managers;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.McediaPlayerStatus;
import top.tobyprime.mcedia.core.PlayerInstanceManagerRegistry;
import top.tobyprime.mcedia.interfaces.IMediaPlayerInstance;
import top.tobyprime.mcedia.interfaces.IPlayerInstanceManager;
import top.tobyprime.mcedia.player_instances.ArmorStandPlayerAgentWrapper;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import static top.tobyprime.mcedia.Configs.MAX_PLAYER_COUNT;

public class ArmorStandPlayerManager implements IPlayerInstanceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArmorStandPlayerManager.class);
    private static final ArmorStandPlayerManager INSTANCE = new ArmorStandPlayerManager();
    private final ConcurrentHashMap<ArmorStand, ArmorStandPlayerAgentWrapper> entityToPlayer = new ConcurrentHashMap<>();

    public static ArmorStandPlayerManager getInstance() {
        return INSTANCE;
    }


    private void addPlayer(ArmorStand entity) {
        if (McediaPlayerStatus.players.get() >= MAX_PLAYER_COUNT) {
            return;
        }
        entityToPlayer.put(entity, new ArmorStandPlayerAgentWrapper(entity));
        McediaPlayerStatus.players.addAndGet(1);
        LOGGER.info("新增了位于 {} 的播放器", entity.position());

    }

    public void removePlayer(ArmorStand entity) {
        var entry = entityToPlayer.remove(entity);
        if (entry != null) {
            entry.close();
            McediaPlayerStatus.players.addAndGet(-1);
            LOGGER.info("移除了位于 {} 的播放器", entity.position());
        }
    }

    private void clearMap() {
        for (var key : entityToPlayer.keySet()) {
            removePlayer(key);
        }
    }

    public void onInitialize() {
        PlayerInstanceManagerRegistry.getInstance().register(this);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (var pair : entityToPlayer.entrySet()) {
                if (pair.getKey().isRemoved()) {
                    removePlayer(pair.getKey());
                    continue;
                }
                pair.getValue().tick();
            }
        });

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((mc, level) -> clearMap());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearMap());
    }

    public void onArmorStandTick(ArmorStand entity) {
        // 确保只在客户端处理
        if (!entity.level().isClientSide()) {
            return;
        }

        boolean isMcediaPlayer = false;
        var name = entity.getName().toString();
        for (var pat : Configs.ARMOR_STAND_PLAYER_NAME_PATTERNS) {
            if (name.contains(pat)){
                isMcediaPlayer = true;
                break;
            }
        }
        if (!entityToPlayer.containsKey(entity) && isMcediaPlayer) {
            addPlayer(entity);
        }
    }

    @Override
    public Collection<? extends IMediaPlayerInstance> getPlayerInstances() {
        return this.entityToPlayer.values();
    }
}

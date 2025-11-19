package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.Configs;
import top.tobyprime.mcedia.interfaces.IMediaPlayerInstance;
import top.tobyprime.mcedia.interfaces.IPlayerInstanceManager;

import java.util.*;

public class PlayerInstanceManagerRegistry {
    private final static PlayerInstanceManagerRegistry instance = new PlayerInstanceManagerRegistry();
    private final List<IPlayerInstanceManager> managers = new ArrayList<>();

    public static PlayerInstanceManagerRegistry getInstance() {
        return instance;
    }

    public void register(IPlayerInstanceManager manager){
        managers.add(manager);
    }

    public void unregister(IPlayerInstanceManager manager){
        managers.remove(manager);
    }

    public ArrayList<? extends IMediaPlayerInstance> getPlayers() {
        ArrayList<IMediaPlayerInstance> players = new ArrayList<>();

        for (IPlayerInstanceManager manager : managers) {
            players.addAll(manager.getPlayerInstances());
        }

        return players;
    }

    public @Nullable IMediaPlayerInstance getTargetingPlayer(){
        IMediaPlayerInstance targetingPlayer = null;
        float distanceMin = Float.MAX_VALUE;
        for (var player : getPlayers()){
            var distance = player.isTargeting();
            if (distance < 0) { continue; }
            if (distance < distanceMin) { targetingPlayer = player; }
        }
        return targetingPlayer;
    }

    public void update() {
        var players = getPlayers();
        players.sort(Comparator.comparingDouble(IMediaPlayerInstance::getDistance));

        int playerCount = 0;
        int nonLowOverhead = 0;

        for (var player : players) {
            if (player.isRemoved())
                continue;

            if (playerCount >= Configs.MAX_PLAYER_COUNT){
                player.remove();
            }

            playerCount++;

            if (nonLowOverhead < Configs.MAX_NON_LOW_OVERHEAD_PLAYER_COUNT) {
                player.getPlayer().setLowOverhead(false);
                nonLowOverhead++;
                continue;
            }
            player.getPlayer().setLowOverhead(true);
        }
    }
}

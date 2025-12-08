package top.tobyprime.mcedia_paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

public class McediaPlugin extends JavaPlugin implements PluginMessageListener {

    private static final String CHANNEL = "mcedia:sync";

    @Override
    public void onEnable() {
        // 注册传入和传出通道 (虽然我们主要用传入)
        this.getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

        getLogger().info("Mcedia Plugin Enabled! Listening on channel: " + CHANNEL);
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this);
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    /**
     * 处理客户端发来的二进制数据包
     */
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (!channel.equals(CHANNEL)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            // 1. 读取数据 (顺序必须与客户端写入顺序一致)
            // 客户端写: WriteUUID, WriteLong
            long mostSigBits = in.readLong();
            long leastSigBits = in.readLong();
            UUID entityUuid = new UUID(mostSigBits, leastSigBits);

            long timestamp = in.readLong();

            // 2. 在主线程执行逻辑 (Bukkit API 必须在主线程调用)
            Bukkit.getScheduler().runTask(this, () -> handleSync(player, entityUuid, timestamp));

        } catch (IOException e) {
            getLogger().warning("Error parsing packet from " + player.getName());
        }
    }

    private void handleSync(Player player, UUID uuid, long timestamp) {
        // 3. 安全检查
        Entity entity = Bukkit.getEntity(uuid);
        if (!(entity instanceof ArmorStand armorStand)) return;

        // 距离检查 (防全图挂)
        if (!player.getWorld().equals(armorStand.getWorld()) ||
                player.getLocation().distanceSquared(armorStand.getLocation()) > 400) {
            return;
        }

        // 验证是否为 Mcedia 实体
        String customName = armorStand.getCustomName();
        if (customName == null || (!customName.contains("mcedia") && !customName.contains("mcdia"))) {
            return;
        }

        // 4. 执行同步 (修改书名)
        ItemStack handItem = armorStand.getEquipment().getItemInMainHand();
        if (handItem.getType().isAir()) return;

        ItemMeta meta = handItem.getItemMeta();
        if (meta != null) {
            String syncString = player.getName() + ":" + timestamp;

            meta.displayName(Component.text(syncString));
            handItem.setItemMeta(meta);

            // getLogger().info("Sync updated: " + syncString);
        }
    }
}
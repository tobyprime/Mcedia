package top.tobyprime.mcedia_paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class McediaPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }


    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand armorStand = event.getRightClicked();

        // 检查盔甲架名称，确保是我们的播放器
        if (!armorStand.getName().contains("mcedia")) {
            return;
        }

        // 只处理对主手的操作
        if (event.getSlot() != EquipmentSlot.HAND) {
            return;
        }

        // 阻止玩家直接拿走盔甲架上的书
        event.setCancelled(true);

        ItemStack newItem = event.getPlayerItem(); // 玩家手上的物品

        // 确保玩家手上拿的是书
        if (newItem.getType().isAir()) {
            armorStand.getEquipment().setItemInMainHand(null);
            return;
        }

        // 确保玩家手上拿的是书
        if (!newItem.getType().name().contains("BOOK")) {
            return;
        }

        // 克隆玩家手上的书，以防修改影响到玩家背包里的原件
        ItemStack bookToPlace = newItem.clone();
        bookToPlace.setAmount(1); // 确保只放置一本

        BookMeta newBookMeta = (BookMeta) bookToPlace.getItemMeta();
        if (newBookMeta == null) return;

        if (!newBookMeta.hasPages()) {
           return;
        }

        // 修改物品的显示名称，格式为 “玩家名:时间戳”
        newBookMeta.displayName(Component.text(event.getPlayer().getName() + ":" + System.currentTimeMillis()));
        // 把修改后的元数据应用回物品
        bookToPlace.setItemMeta(newBookMeta);

        // 将处理过的新书放置到盔甲架上
        armorStand.getEquipment().setItemInMainHand(bookToPlace);
        getLogger().info("播放器 " + armorStand.getName() + " 的内容已更新。");
    }
}
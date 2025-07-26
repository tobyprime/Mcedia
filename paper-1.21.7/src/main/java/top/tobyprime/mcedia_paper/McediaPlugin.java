package top.tobyprime.mcedia_paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class McediaPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand armorStand = event.getRightClicked();
        if ((!armorStand.getName().contains("mcedia")) && (!armorStand.getName().contains("mcdia"))) return;

        if (event.getSlot() == EquipmentSlot.HAND) {
            var newItem = event.getPlayerItem();
            if (newItem.getType().name().contains("BOOK")) {
                ItemMeta meta = newItem.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text(event.getPlayer().getName() + ":"+ System.currentTimeMillis()));
                    newItem.setItemMeta(meta);
                }
            }
            getLogger().info("播放器主手物品已变更");
        }
    }
}

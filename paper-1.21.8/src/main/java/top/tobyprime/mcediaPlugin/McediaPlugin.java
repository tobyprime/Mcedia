package top.tobyprime.mcediaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class McediaPlugin extends JavaPlugin implements Listener {

    // 正则表达式，用于匹配URL
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://\\S+)");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand armorStand = event.getRightClicked();

        // 检查盔甲架名称，确保是我们的播放器
        if (!armorStand.getName().contains("mcedia") && !armorStand.getName().contains("mcdia")) {
            return;
        }

        // 只处理对主手的操作
        if (event.getSlot() != EquipmentSlot.HAND) {
            return;
        }

        // 阻止玩家直接拿走盔甲架上的书
        event.setCancelled(true);

        ItemStack newItem = event.getPlayerItem(); // 玩家手上的物品
        ItemStack oldItem = armorStand.getEquipment().getItemInMainHand(); // 盔甲架上的物品

        // 确保玩家手上拿的是书
        if (!newItem.getType().name().contains("BOOK")) {
            return;
        }

        // 克隆玩家手上的书，以防修改影响到玩家背包里的原件
        ItemStack bookToPlace = newItem.clone();
        bookToPlace.setAmount(1); // 确保只放置一本

        BookMeta newBookMeta = (BookMeta) bookToPlace.getItemMeta();
        if (newBookMeta == null) return;

        // [核心逻辑]
        // 检查书的第一页内容
        if (newBookMeta.hasPages()) {
            String firstPage = PlainTextComponentSerializer.plainText().serialize(newBookMeta.page(1));
            String[] lines = firstPage.split("\n");

            // 如果只有一行，并且是URL，那么我们就需要为它添加同步时间
            if (lines.length == 1 && URL_PATTERN.matcher(lines[0].trim()).matches()) {
                long startTime = getStartTimeFromItem(oldItem);

                if (startTime > 0) {
                    long currentTime = System.currentTimeMillis();
                    long durationMillis = currentTime - startTime;

                    // 将毫秒转换为 分:秒 的格式
                    long seconds = (durationMillis / 1000) % 60;
                    long minutes = (durationMillis / (1000 * 60)) % 60;
                    long hours = durationMillis / (1000 * 60 * 60);

                    String timeString = String.format("%d:%02d:%02d", hours, minutes, seconds);

                    // 创建新的页面内容
                    List<Component> newPages = new ArrayList<>();
                    // 第一页：URL 和我们计算出的时间戳
                    newPages.add(Component.text(lines[0] + "\n" + timeString));

                    // 把书本剩下的页面也加回来
                    for (int i = 2; i <= newBookMeta.getPageCount(); i++) {
                        newPages.add(newBookMeta.page(i));
                    }

                    newBookMeta.pages(newPages);
                    getLogger().info("已为视频 " + lines[0] + " 自动同步播放时间: " + timeString);
                }
            }
        }

        // 更新新书的物品名称，存入当前的“起始时间点”
        newBookMeta.displayName(Component.text("Mcedia:" + System.currentTimeMillis()));
        bookToPlace.setItemMeta(newBookMeta);

        // 将处理过的新书放置到盔甲架上
        armorStand.getEquipment().setItemInMainHand(bookToPlace);
        getLogger().info("播放器 " + armorStand.getName() + " 的内容已更新。");
    }

    /**
     * 从物品名称中解析出我们之前存入的时间戳
     * @param item 盔甲架上原有的物品
     * @return 时间戳 (毫秒)，如果解析失败则返回0
     */
    private long getStartTimeFromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            String[] parts = name.split(":");
            if (parts.length == 2 && parts[0].equals("Mcedia")) {
                try {
                    return Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    // 格式错误，忽略
                }
            }
        }
        return 0;
    }
}
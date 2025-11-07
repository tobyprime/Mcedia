// 这是插件的包路径，对应项目的目录结构
package top.tobyprime.mcediaPlugin;

// 引入 Paper API（Bukkit 的高级实现）中使用的类
import net.kyori.adventure.text.Component;  // 用于新版聊天文本（代替旧的 ChatColor）
import org.bukkit.Bukkit;                    // Bukkit 主类，用于注册事件、访问服务器等
import org.bukkit.entity.ArmorStand;         // 盔甲架实体类
import org.bukkit.event.EventHandler;        // 标注事件监听方法
import org.bukkit.event.Listener;            // 表示此类是一个事件监听器
import org.bukkit.event.player.PlayerArmorStandManipulateEvent; // 玩家操作盔甲架时触发的事件
import org.bukkit.inventory.EquipmentSlot;   // 玩家持有物品的槽位枚举（主手、副手、头盔等）
import org.bukkit.inventory.meta.ItemMeta;   // 物品元数据，用于修改物品名称、Lore 等
import org.bukkit.plugin.java.JavaPlugin;    // 所有 Bukkit 插件必须继承的基类

// 插件主类，必须继承 JavaPlugin 并在 plugin.yml 中声明
// 同时实现 Listener 接口，表示这个类能监听事件
public class McediaPlugin extends JavaPlugin implements Listener {

    // 插件启用时执行（/reload 或服务器启动）
    @Override
    public void onEnable() {
        // 向 Bukkit 注册这个类作为事件监听器
        // 第一个参数是监听器实例，第二个参数是插件本身
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    // 当玩家尝试与盔甲架交互（例如取下或放置装备）时触发
    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {

        // 获取被交互的盔甲架对象
        ArmorStand armorStand = event.getRightClicked();

        // 检查这个盔甲架的名字中是否包含 "mcedia" 或 "mcdia"
        // 如果不是我们自定义的盔甲架，就直接返回（不做任何操作）
        if ((!armorStand.getName().contains("mcedia")) && (!armorStand.getName().contains("mcdia")))
            return;

        // 如果交互的是盔甲架的主手（即玩家右键它手上的物品）
        if (event.getSlot() == EquipmentSlot.HAND) {

            // 获取玩家当前使用的物品（右键用的）
            var newItem = event.getPlayerItem();

            // 检查物品类型名中是否包含 "BOOK"（即任意书类物品）
            if (newItem.getType().name().contains("BOOK")) {

                // 取得物品的元数据
                ItemMeta meta = newItem.getItemMeta();
                if (meta != null) {
                    // 修改物品的显示名称，格式为 “玩家名:时间戳”
                    meta.displayName(Component.text(event.getPlayer().getName() + ":" + System.currentTimeMillis()));
                    // 把修改后的元数据应用回物品
                    newItem.setItemMeta(meta);
                }
            }

            // 向控制台输出日志信息，方便调试
            getLogger().info("播放器主手物品已变更");
        }
    }
}

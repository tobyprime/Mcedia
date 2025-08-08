## 使用
### 功能
1. 播放 Bilibili 普通视频 （不包含番剧、纪录片或是其他会员专享内容）
2. 播放 Bilibili 直播
3. 播放抖音视频

### 主要用法
1. 合成盔甲架命名为 `mcdia_player`
2. 合成书与笔，在第一页写入需要播放的视频（必须以 "https://live.bilibili.com/" 、"https://www.bilibili.com/" 开头，或是抖音分享文本），开头、中间不得存在空格或换行字符
3. 使用服务器内置的燧石盔甲架编辑器（见服务器 wiki）将书与笔放置在盔甲架**主手**
4. 等待视频播放

### 其他调节项
声音由盔甲架**副手 x 轴**旋转调节
播放由盔甲架**副手 y 轴**旋转调节（**会导致不同步**）
旋转由盔甲架头部旋转调节，或是主体旋转调节
大小由盔甲架缩放调节

在视频 URL 后换行输入类似 0:30:12 （小时:分钟:秒）来设置开始播放时间

在**副手**放置书与笔
- 第一页
    - 第一行：画面 x 轴相对偏移
    - 第二行：画面 y 轴相对偏移
    - 第三行：画面 z 轴相对偏移
    - 第四行：缩放（会与盔甲架缩放相乘）
- 第二页
    - 第一行：声源 x 轴相对偏移
    - 第二行：声源 y 轴相对偏移
    - 第三行：声源 z 轴相对偏移
    - 第四行：声音最大值 （默认 5）
    - 第五行：声音最大值的范围 （默认 1）
    - 第六行：可听到声音的最大范围（默认 500）
- 第三页
    - 只要包含 looping 字符串就会自动重播（**会导致不同步**）

## 服务器同步
视频同步播放依赖的是主手书与笔的物品名，首次播放会记录下 用户:时间戳，例：

```java
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
```
或查看 paper-1.21.7 内代码。




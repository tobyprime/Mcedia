package top.tobyprime.mcedia.playeragent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.PlayerAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PlayerConfigManager {
    private final PlayerAgent agent;

    // --- 配置字段 ---
    public float offsetX = 0, offsetY = 0, offsetZ = 0;
    public float scale = 1;
    public float audioOffsetX = 0, audioOffsetY = 0, audioOffsetZ = 0;
    public float audioMaxVolume = 5f;
    public float audioRangeMin = 2;
    public float audioRangeMax = 500;
    public float audioOffsetX2 = 0, audioOffsetY2 = 0, audioOffsetZ2 = 0;
    public float audioMaxVolume2 = 5f;
    public float audioRangeMin2 = 2;
    public float audioRangeMax2 = 500;
    public boolean isSecondaryAudioActive = false;
    public boolean danmakuEnable = false;
    public boolean showScrollingDanmaku = true;
    public boolean showTopDanmaku = true;
    public boolean showBottomDanmaku = true;
    public float danmakuDisplayArea = 1.0f;
    public float danmakuOpacity = 1.0f;
    public float danmakuFontScale = 1.0f;
    public float danmakuSpeedScale = 1.0f;
    public boolean videoAutoplay = false;
    public int customLightLevel = -1;
    public String desiredQuality = "自动";

    public PlayerConfigManager(PlayerAgent agent) {
        this.agent = agent;
    }

    /**
     * 从副手物品更新所有配置，并返回清晰度是否发生变化
     *
     * @param offHandItem 副手物品
     * @return 如果清晰度发生变化，则返回true，提示需要重载视频
     */
    public boolean updateConfigFrom(ItemStack offHandItem) {
        List<String> pages = getBookPages(offHandItem);

        if (pages == null) {
            resetAllToDefaults();
            return !this.desiredQuality.equals("自动"); // 如果之前不是自动，现在是了，也算变更
        }

        // 解析配置
        updateOffset(pages.size() > 0 ? pages.get(0) : null);
        updateAudio(pages.size() > 1 ? pages.get(1) : null);
        updateOther(pages.size() > 2 ? pages.get(2) : null);
        updateDanmaku(pages.size() > 4 ? pages.get(4) : null);

        // 处理清晰度变更
        String newQualityFromBook = (pages.size() > 3) ? pages.get(3) : null;
        String newQuality = (newQualityFromBook == null || newQualityFromBook.isBlank()) ? "自动" : newQualityFromBook.trim();

        if (!this.desiredQuality.equals(newQuality)) {
            this.desiredQuality = newQuality;
            return true; // 清晰度已改变
        }
        return false; // 清晰度未改变
    }

    private void resetAllToDefaults() {
        resetOffset();
        resetAudio();
        resetDanmaku();
        updateOther(null);
        this.desiredQuality = "自动";
    }

    // --- Private Helper Methods (从PlayerAgent移动过来) ---

    private void updateOffset(@Nullable String page) {
        if (page == null) {
            resetOffset();
            return;
        }
        try {
            var vars = page.split("\n");
            offsetX = Float.parseFloat(vars[0]);
            offsetY = Float.parseFloat(vars[1]);
            offsetZ = Float.parseFloat(vars[2]);
            scale = Float.parseFloat(vars[3]);
        } catch (Exception ignored) {
            resetOffset();
        }
    }

    public void updateAudio(String config) {
        if (config == null) return;
        String[] blocks = config.split("\\n\\s*\\n");

        try {
            if (blocks.length > 0 && !blocks[0].isBlank()) {
                String[] vars = blocks[0].split("\n");
                audioOffsetX = Float.parseFloat(vars[0]);
                audioOffsetY = Float.parseFloat(vars[1]);
                audioOffsetZ = Float.parseFloat(vars[2]);
                audioMaxVolume = Float.parseFloat(vars[3]);
                audioRangeMin = Float.parseFloat(vars[4]);
                audioRangeMax = Float.parseFloat(vars[5]);
            }
            if (blocks.length > 1 && !blocks[1].isBlank()) {
                String[] vars = blocks[1].split("\n");
                audioOffsetX2 = Float.parseFloat(vars[0]);
                audioOffsetY2 = Float.parseFloat(vars[1]);
                audioOffsetZ2 = Float.parseFloat(vars[2]);
                audioMaxVolume2 = Float.parseFloat(vars[3]);
                audioRangeMin2 = Float.parseFloat(vars[4]);
                audioRangeMax2 = Float.parseFloat(vars[5]);
                if (!isSecondarySourceActive) {
                    player.bindAudioSource(secondaryAudioSource);
                    isSecondarySourceActive = true;
                    LOGGER.info("已启用并配置副声源。");
                }
            } else {
                if (isSecondarySourceActive) {
                    player.unbindAudioSource(secondaryAudioSource);
                    isSecondarySourceActive = false;
                    LOGGER.info("已禁用副声源。");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析声源配置失败，请检查格式。", e);
        }
    }

    public void updateOther(String pageContent) {
        if (pageContent == null) {
            this.player.setLooping(false);
            this.shouldCacheForLoop = false;
            this.videoAutoplay = false;
            this.customLightLevel = -1;
            return;
        }
        String[] lines = pageContent.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().toLowerCase();
            if (i == 0 && line.equals("looping")) {
                this.player.setLooping(true);
                this.shouldCacheForLoop = McediaConfig.CACHING_ENABLED;
            } else if (i == 1 && line.equals("autoplay")) {
                this.videoAutoplay = true;
            } else if (line.startsWith("light:")) {
                try {
                    String valueStr = line.substring("light:".length()).trim();
                    int light = Integer.parseInt(valueStr);
                    this.customLightLevel = Mth.clamp(light, 0, 15);
                } catch (Exception e) {
                }
            } else if (line.equals("looping")) {
                this.player.setLooping(true);
                this.shouldCacheForLoop = McediaConfig.CACHING_ENABLED;
            } else if (line.equals("autoplay")) {
                this.videoAutoplay = true;
            }
        }
    }

    private void updateDanmaku(@Nullable String pageContent) {
        if (pageContent == null || pageContent.isBlank()) {
            resetDanmakuConfig();
            return;
        }

        String[] lines = pageContent.split("\n");

        this.danmakuEnable = lines.length > 0 && lines[0].contains("弹幕");

        if (lines.length > 1) this.danmakuDisplayArea = parsePercentage(lines[1]);
        else this.danmakuDisplayArea = 1.0f;

        if (lines.length > 2) this.danmakuOpacity = parsePercentage(lines[2]);
        else this.danmakuOpacity = 1.0f;

        if (lines.length > 3) this.danmakuFontScale = parseFloat(lines[3], 1.0f);
        else this.danmakuFontScale = 1.0f;

        if (lines.length > 4) this.danmakuSpeedScale = parseFloat(lines[4], 1.0f);
        else this.danmakuSpeedScale = 1.0f;

        this.showScrollingDanmaku = true;
        this.showTopDanmaku = true;
        this.showBottomDanmaku = true;

        if (lines.length > 5) {
            List<String> configLines = new ArrayList<>();
            for (int i = 5; i < lines.length; i++) {
                configLines.add(lines[i]);
            }
            String combinedConfig = String.join(" ", configLines).toLowerCase(); // 转为小写以便匹配

            if (combinedConfig.contains("屏蔽滚动")) {
                this.showScrollingDanmaku = false;
            } else if (combinedConfig.contains("显示滚动")) {
                this.showScrollingDanmaku = true;
            }

            if (combinedConfig.contains("屏蔽顶部")) {
                this.showTopDanmaku = false;
            } else if (combinedConfig.contains("显示顶部")) {
                this.showTopDanmaku = true;
            }

            if (combinedConfig.contains("屏蔽底部")) {
                this.showBottomDanmaku = false;
            } else if (combinedConfig.contains("显示底部")) {
                this.showBottomDanmaku = true;
            }
        }
    }

    public void resetOffset() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetZ = 0;
        this.scale = 1;
    }

    public void resetAudio() {
        this.audioOffsetX = 0;
        this.audioOffsetY = 0;
        this.audioOffsetZ = 0;
        this.audioMaxVolume = 5;
        this.audioRangeMin = 2;
        this.audioRangeMax = 500;
    }

    private void resetDanmaku() {
        this.danmakuEnable = false;
        this.danmakuDisplayArea = 1.0f;
        this.danmakuOpacity = 1.0f;
        this.danmakuFontScale = 1.0f;
        this.danmakuSpeedScale = 1.0f;
        this.showScrollingDanmaku = true;
        this.showTopDanmaku = true;
        this.showBottomDanmaku = true;
    }

    // 工具方法

    @Nullable
    private List<String> getBookPages(ItemStack bookStack) {
        boolean isTextFilteringEnabled = Minecraft.getInstance().isTextFilteringEnabled();
        if (bookStack.is(Items.WRITABLE_BOOK)) {
            WritableBookContent content = bookStack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (content != null) return content.getPages(isTextFilteringEnabled).toList();
        } else if (bookStack.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = bookStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null)
                return content.getPages(isTextFilteringEnabled).stream().map(Component::getString).collect(Collectors.toList());
        }
        return null;
    }
}
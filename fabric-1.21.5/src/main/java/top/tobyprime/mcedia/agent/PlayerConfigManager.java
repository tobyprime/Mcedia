package top.tobyprime.mcedia.agent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.PlayerAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PlayerConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerConfigManager.class);
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
    public boolean isSecondarySourceActive = false; // 原名 isSecondarySourceActive
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
    public boolean shouldCacheForLoop = false;

    public enum ConfigChangeType {
        NONE,           // 无变化
        RELOAD_MEDIA,   // 需要重载视频流 (如清晰度变化)
        HOT_UPDATE      // 只需要热更新 (如字幕、弹幕开关变化)
    }

    public PlayerConfigManager(PlayerAgent agent) {
        this.agent = agent;
    }

    /**
     * 从副手物品更新所有配置。
     * @param offHandItem 副手物品
     * @return 如果清晰度发生变化，则返回 true
     */
    public ConfigChangeType updateConfigFrom(ItemStack offHandItem) {
        List<String> pages = getBookPages(offHandItem);
        String previousQuality = this.desiredQuality;

        if (pages != null) {
            if (!pages.isEmpty()) updateOffset(pages.get(0)); else resetOffset();
            if (pages.size() > 1) updateAudioOffset(pages.get(1)); else resetAudioOffset();
            if (pages.size() > 2) updateOther(pages.get(2)); else updateOther(null);
            if (pages.size() > 3) updateQuality(pages.get(3)); else resetQuality();
            if (pages.size() > 4) updateDanmakuConfig(pages.get(4)); else resetDanmakuConfig();
        } else {
            resetOffset();
            resetAudioOffset();
            updateOther(null);
            resetQuality();
            resetDanmakuConfig();
        }

        boolean qualityChanged = !Objects.equals(this.desiredQuality, previousQuality);

        if (qualityChanged) {
            return ConfigChangeType.RELOAD_MEDIA;
        }

        return ConfigChangeType.NONE;
    }

    // --- 内部实现方法 ---

    public void updateOffset(String offset) {
        try {
            var vars = offset.split("\n");
            offsetX = Float.parseFloat(vars[0]);
            offsetY = Float.parseFloat(vars[1]);
            offsetZ = Float.parseFloat(vars[2]);
            scale = Float.parseFloat(vars[3]);
        } catch (Exception ignored) {
        }
    }

    public void resetOffset() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetZ = 0;
        this.scale = 1;
    }

    public void updateAudioOffset(String config) {
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
                    agent.getPlayer().bindAudioSource(agent.getSecondaryAudioSource());
                    isSecondarySourceActive = true;
                    LOGGER.info("已启用并配置副声源。");
                }
            } else {
                if (isSecondarySourceActive) {
                    agent.getPlayer().unbindAudioSource(agent.getSecondaryAudioSource());
                    isSecondarySourceActive = false;
                    LOGGER.info("已禁用副声源。");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析声源配置失败，请检查格式。", e);
        }
    }

    public void resetAudioOffset() {
        this.audioOffsetX = 0;
        this.audioOffsetY = 0;
        this.audioOffsetZ = 0;
        this.audioMaxVolume = 5;
        this.audioRangeMin = 2;
        this.audioRangeMax = 500;
    }

    public void updateOther(String pageContent) {
        if (pageContent == null) {
            agent.getPlayer().setLooping(false);
            this.shouldCacheForLoop = false;
            this.videoAutoplay = false;
            this.customLightLevel = -1;
            return;
        }
        boolean loopingFound = false;
        boolean autoplayFound = false;

        String[] lines = pageContent.split("\n");
        for (String lineRaw : lines) {
            String line = lineRaw.trim().toLowerCase();
            if (line.equals("looping")) {
                loopingFound = true;
            } else if (line.equals("autoplay")) {
                autoplayFound = true;
            } else if (line.startsWith("light:")) {
                try {
                    String valueStr = line.substring("light:".length()).trim();
                    int light = Integer.parseInt(valueStr);
                    this.customLightLevel = Mth.clamp(light, 0, 15);
                } catch (Exception e) {
                    this.customLightLevel = -1;
                }
            }
        }

        agent.getPlayer().setLooping(loopingFound);
        this.shouldCacheForLoop = loopingFound && McediaConfig.CACHING_ENABLED;
        this.videoAutoplay = autoplayFound;
        if (!pageContent.toLowerCase().contains("light:")) {
            this.customLightLevel = -1;
        }
    }

    private void updateQuality(@Nullable String pageContent) {
        this.desiredQuality = (pageContent == null || pageContent.isBlank()) ? "自动" : pageContent.trim();
    }

    private void resetQuality() {
        this.desiredQuality = "自动";
    }

    private void updateDanmakuConfig(@Nullable String pageContent) {
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
            String combinedConfig = String.join(" ", configLines).toLowerCase();

            if (combinedConfig.contains("屏蔽滚动")) this.showScrollingDanmaku = false;
            else if (combinedConfig.contains("显示滚动")) this.showScrollingDanmaku = true;

            if (combinedConfig.contains("屏蔽顶部")) this.showTopDanmaku = false;
            else if (combinedConfig.contains("显示顶部")) this.showTopDanmaku = true;

            if (combinedConfig.contains("屏蔽底部")) this.showBottomDanmaku = false;
            else if (combinedConfig.contains("显示底部")) this.showBottomDanmaku = true;
        }
    }

    private void resetDanmakuConfig() {
        this.danmakuEnable = false;
        this.danmakuDisplayArea = 1.0f;
        this.danmakuOpacity = 1.0f;
        this.danmakuFontScale = 1.0f;
        this.danmakuSpeedScale = 1.0f;
        this.showScrollingDanmaku = true;
        this.showTopDanmaku = true;
        this.showBottomDanmaku = true;
    }

    // --- 辅助方法 ---

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

    private float parsePercentage(String line) {
        try {
            String numericPart = line.replaceAll("[^\\d.]", "");
            if (numericPart.isBlank()) return 1.0f;
            float value = Float.parseFloat(numericPart);
            return Mth.clamp(value / 100.0f, 0.0f, 1.0f);
        } catch (NumberFormatException e) {
            return 1.0f;
        }
    }

    private float parseFloat(String line, float defaultValue) {
        try {
            String numericPart = line.replaceAll("[^\\d.]", "");
            if (numericPart.isBlank()) return defaultValue;
            return Float.parseFloat(numericPart);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
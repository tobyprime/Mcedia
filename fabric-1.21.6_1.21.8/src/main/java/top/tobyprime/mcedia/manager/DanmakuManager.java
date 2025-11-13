package top.tobyprime.mcedia.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.core.Danmaku;
import top.tobyprime.mcedia.core.Danmaku.DanmakuType;

import java.util.*;

public class DanmakuManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DanmakuManager.class);
    private List<Danmaku> allDanmaku = new ArrayList<>();
    private final List<Danmaku> activeDanmaku = new ArrayList<>();

    private int danmakuPoolIndex = 0;
    private final Random random = new Random();

    private final float[] trackAvailableTime; // 记录每个轨道下一次可用的时间点 (以视频秒为单位)
    private static final float FIXED_DANMAKU_DURATION_SEC = 5.0f;

    public DanmakuManager() {
        this.trackAvailableTime = new float[200]; // 支持最多200个轨道
    }

    public void load(List<Danmaku> danmakuList) {
        clear();
        this.allDanmaku = danmakuList;
    }

    public void update(float deltaTime, long currentVideoTimeUs, float halfW, float fontScale, float speedScale,
                       boolean showScrolling, boolean showTop, boolean showBottom) {
        if (allDanmaku.isEmpty()) return;

        float currentVideoTimeSec = currentVideoTimeUs / 1_000_000.0f;

        // 熔断与流量控制
        if (activeDanmaku.size() < McediaConfig.DANMAKU_HARD_CAP) {
            int spawnedThisTick = 0;
            while (danmakuPoolIndex < allDanmaku.size() &&
                    allDanmaku.get(danmakuPoolIndex).timestamp <= currentVideoTimeSec &&
                    spawnedThisTick < McediaConfig.DANMAKU_MAX_PER_TICK &&
                    activeDanmaku.size() < McediaConfig.DANMAKU_HARD_CAP) {

                Danmaku newDanmaku = allDanmaku.get(danmakuPoolIndex++); // 取出并推进指针

                boolean shouldSpawn = switch (newDanmaku.type) {
                    case SCROLLING -> showScrolling;
                    case TOP -> showTop;
                    case BOTTOM -> showBottom;
                };

                if (shouldSpawn && spawnDanmaku(newDanmaku, currentVideoTimeSec, halfW, fontScale, speedScale)) {
                    spawnedThisTick++;
                }
            }
        }

        updateActiveDanmaku(deltaTime);
    }

    private void updateActiveDanmaku(float deltaTime) {
        activeDanmaku.removeIf(danmaku -> {
            if (danmaku.type == DanmakuType.SCROLLING) {
                danmaku.x -= danmaku.speed * deltaTime;
                return danmaku.x + danmaku.width < 0;
            } else {
                danmaku.lifetime -= deltaTime;
                return danmaku.lifetime <= 0;
            }
        });
    }

    private boolean spawnDanmaku(Danmaku danmaku, float currentTime, float halfW, float fontScale, float speedScale) {
        int dynamicTrackCount = (int) (McediaConfig.DANMAKU_BASE_TRACK_COUNT / fontScale);
        if (dynamicTrackCount < 1) dynamicTrackCount = 1;
        if (dynamicTrackCount >= trackAvailableTime.length) dynamicTrackCount = trackAvailableTime.length - 1;

        int trackIdx = findAvailableTrack(danmaku.type, currentTime, dynamicTrackCount);
        if (trackIdx == -1) {
            return false;
        }

        Font font = Minecraft.getInstance().font;
        float videoWidthUnits = halfW * 2;
        float videoHeightUnits = 2.0f;
        float danmakuHeightUnits = videoHeightUnits / dynamicTrackCount;
        float scale = danmakuHeightUnits / font.lineHeight;
        float renderedWidth = font.width(danmaku.text) * scale;
        danmaku.width = renderedWidth / videoWidthUnits;
        danmaku.y = (float) trackIdx / dynamicTrackCount;

        if (danmaku.type == DanmakuType.SCROLLING) {
            float totalRelativeDistance = 1.0f + danmaku.width;
            float duration = (McediaConfig.DANMAKU_BASE_DURATION_SEC + random.nextFloat() * 4.0f) / speedScale;
            danmaku.speed = totalRelativeDistance / duration;
            danmaku.x = 1.0f;
            trackAvailableTime[trackIdx] = currentTime + (1.0f / danmaku.speed); // 记录弹幕头到达左边的时间
        } else { // TOP or BOTTOM
            danmaku.speed = 0;
            danmaku.x = 0.5f - danmaku.width / 2; // 居中
            danmaku.lifetime = FIXED_DANMAKU_DURATION_SEC;
            trackAvailableTime[trackIdx] = currentTime + FIXED_DANMAKU_DURATION_SEC; // 记录轨道被占用的结束时间
        }

        activeDanmaku.add(danmaku);
        return true;
    }

    private int findAvailableTrack(DanmakuType type, float currentTime, int trackCount) {
        int startIdx, endIdx, step;

        if (type == DanmakuType.BOTTOM) {
            // 底部弹幕，从下往上搜索轨道
            startIdx = trackCount - 1;
            endIdx = -1;
            step = -1;
        } else {
            // 滚动和顶部弹幕，从上往下搜索轨道
            startIdx = 0;
            endIdx = trackCount;
            step = 1;
        }

        for (int i = startIdx; i != endIdx; i += step) {
            if (trackAvailableTime[i] <= currentTime) {
                return i; // 找到一个已经空闲的轨道
            }
        }
        return -1; // 所有轨道都被占用
    }

    public void seek(long seekTimeUs) {
        LOGGER.info("[DANMAKU-DEBUG] Seeking danmaku to {} us", seekTimeUs);
        clearActiveDanmaku();

        float seekTimeSec = seekTimeUs / 1_000_000.0f;

        int searchIndex = Collections.binarySearch(allDanmaku, new Danmaku(seekTimeSec));
        if (searchIndex < 0) {
            searchIndex = -searchIndex - 1;
        }

        this.danmakuPoolIndex = Math.max(0, searchIndex);
        LOGGER.info("[DANMAKU-DEBUG] Danmaku pool index reset to {}", this.danmakuPoolIndex);
    }

    public void injectDanmaku(Danmaku danmaku, float currentVideoTimeSec, float halfW, float fontScale, float speedScale) {
        if (activeDanmaku.size() >= McediaConfig.DANMAKU_HARD_CAP) {
            return;
        }
        if (danmaku.type == Danmaku.DanmakuType.SCROLLING) {
            spawnDanmaku(danmaku, currentVideoTimeSec, halfW, fontScale, speedScale);
        }
    }

    public List<Danmaku> getActiveDanmaku() { return activeDanmaku; }

    public void clear() {
        allDanmaku.clear();
        clearActiveDanmaku();
        danmakuPoolIndex = 0;
    }

    private void clearActiveDanmaku() {
        activeDanmaku.clear();
        Arrays.fill(trackAvailableTime, 0);
    }
}
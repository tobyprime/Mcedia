package top.tobyprime.mcedia.danmaku;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DanmakuPlay {
    public static Logger logger = LoggerFactory.getLogger(DanmakuPlay.class);
    float danmakuDuration = 2;
    float secs = 0;

    List<Danmaku> activeDanmakus = new ArrayList<>();
    Danmaku pendingDanmaku = null;

    ArrayList<Danmaku> sortedDanmakus;
    Iterator<Danmaku> it;

    public DanmakuPlay(List<Danmaku> danmakus, float danmakuDuration) {
        this.sortedDanmakus = new ArrayList<>(danmakus.stream().sorted().toList());
        it = sortedDanmakus.listIterator();
        this.danmakuDuration = danmakuDuration;
    }


    private boolean isExpired(Danmaku danmaku) {
        return danmaku.secs + danmakuDuration < secs;
    }

    private boolean isFuture(Danmaku danmaku) {
        return danmaku.secs > secs;
    }

    private boolean isActive(@NotNull Danmaku danmaku) {
        return (!isExpired(danmaku)) && (!isFuture(danmaku));
    }

    public List<Danmaku> lookForward() {
        List<Danmaku> newDanmakus = new ArrayList<>();

        // 下一个还没到则直接返回空
        if (pendingDanmaku != null && isFuture(pendingDanmaku)) {
            return newDanmakus;
        }

        // 下一个已经到了，则先加入活动弹幕，并继续
        if (pendingDanmaku != null && isActive(pendingDanmaku)) {
            activeDanmakus.add(pendingDanmaku);
            newDanmakus.add(pendingDanmaku);

            pendingDanmaku = null;
        }


        while (it.hasNext()) {
            var danmaku = it.next();

            // 过期则跳过
            if (isExpired(danmaku)) {
                continue;
            }

            // 下一个还没到则结束
            if (isFuture(danmaku)) {
                pendingDanmaku = danmaku;
                break;
            }
            newDanmakus.add(danmaku);
            activeDanmakus.add(danmaku);
        }
        return newDanmakus;
    }

    public List<Danmaku> removeExpired() {

        var expiredDanmakus = activeDanmakus.stream().filter(this::isExpired).toList();
        this.activeDanmakus.removeIf(expiredDanmakus::contains);
        return expiredDanmakus;
    }

    public void reset() {
        this.activeDanmakus = new ArrayList<>();
        this.pendingDanmaku = null;
        this.it = sortedDanmakus.iterator();
        this.secs = 0;
    }

    public DanmakuPlayUpdateResult update(float secs) {

        DanmakuPlayUpdateResult result = new DanmakuPlayUpdateResult();

        if (this.secs > secs) {
            result.removedDanmakus = this.activeDanmakus;
            reset();
        } else {
            result.removedDanmakus = removeExpired();
        }

        this.secs = secs;

        result.newDanmakus = lookForward();

        result.activeDanmakus = this.activeDanmakus;
        if (!result.removedDanmakus.isEmpty()) {
            logger.info("removed damakus {}", result.removedDanmakus.size());

        }
        if (result.newDanmakus != null && !result.newDanmakus.isEmpty()) {
            float floatNExt = -1;
            if (pendingDanmaku != null) {
                floatNExt = pendingDanmaku.secs - secs;
            }
            logger.info("new damakus {}, next {}", result.newDanmakus.size(), floatNExt);
        }
        return result;
    }

    public List<Danmaku> getActiveDanmakus() {
        return activeDanmakus;
    }
}


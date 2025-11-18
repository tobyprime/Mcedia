package top.tobyprime.mcedia.danmaku;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Configs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class DanmakuPlay {
    public static Logger logger = LoggerFactory.getLogger(DanmakuPlay.class);
    float secs = 0;

    Danmaku pendingDanmaku = null;

    ArrayList<Danmaku> sortedDanmakus;
    Iterator<Danmaku> it;

    public DanmakuPlay(List<Danmaku> danmakus) {
        this.sortedDanmakus = new ArrayList<>(danmakus.stream().sorted().toList());
        it = sortedDanmakus.listIterator();
    }


    private boolean isExpired(Danmaku danmaku) {
        return danmaku.secs + Configs.DANMAKU_DURATION < secs;
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
        }
        return newDanmakus;
    }

    public void reset() {
        this.pendingDanmaku = null;
        this.it = sortedDanmakus.iterator();
        this.secs = 0;
    }

    public Collection<Danmaku> update(float secs) {
        if (secs < this.secs) {
            reset();
        }

        DanmakuPlayUpdateResult result = new DanmakuPlayUpdateResult();

        this.secs = secs;

        result.newDanmakus = lookForward();

        return result.newDanmakus;
    }
}


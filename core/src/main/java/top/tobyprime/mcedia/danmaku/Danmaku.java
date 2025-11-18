package top.tobyprime.mcedia.danmaku;

import java.util.Objects;

public class Danmaku implements Comparable<Danmaku> {
    public final float secs; // 弹幕在视频中出现的时间（秒）
    public final String text;
    public final int color;
    public final DanmakuType type;
    public Danmaku(float timestamp, String text, int color, DanmakuType type) {
        this.secs = timestamp;
        this.text = text;
        this.color = color;
        this.type = type;
    }

    public Danmaku(float timestamp) {
        this(timestamp, "", 0, DanmakuType.SCROLLING);
    }

    @Override
    public int compareTo(Danmaku other) {
        return Float.compare(this.secs, other.secs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Danmaku danmaku = (Danmaku) o;
        return Float.compare(danmaku.secs, secs) == 0 && Objects.equals(text, danmaku.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(secs, text);
    }

    public enum DanmakuType {
        SCROLLING,
        TOP,
        BOTTOM
    }
}
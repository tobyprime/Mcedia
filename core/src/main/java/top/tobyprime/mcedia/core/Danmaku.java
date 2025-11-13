package top.tobyprime.mcedia.core;

import java.util.Objects;

public class Danmaku implements Comparable<Danmaku> {
    public enum DanmakuType {
        SCROLLING,
        TOP,
        BOTTOM
    }

    public final float timestamp; // 弹幕在视频中出现的时间（秒）
    public final String text;
    public final int color;
    public final DanmakuType type;

    // 渲染时使用的动态属性
    public float x; // 当前X坐标（相对于屏幕宽度，1.0为最右侧）
    public float y; // 当前Y坐标（相对于屏幕高度，0.0-1.0）
    public float speed; // 移动速度
    public float width; // 文本宽度
    public float lifetime; // 固定弹幕的生命周期

    public Danmaku(float timestamp, String text, int color, DanmakuType type) {
        this.timestamp = timestamp;
        this.text = text;
        this.color = color;
        this.type = type;
    }

    public Danmaku(float timestamp) {
        this(timestamp, "", 0, DanmakuType.SCROLLING);
    }

    @Override
    public int compareTo(Danmaku other) {
        return Float.compare(this.timestamp, other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Danmaku danmaku = (Danmaku) o;
        return Float.compare(danmaku.timestamp, timestamp) == 0 && Objects.equals(text, danmaku.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, text);
    }
}
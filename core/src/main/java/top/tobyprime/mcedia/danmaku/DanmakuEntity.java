package top.tobyprime.mcedia.danmaku;

public final class DanmakuEntity {
    public final Danmaku danmaku;
    // 以轨道长度为单位
    public final float width;
    public final int trackId;
    public float position;

    public DanmakuEntity(Danmaku danmaku, float position, float width, int trackId) {
        this.danmaku = danmaku;
        this.position = position;
        this.width = width;
        this.trackId = trackId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DanmakuEntity e) {
            return danmaku == e.danmaku;
        }
        return false;
    }

}

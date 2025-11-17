package top.tobyprime.mcedia.danmaku;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.Configs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class DanmakuScreen {
    public HashMap<Danmaku, DanmakuEntity> entities = new HashMap<>();
    public DanmakuPlay danmakuPlay;
    // 每个弹幕轨道最后一个弹幕
    public List<Track> tracks = new ArrayList<>();
    @Nullable
    private Function<Danmaku, Float> widthPredictor;

    public DanmakuScreen(List<Danmaku> danmakus) {
        this.danmakuPlay = new DanmakuPlay(danmakus);
        for (int i = 0; i < Configs.DANMAKU_TRACKS; i++) {
            this.tracks.add(new Track(i));
        }
    }

    public void setWidthPredictor(@Nullable Function<Danmaku, Float> widthPredictor) {
        this.widthPredictor = widthPredictor;
    }

    public Collection<DanmakuEntity> update(float secs) {
        var result = danmakuPlay.update(secs);

        for (var removed : result.removedDanmakus) {
            var entity = entities.remove(removed);
            if (entity == null) {
                continue;
            }
            var track = tracks.get(entity.trackId);
            if (entity.equals(track.tailEntity)) {
                track.tailEntity = null;
            }
        }

        for (var entity : this.entities.values()) {
            entity.position = (1 - (secs - entity.danmaku.secs) * ((entity.width + 1) / Configs.DANMAKU_DURATION));
        }

        for (var newDanmakus : result.newDanmakus) {
            spawnDanmaku(newDanmakus);
        }
        return entities.values();
    }


    protected float getDanmakuWidth(Danmaku danmaku) {
        var predictor = widthPredictor;
        if (predictor == null) return -1;
        return predictor.apply(danmaku);
    }

    /**
     * 尝试生成弹幕
     */
    protected void spawnDanmaku(Danmaku danmaku) {
        for (var track : tracks) {
            var width = getDanmakuWidth(danmaku);
            if (width == -1) return;

            DanmakuEntity entity = track.trySpawnDanmaku(danmaku, width);

            if (entity != null) {
                this.entities.put(danmaku, entity);
                return;
            }
        }
    }


    public static class Track {
        public int id;

        @Nullable
        public DanmakuEntity tailEntity;

        public Track(int i) {
            id = i;
        }

        @Nullable
        public DanmakuEntity trySpawnDanmaku(Danmaku danmaku, float width) {
            if (tailEntity == null || tailEntity.position + tailEntity.width < 1) {
                var entity = new DanmakuEntity(danmaku, 1, width, id);
                tailEntity = entity;
                return entity;
            }
            return null;
        }

    }
}


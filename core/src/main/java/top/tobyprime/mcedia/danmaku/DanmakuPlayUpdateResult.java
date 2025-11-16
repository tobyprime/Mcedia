package top.tobyprime.mcedia.danmaku;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DanmakuPlayUpdateResult {
    @NotNull
    public List<Danmaku> activeDanmakus = new ArrayList<>();
    @NotNull
    public List<Danmaku> removedDanmakus = new ArrayList<>();
    @NotNull
    public List<Danmaku> newDanmakus = new ArrayList<>();
}

// PlaylistManager.java
package top.tobyprime.mcedia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 负责管理视频URL列表、播放顺序和循环逻辑。
 * 这是一个简单的状态管理器，不直接处理媒体播放，只提供要播放的URL。
 */
public class PlaylistManager {
    private List<String> urls = Collections.emptyList();
    private int currentIndex = -1;
    private boolean loopPlaylist = false;

    /**
     * 加载一个新的播放列表。如果新列表与当前列表不同，则重置播放状态。
     * @param newUrls 新的视频URL列表。
     * @param loop 是否循环整个播放列表。
     * @return 如果播放列表发生了变化，则返回 true，否则返回 false。
     */
    public boolean load(List<String> newUrls, boolean loop) {
        // 如果列表和循环设置都没有改变，则不做任何事
        if (!hasChanged(newUrls) && this.loopPlaylist == loop) {
            return false;
        }

        this.urls = new ArrayList<>(newUrls);
        this.loopPlaylist = loop;
        this.currentIndex = -1; // 准备从头开始播放
        return true;
    }

    /**
     * 获取播放列表中的下一个URL。
     * 此方法会推进播放索引。
     * @return 下一个要播放的URL，如果播放列表结束则返回null。
     */
    public String getNextUrl() {
        if (urls.isEmpty()) {
            return null;
        }

        currentIndex++;

        if (currentIndex >= urls.size()) {
            if (loopPlaylist) {
                currentIndex = 0; // 循环到第一个
            } else {
                return null; // 列表播放完毕
            }
        }

        // 再次检查索引有效性，以防列表为空时循环
        if (currentIndex < urls.size()) {
            return urls.get(currentIndex);
        }

        return null;
    }

    /**
     * 获取当前正在播放的URL，不推进索引。
     * @return 当前URL，如果未开始播放则返回null。
     */
    public String getCurrentUrl() {
        if (currentIndex >= 0 && currentIndex < urls.size()) {
            return urls.get(currentIndex);
        }
        return null;
    }

    /**
     * 清空播放列表并重置状态。
     */
    public void clear() {
        this.urls = Collections.emptyList();
        this.currentIndex = -1;
    }

    /**
     * 检查新列表是否与当前列表不同。
     */
    private boolean hasChanged(List<String> newUrls) {
        return !Objects.equals(this.urls, newUrls);
    }

    public boolean hasPlaylist() {
        return !urls.isEmpty();
    }
}
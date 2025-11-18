package top.tobyprime.mcedia.interfaces;

import top.tobyprime.mcedia.core.MediaPlayer;

public interface IMediaPlayerInstance {
    /**
     * 获取 Player
     */
    public MediaPlayer getPlayer();

    /**
     * 获取玩家到 player 的距离
     */
    public double getDistance();

    /**
     * 玩家是否正注视该播放器实例
     *
     * @return >=0 则代表正在注视的距离, <0 代表不在注视
     */
    public double isTargeting();

    /**
     * 强制移除
     */
    public void remove();

    /**
     * 是否已经被移除了
     */
    public boolean isRemoved();
}

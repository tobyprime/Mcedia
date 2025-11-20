package top.tobyprime.mcedia.player_instances;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.component.WritableBookContent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Utils;
import top.tobyprime.mcedia.core.AudioSource;
import top.tobyprime.mcedia.core.AudioSourceInstance;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.decoders.DecoderConfiguration;
import top.tobyprime.mcedia.entities.MediaPlayerAgentEntity;
import top.tobyprime.mcedia.interfaces.IMediaPlayerInstance;
import top.tobyprime.mcedia.player_instance_managers.ArmorStandPlayerManager;
import top.tobyprime.mcedia.renderers.MediaPlayerScreen;

import java.util.Arrays;
import java.util.Objects;

public class ArmorStandPlayerAgentWrapper implements IMediaPlayerInstance {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArmorStandPlayerAgentWrapper.class);
    private final MediaPlayerAgentEntity playerAgent;
    private final ArmorStand armorStand;
    private final MediaPlayer player;
    public String playingUrl;
    public float speed = 1;
    WritableBookContent preOffHandBookComponent = null;
    String inputContent = null;
    MediaPlayerScreen screen;
    AudioSourceInstance audioSourceInstance;
    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private float scale = 1;
    private float audioMaxVolume = 5f;
    private float audioRangeMin = 2;
    private float audioRangeMax = 500;
    public boolean closed = false;

    public static long parseToMicros(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be null or empty");
        }

        String[] parts = timeStr.split(":");
        int len = parts.length;

        int hours, minutes = 0, seconds = 0;

        try {
            if (len == 1) {
                // 可能只有小时
                hours = Integer.parseInt(parts[0]);
            } else if (len == 2) {
                // 小时:分钟
                hours = Integer.parseInt(parts[0]);
                minutes = Integer.parseInt(parts[1]);
            } else if (len == 3) {
                // 小时:分钟:秒
                hours = Integer.parseInt(parts[0]);
                minutes = Integer.parseInt(parts[1]);
                seconds = Integer.parseInt(parts[2]);
            } else {
                throw new IllegalArgumentException("Invalid time format: " + timeStr);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in time string: " + timeStr, e);
        }

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        return totalSeconds * 1_000_000L;
    }

    public void updateInputUrl(String content) {
        try {
            if (content == null) {
                open(null);
                return;
            }
            inputContent = content;
            var args = content.split("\n");
            var url = args[0];
            if (Objects.equals(url, playingUrl)) {
                return;
            }
            open(url);
        } catch (Exception e) {
            LOGGER.warn("播放失败", e);
            Utils.msgToPlayer("播放失败");
        }
    }


    public void resetOffset() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetZ = 0;
        this.scale = 1;
    }

    public void updateOther(String flags) {
        this.player.setLooping(flags.contains("looping") || flags.contains("循环播放"));
        this.screen.renderDanmaku = !(flags.contains("nodanmaku") || flags.contains("关闭弹幕"));
    }

    public void updateOffset(String offset) {
        try {
            var vars = offset.split("\n");
            offsetX = Float.parseFloat(vars[0]);
            offsetY = Float.parseFloat(vars[1]);
            offsetZ = Float.parseFloat(vars[2]);
            scale = Float.parseFloat(vars[3]);
        } catch (Exception ignored) {
        }
    }

    public void resetAudioOffset() {
        this.audioSourceInstance.offsetX = 0;
        this.audioSourceInstance.offsetY = 0;
        this.audioSourceInstance.offsetZ = 0;
        this.audioMaxVolume = 5;
        this.audioRangeMin = 2;
        this.audioRangeMax = 500;
    }


    private long getBaseDuration() {
        long duration = 0;
        try {
            var args = inputContent.split("\n");
            if (args.length < 2) return 0;
            duration = parseToMicros(args[1]);
        } catch (Exception e) {
            LOGGER.info("获取base duration失败", e);
        }
        return duration;
    }

    public ArmorStandPlayerAgentWrapper(ArmorStand entity) {
        LOGGER.info("在 {} 新增了一个 Armor Stand Player", entity.position());
        this.armorStand = entity;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            throw new RuntimeException("level 为 null");
        }
        playerAgent = new MediaPlayerAgentEntity(MediaPlayerAgentEntity.TYPE, level);
        level.addEntity(playerAgent);
        player = playerAgent.getPlayer();
        player.setDecoderConfiguration(new DecoderConfiguration(new DecoderConfiguration.Builder()));
        screen = new MediaPlayerScreen();
        audioSourceInstance = new AudioSourceInstance(new AudioSource(Utils.getAudioExecutor()::schedule), 0, 0, 0, -1);

        playerAgent.addScreen(screen);
        playerAgent.addAudioSource(audioSourceInstance);
    }

    public long getDuration() {
        return getBaseDuration() + getServerDuration();
    }

    public void updateAudioOffset(String config) {
        try {
            var vars = config.split("\n");
            audioSourceInstance.offsetX = Float.parseFloat(vars[0]);
            audioSourceInstance.offsetY = Float.parseFloat(vars[1]);
            audioSourceInstance.offsetZ = Float.parseFloat(vars[2]);
            audioMaxVolume = Float.parseFloat(vars[3]);
            audioRangeMin = Float.parseFloat(vars[4]);
            audioRangeMax = Float.parseFloat(vars[5]);

        } catch (Exception ignored) {
        }
    }

    public long getServerDuration() {
        try {
            var args = armorStand.getMainHandItem().getDisplayName().getString().split(":");
            var duration = System.currentTimeMillis() - Long.parseLong(args[1].substring(0, args[1].length() - 1));
            LOGGER.info("从 {} 开始播放", duration);
            if (duration < 1000) {
                return 0;
            }
            return duration * 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    public float rotationToFactor(float rotation) {
        if (rotation < 0) {
            return -rotation / 360;
        } else {
            return (360.0f - rotation) / 360;
        }
    }

    public void tick() {
        update();
    }

    public void update() {
        try {
            playerAgent.setPos(armorStand.position());
            playerAgent.rotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(-armorStand.getYRot()))
                    .rotateX((float) Math.toRadians(-armorStand.getXRot()))
                    .rotateY((float) Math.toRadians(-armorStand.getHeadPose().getY()))
                    .rotateZ((float) Math.toRadians(-armorStand.getHeadPose().getZ()))
                    .rotateX((float) Math.toRadians(armorStand.getHeadPose().getX()));

            var mainHandBook = armorStand.getItemInHand(InteractionHand.MAIN_HAND);

            if (mainHandBook.getItem() instanceof WritableBookItem) {
                var components = mainHandBook.get(DataComponents.WRITABLE_BOOK_CONTENT);
                if (components != null) {
                    updateInputUrl(components.getPages(Minecraft.getInstance().isTextFilteringEnabled()).findFirst().orElse(null));
                } else {
                    this.open(null);
                }
            } else {
                this.open(null);
            }
            var offHandBook = armorStand.getItemInHand(InteractionHand.OFF_HAND);

            if (offHandBook.getItem() instanceof WritableBookItem) {
                var components = offHandBook.get(DataComponents.WRITABLE_BOOK_CONTENT);
                if (components != null && components != preOffHandBookComponent) {
                    preOffHandBookComponent = components;
                    var pages = components.getPages(Minecraft.getInstance().isTextFilteringEnabled()).toList();
                    if (!pages.isEmpty()) {
                        updateOffset(pages.getFirst());
                    }
                    if (pages.size() > 1) {
                        updateAudioOffset(pages.get(1));
                    }
                    if (pages.size() > 2) {
                        updateOther(pages.get(2));
                    }
                }
            } else {
                resetOffset();
                resetAudioOffset();
            }

        } catch (Exception ignored) {
        }
        screen.Height = armorStand.getScale() * scale;

        screen.offset.x = offsetX;

        /// 确保底部在默认状态下与地面平行
        screen.offset.y = (float) (offsetY + 1.02 * screen.Height);
        screen.offset.z = offsetZ;

        var volumeFactor = 1 - rotationToFactor(armorStand.getLeftArmPose().getX());
        var speedFactor = rotationToFactor(armorStand.getRightArmPose().getY());
        var nowSpeed = 0.1;
        if (speedFactor < 0.1) {
            nowSpeed = 1;
        } else if (speedFactor > 0.5) {
            nowSpeed = 1 - (1 - speedFactor) * 2;
        } else {
            nowSpeed = (speedFactor - 0.1f) / 0.4f * 8;
        }
        if (nowSpeed != speed)
            player.setSpeed(speed);
        var volume = volumeFactor * audioMaxVolume;

        audioSourceInstance.audioSource.setVolume(volume);
        audioSourceInstance.audioSource.setRange(audioRangeMin, audioRangeMax);
    }

    /**
     * 关闭媒体
     */
    public void stopMedia() {
        player.stop();
    }

    public void open(@Nullable String mediaUrl) {
        playingUrl = mediaUrl;

        if (mediaUrl == null) {
            stopMedia();
            return;
        }

        long duration = getDuration();

        var name = armorStand.getMainHandItem().getDisplayName().getString().substring(1);
        var poster = name.contains(":") ? Arrays.stream(armorStand.getMainHandItem().getDisplayName().getString().substring(1).split(":")).findFirst().orElse("未知") : "未知";

        LOGGER.info("准备播放 {}", mediaUrl);

        var mediaPlay = player.getMediaPlayAndOpen(mediaUrl, (media) -> {
            player.play();
            media.seek(duration % media.getLength());
            Utils.msgToPlayer(poster + "播放: " + media.getMediaInfo().title);
        });
        if (mediaPlay.getStatus() != null) {
            Utils.msgToPlayer("播放器状态" + mediaPlay.getStatus());
        }
        mediaPlay.registerOnStatusUpdatedEventAndCallOnce(status -> {
            if (status != null)
                Utils.msgToPlayer("播放器状态: " + status);
        });
    }

    /**
     * 销毁播放器
     */
    public void close() {
        closed = true;
        stopMedia();
        playerAgent.remove(Entity.RemovalReason.KILLED);
    }

    @Override
    public MediaPlayer getPlayer() {
        return player;
    }

    @Override
    public double getDistance() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            return armorStand.position().distanceTo(player.position());
        }
        return Double.MAX_VALUE;
    }

    @Override
    public double isTargeting() {
        if (Minecraft.getInstance().crosshairPickEntity == this.armorStand) {
            return getDistance();
        }
        return -1;
    }

    @Override
    public void remove() {
        ArmorStandPlayerManager.getInstance().removePlayer(this.armorStand);
    }

    @Override
    public boolean isRemoved() {
        return closed;
    }
}

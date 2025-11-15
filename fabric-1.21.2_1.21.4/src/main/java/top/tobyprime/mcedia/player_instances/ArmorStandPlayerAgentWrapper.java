package top.tobyprime.mcedia.player_instances;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
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
import top.tobyprime.mcedia.renderers.MediaPlayerScreen;
import top.tobyprime.mcedia.video_fetcher.MediaFetchers;

import java.util.Arrays;
import java.util.Objects;

public class ArmorStandPlayerAgentWrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArmorStandPlayerAgentWrapper.class);
    private final MediaPlayerAgentEntity playerAgent;
    private final ArmorStand entity;
    public String playingUrl;
    WritableBookContent preOffHandBookComponent = null;
    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private float scale = 1;

    private float audioMaxVolume = 5f;
    private final MediaPlayer player;

    private float audioRangeMin = 2;
    private float audioRangeMax = 500;
    public static long parseToMicros(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be null or empty");
        }

        String[] parts = timeStr.split(":");
        int len = parts.length;

        int hours = 0, minutes = 0, seconds = 0;

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
    String inputContent = null;

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
        }
    }

    public void resetOffset() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetZ = 0;
        this.scale = 1;
    }
    public float speed = 1;

    public void updateOther(String flags) {
        this.player.setLooping(flags.contains("looping"));
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
    MediaPlayerScreen screen;
    AudioSourceInstance audioSourceInstance;
    public ArmorStandPlayerAgentWrapper(ArmorStand entity) {
        LOGGER.info("在 {} 新增了一个 Mcdia Player", entity.position());
        this.entity = entity;
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

    public void resetAudioOffset() {
        this.audioSourceInstance.offsetX = 0;
        this.audioSourceInstance.offsetY = 0;
        this.audioSourceInstance.offsetZ = 0;
        this.audioMaxVolume = 5;
        this.audioRangeMin = 2;
        this.audioRangeMax = 500;
    }


    private long getBaseDuration(){
        long duration = 0;
        try {
            var args = inputContent.split("\n");
            if (args.length < 2) return 0;
            duration = parseToMicros(args[1]);
        }catch (Exception e){
            LOGGER.info("获取base duration失败",e);
        }
        return duration;
    }

    public long getServerDuration() {
        try {
            var args = entity.getMainHandItem().getDisplayName().getString().split(":");
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
    public long getDuration(){
        return  getBaseDuration() + getServerDuration();
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

    public void update() {
        try{
            playerAgent.setPos(entity.position());
            playerAgent.rotation = new Quaternionf()
                    .rotateXYZ((float) Math.toRadians(-entity.getXRot()), (float) Math.toRadians(-entity.getYRot()), 0)
                    .rotateXYZ((float) Math.toRadians(-entity.getHeadPose().getX()), (float) Math.toRadians(-entity.getHeadPose().getY()), (float) Math.toRadians(-entity.getHeadPose().getZ()));

            var mainHandBook = entity.getItemInHand(InteractionHand.MAIN_HAND);

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
            var offHandBook = entity.getItemInHand(InteractionHand.OFF_HAND);

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

        }
        catch (Exception ignored){
        }
        screen.Height = entity.getScale() * scale;

        screen.offset.x = offsetX;

        /// 确保底部在默认状态下与地面平行
        screen.offset.y = (float) (offsetY + 1.02 * screen.Height);
        screen.offset.z = offsetZ;

        var volumeFactor = 1 - rotationToFactor(entity.getLeftArmPose().getX());
        var speedFactor = rotationToFactor(entity.getRightArmPose().getY());
        if (speedFactor < 0.1) {
            speed = 1;
        } else if (speedFactor > 0.5) {
            speed = 1 - (1 - speedFactor) * 2;
        } else {
            speed = (speedFactor - 0.1f) / 0.4f * 8;
        }

        player.setSpeed(speed);
        var volume = volumeFactor * audioMaxVolume;

        audioSourceInstance.audioSource.setVolume(volume);
        audioSourceInstance.audioSource.setRange(audioRangeMin, audioRangeMax);
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

    public void open(@Nullable String mediaUrl) {
        playingUrl = mediaUrl;

        if (mediaUrl == null) {
            stopMedia();
            return;
        }

        long duration = getDuration();

        var name = entity.getMainHandItem().getDisplayName().getString().substring(1);
        var poster = name.contains(":") ? Arrays.stream(entity.getMainHandItem().getDisplayName().getString().substring(1).split(":")).findFirst().orElse("未知") : "未知";
        Utils.msgToPlayer(poster + "点播: " + mediaUrl);

        LOGGER.info("准备播放 {}", mediaUrl);

        player.openAsync(() -> {
            var media = MediaFetchers.getMedia(mediaUrl);
            Utils.msgToPlayer(poster + "播放" + media.platform + "上的视频: " + media.title);

            return media.streamUrl;
        }).exceptionally((e) -> {
            LOGGER.warn("打开视频失败", e);
            Utils.msgToPlayer("无法解析或播放: " + mediaUrl);
            throw new RuntimeException(e);
        }).thenRun(() -> {
            player.play();
            player.seek(duration);
            player.setSpeed(2);
        }).exceptionally(e -> {
            LOGGER.warn("播放视频失败", e);
            Utils.msgToPlayer("无法解析或播放: " + mediaUrl);
            throw new RuntimeException(e);
        });
    }

    /**
     * 关闭媒体
     */
    public void stopMedia() {
        player.stopAsync();
    }

    /**
     * 销毁播放器
     */
    public void close(){
        stopMedia();
        playerAgent.remove(Entity.RemovalReason.KILLED);
    }
}

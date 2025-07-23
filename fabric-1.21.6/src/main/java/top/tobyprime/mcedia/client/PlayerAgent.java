package top.tobyprime.mcedia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.component.WritableBookContent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.*;

import java.util.concurrent.Executors;

public class PlayerAgent {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAgent.class);
    private final ArmorStand entity;
    private @Nullable McediaDecoder player;
    public String playingUrl;
    WritableBookContent preOffHandBookComponent = null;
    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private final float audioOffsetX = 0;
    private final float audioOffsetY = 0;
    private final float audioOffsetZ = 0;

    public PlayerAgent(ArmorStand entity) {
        LOGGER.info("在 {} 新增了一个 Mcdia Player", entity.position());
        this.entity = entity;
    }
    private float audioRange = 500;

    public void updateInputUrl(String url) {
        if (url == null) {
            play(null);
            return;
        }
        play(url);
    }

    public void resetOffset() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetZ = 0;
    }


    public void resetAudioOffset() {
        this.audioRange = 500;
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetZ = 0;
    }

    public void updateOffset(String offset) {
        try {
            var vars = offset.split("\n");
            offsetX = Float.parseFloat(vars[0]);
            offsetY = Float.parseFloat(vars[1]);
            offsetZ = Float.parseFloat(vars[2]);
        } catch (Exception ignored) {
            resetOffset();
        }
    }

    public void updateAudioOffset(String config) {
        try {
            var vars = config.split("\n");
            audioRange = Float.parseFloat(vars[0]);
            offsetX = Float.parseFloat(vars[1]);
            offsetY = Float.parseFloat(vars[2]);
            offsetZ = Float.parseFloat(vars[3]);
        } catch (Exception ignored) {
            resetAudioOffset();
        }
    }


    public void tick() {
        var mainHandBook = entity.getItemInHand(InteractionHand.MAIN_HAND);

        if (mainHandBook.getItem() instanceof WritableBookItem) {
            var components = mainHandBook.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (components != null) {
                updateInputUrl(components.getPages(Minecraft.getInstance().isTextFilteringEnabled()).findFirst().orElse(null));
            } else {
                this.play(null);
            }
        } else {
            this.play(null);
        }
        var offHandBook = entity.getItemInHand(InteractionHand.OFF_HAND);

        if (offHandBook.getItem() instanceof WritableBookItem) {
            var components = offHandBook.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (components != null && components != preOffHandBookComponent) {
                preOffHandBookComponent = components;
                var pages = components.getPages(Minecraft.getInstance().isTextFilteringEnabled()).toList();
                if (pages.size() > 0) {
                    updateOffset(pages.get(0));
                }
                if (pages.size() > 1) {
                    updateAudioOffset(pages.get(1));
                }
            }
        } else {
            resetOffset();
            resetAudioOffset();
        }
    }


    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i) {
        if (!isPlaying()) {
            return;
        }
        var size = state.scale;
        var volume = Math.abs(state.leftArmPose.x() / 60);
        var audioOffsetRotated = new Vector3f(audioOffsetX, audioOffsetY, audioOffsetZ).rotateY(state.yRot);

        player.setAudioVolume(volume);
        player.setAudioMaxDistance(audioRange);
        if (i < 0) {
            i = 5;
        }
        player.setAudioPos(((float) state.x + audioOffsetRotated.x), ((float) state.y + audioOffsetRotated.y), ((float) state.z + audioOffsetRotated.z));
        poseStack.pushPose();

        poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.yRot), 0, 0));
        poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.headPose.x()), (float) Math.toRadians(-state.headPose.y()), (float) Math.toRadians(-state.headPose.z())));
        poseStack.translate(offsetX, offsetY + 1 * state.scale, offsetZ + 0.6 * state.scale);
        poseStack.scale(size, size, size);
        VertexConsumer consumer = player.createBuffer(bufferSource);

        var matrix = poseStack.last().pose();

        float halfW = (float) (player.getAspect());

        consumer.addVertex(matrix, -halfW, -1, 0).setLight(i).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, -1, 0).setLight(i).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, 1, 0).setLight(i).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, -halfW, 1, 0).setLight(i).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);

        poseStack.popPose();
    }

    public boolean isPlaying() {
        if (player == null) {
            return false;
        }
        return player.isPlaying();
    }

    public void play(@Nullable String mediaUrl) {
        if (mediaUrl == null) {
            stop();
            return;
        }
        if (player != null) {
            if (playingUrl.equals(mediaUrl)) return;
            stop();
        }
        playingUrl = mediaUrl;
        LOGGER.info("准备播放 {}", mediaUrl);
        player = new McediaDecoder();

        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.submit(()->{
                var mcplayer =Minecraft.getInstance().player;
                if (mcplayer == null) { return; }

                if (mediaUrl.startsWith("https://media.zenoxs.cn/")) {
                    player.load(new PlayConfiguration(mediaUrl));
                }
                else if (mediaUrl.startsWith("https://live.bilibili.com/")) {
                    var realUrl = BiliBiliLiveFetcher.fetch(mediaUrl);
                    if (realUrl == null) {
                        mcplayer.displayClientMessage(Component.literal("无法解析: " + mediaUrl), false);
                        return;
                    }player.load(new PlayConfiguration(realUrl));
                }
                else if (mediaUrl.startsWith("https://www.bilibili.com/")) {
                    var realUrl = BiliBiliVideoFetcher.fetch(mediaUrl);
                    if (realUrl == null) {
                        mcplayer.displayClientMessage(Component.literal("无法解析: " + mediaUrl), false);
                        return;
                    }
                    player.load(new PlayConfiguration(realUrl));

                }else if (mediaUrl.startsWith("https://v.douyin.com/")) {
                    var realUrl = DouyinVideoFetcher.fetch(mediaUrl);
                    if (realUrl == null) {
                        mcplayer.displayClientMessage(Component.literal("无法解析: " + mediaUrl), false);
                        return;
                    }
                    player.load(new PlayConfiguration(realUrl));
                }

                else {
                    mcplayer.displayClientMessage(Component.literal("不支持的视频: "+ mediaUrl), false);
                    return;
                }
                try{
                    mcplayer.displayClientMessage(Component.literal("正在播放: " + mediaUrl), false);
                    player.play();
                }
                catch (Exception e){
                    LOGGER.warn("播放失败", e);
                    mcplayer.displayClientMessage(Component.literal("播放失败"), false);
                }

            });
        }
    }

    public void stop() {
        var pre = player;
        playingUrl = null;
        if (player == null)
            return;

        LOGGER.info("停止播放");
        this.player = null;

        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.submit(pre::close);
        }
    }

}

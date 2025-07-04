package top.tobyprime.mcedia.client;

import com.mojang.blaze3d.vertex.*;
import top.tobyprime.mcedia.core.InputHelper;
import top.tobyprime.mcedia.core.MediaDecoder;
import top.tobyprime.mcedia.core.PlayConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.WritableBookItem;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.McediaDecoder;

import java.util.List;
import java.util.concurrent.Executors;

public class PlayerAgent {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAgent.class);
    private final ArmorStand entity;
    private @Nullable McediaDecoder player;


    public PlayerAgent(ArmorStand entity) {
        LOGGER.info("在 {} 新增了一个 Mcdia Player", entity.position());
        this.entity = entity;
    }

    public static <T> T tryGet(List<T> list, int index) {
        return (index >= 0 && index < list.size()) ? list.get(index) : null;
    }

    public void updateInputUrl(String url) {
        if (url == null) {
            play(null);
            return;
        }
        if (url.startsWith("https://www.bilibili.com/")) {
            play(InputHelper.resolveBilibili(url));
        } else if (url.equals("CCTV")) {
            play("http://120.196.232.124:8088/rrs03.hw.gmcc.net/PLTV/651/224/3221226635/1.m3u8");
        }

    }

    public void tick() {
        var item = entity.getItemInHand(InteractionHand.MAIN_HAND);

        if (!(item.getItem() instanceof WritableBookItem)) {
            this.play(null);
            return;
        }
        var components = item.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (components == null) return;
        var pages = components.getPages(Minecraft.getInstance().isTextFilteringEnabled()).toList();

        updateInputUrl(tryGet(pages, 0));
    }


    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i) {
        if (player == null) {
            return;
        }
        var size = state.scale;
        var volume = Math.abs(state.leftArmPose.getX() / 60);
        player.setAudioVolume(volume);
        if (i < 0) {
            i = 5;
        }
        player.setAudioPos(((float) state.x), ((float) state.y), ((float) state.z));
        poseStack.pushPose();

        poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.yRot), 0, 0));
        poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.headPose.getX()), (float) Math.toRadians(-state.headPose.getY()), (float) Math.toRadians(-state.headPose.getZ())));
        poseStack.translate(0, 0 + 1 * state.scale, 0 + 0.6 * state.scale);
        poseStack.scale(size, size, size);
        VertexConsumer consumer = player.createBuffer(bufferSource);

        var matrix = poseStack.last().pose();

        float halfW = (float) (player.getAspect());

        consumer.addVertex(matrix, -halfW, -1, 0).setLight(i).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, -1, 0).setLight(i).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, 1, 0).setLight(i).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, -halfW, 1, 0).setLight(i).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);

        poseStack.popPose();

        // 将GL中的texture写入rendered.png以测试
    }

    private final Object playerLock = new Object();
    private volatile String playingUrl = null;

    public void play(@Nullable String mediaUrl) {
        synchronized (this) {
            if (mediaUrl == null) {
                stop();
                return;
            }
            if (player != null) {
                if (player.getConfig().getInputUrl().equals(mediaUrl)) return;
                stop();
            }
            LOGGER.info("准备播放 {}", mediaUrl);
            player = new McediaDecoder();
        }
        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.submit(()->{
                player.load(new PlayConfiguration(mediaUrl));
                player.play();
            });
        }
    }

    public void stop() {
        var pre = player;
        if (player ==null) return;

        synchronized (this){
            LOGGER.info("停止播放");
            this.player = null;
        }

        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.submit(pre::close);
        }
    }

}
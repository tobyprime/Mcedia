package top.tobyprime.mcedia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.component.WritableBookContent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.AudioSource;
import top.tobyprime.mcedia.core.DecoderConfiguration;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.video_fetcher.VideoUrlProcessor;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class PlayerAgent {
    private static final ResourceLocation idleScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAgent.class);
    private final ArmorStand entity;
    public String playingUrl;
    WritableBookContent preOffHandBookComponent = null;
    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private float scale = 1;
    private float audioOffsetX = 0;
    private float audioOffsetY = 0;
    private float audioOffsetZ = 0;

    private float audioMaxVolume = 5f;
    private final MediaPlayer player;
    private final AudioSource audioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);
    private final VideoTexture texture  = new VideoTexture(ResourceLocation.fromNamespaceAndPath("mcedia","player_"+hashCode()));

    private @Nullable CompletableFuture<?> currentPlayFuture;

    private float audioRangeMin = 2;
    private float audioRangeMax = 500;

    public void updateInputUrl(String url) {
        if (url == playingUrl) {return;}
        if (url == null) {
            open(null);
            return;
        }
        open(url);
    }

    public void resetOffset() {
        this.offsetX = 0;
        this.offsetY = 0;
        this.offsetZ = 0;
        this.scale = 1;
    }


    public void resetAudioOffset() {
        this.audioOffsetX = 0;
        this.audioOffsetY = 0;
        this.audioOffsetZ = 0;
        this.audioMaxVolume = 5;
        this.audioRangeMin = 2;
        this.audioRangeMax = 500;
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

    public void updateAudioOffset(String config) {
        try {
            var vars = config.split("\n");
            audioOffsetX = Float.parseFloat(vars[0]);
            audioOffsetY = Float.parseFloat(vars[1]);
            audioOffsetZ = Float.parseFloat(vars[2]);
            audioMaxVolume = Float.parseFloat(vars[3]);
            audioRangeMin = Float.parseFloat(vars[4]);
            audioRangeMax = Float.parseFloat(vars[5]);

        } catch (Exception ignored) {
        }
    }


    public PlayerAgent(ArmorStand entity) {
        LOGGER.info("在 {} 新增了一个 Mcdia Player", entity.position());
        this.entity = entity;
        player = new MediaPlayer();
        player.setDecoderConfiguration(new DecoderConfiguration(new DecoderConfiguration.Builder()));
        player.bindTexture(texture);
        player.bindAudioSource(audioSource);
    }


    public long getServerDuration() {
        var args = entity.getMainHandItem().getDisplayName().getString().split(":");
        try {
            var duration = System.currentTimeMillis() - Long.parseLong(args[1].substring(0, args[1].length() - 1));
            LOGGER.info("从 {} 开始播放", duration);
            if (duration < 1000) {
                return 0;
            }
            return duration * 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void update() {
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

    private float halfW = 1.777f;

    public void renderScreen(PoseStack poseStack, MultiBufferSource bufferSource, int i) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull((player.getMedia() != null) ? this.texture.getResourceLocation() : idleScreen));

        var matrix = poseStack.last().pose();

        consumer.addVertex(matrix, -halfW, -1, 0).setLight(i).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, -1, 0).setLight(i).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, 1, 0).setLight(i).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, -halfW, 1, 0).setLight(i).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
    }

    public void renderProgressBar(PoseStack poseStack, MultiBufferSource bufferSource, float progress, int i) {
        // 绘制进度条，黑色为底，白色为进度
        // 进度条参数
        float barHeight = (float) 1 / 50;
        float barY = -1;
        float barLeft = -halfW;
        float barRight = halfW;
        float barBottom = barY - barHeight;

        // 画底色（黑色）
        VertexConsumer black = bufferSource.getBuffer(RenderType.debugQuads());
        int blackColor = 0xFF000000;
        black.addVertex(poseStack.last().pose(), barLeft, barBottom, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barRight, barBottom, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barRight, barY, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barLeft, barY, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);

        // 画进度（白色）
        float progressRight = barLeft + (barRight - barLeft) * Math.max(0, Math.min(progress, 1));
        int whiteColor = 0xFFFFFFFF;
        if (progress > 0) {
            VertexConsumer white = bufferSource.getBuffer(RenderType.debugQuads());
            white.addVertex(poseStack.last().pose(), barLeft, barBottom, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), progressRight, barBottom, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), progressRight, barY, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), barLeft, barY, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
        }
    }

    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i) {
        var size = state.scale * scale;
        var audioOffsetRotated = new Vector3f(audioOffsetX, audioOffsetY, audioOffsetZ).rotateY(state.yRot);
        var volumeFactor = 1f;
        if (state.leftArmPose.x() < 0) {
            volumeFactor = (360.0f + state.leftArmPose.x()) / 360;
        } else {
            volumeFactor = (state.leftArmPose.x()) / 360;
        }
        var volume = volumeFactor * audioMaxVolume;

        synchronized (player){
            if (player.getMedia() != null) {
                this.player.getMedia().uploadVideo();
                halfW = player.getMedia().getAspectRatio();
            } else {
                halfW = 1.777f;
            }
        }

        audioSource.setVolume(volume);
        audioSource.setRange(audioRangeMin, audioRangeMax);
        if (i < 0) {
            i = 5;
        }
        audioSource.setPos(((float) state.x + audioOffsetRotated.x), ((float) state.y + audioOffsetRotated.y), ((float) state.z + audioOffsetRotated.z));
        poseStack.pushPose();

        poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.yRot), 0, 0));
        poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.headPose.x()), (float) Math.toRadians(-state.headPose.y()), (float) Math.toRadians(-state.headPose.z())));
        poseStack.translate(offsetX, offsetY + 1.02 * state.scale, offsetZ + 0.6 * state.scale);
        poseStack.scale(size, size, size);

        renderScreen(poseStack, bufferSource, i);
        renderProgressBar(poseStack, bufferSource, player.getProgress(), i);
        poseStack.popPose();

    }

    public void tick() {
        update();
    }

    public void open(@Nullable String mediaUrl) {
        if (mediaUrl == null) {
            close();
            return;
        }
        var poster = Arrays.stream(entity.getMainHandItem().getDisplayName().getString().substring(1).split(":")).findFirst().orElse("未知");
        Mcedia.msgToPlayer(poster + "点播: " + mediaUrl);

        playingUrl = mediaUrl;
        LOGGER.info("准备播放 {}", mediaUrl);

        currentPlayFuture = player.openAsync(() -> VideoUrlProcessor.Process(mediaUrl)).exceptionally((e) -> {
            LOGGER.warn("打开视频失败", e);
            Mcedia.msgToPlayer("无法解析或播放: " + mediaUrl);
            throw new RuntimeException(e);
        }).thenRun(() -> {
            player.play();
            LOGGER.info("seek to {} % {}/{}", (float) getServerDuration() / player.getMedia().getLengthUs(), getServerDuration(), player.getMedia().getLengthUs());
            player.seek(getServerDuration());
        }).exceptionally(e -> {
            LOGGER.warn("播放视频失败", e);
            Mcedia.msgToPlayer("无法解析或播放: " + mediaUrl);
            throw new RuntimeException(e);
        });
    }

    public void close() {
        player.closeAsync();
    }

}

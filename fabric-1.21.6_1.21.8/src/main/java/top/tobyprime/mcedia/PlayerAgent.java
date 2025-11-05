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
import top.tobyprime.mcedia.core.Media;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.provider.MediaProviderRegistry;

import java.util.Arrays;
import java.util.Objects;

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
    @Nullable
    private VideoTexture texture = null;
    private float audioRangeMin = 2;
    private float audioRangeMax = 500;
    String inputContent = null;
    public float speed = 1;
    private String desiredQuality = "自动";

    public PlayerAgent(ArmorStand entity) {
        LOGGER.info("在 {} 注册了一个 Mcedia Player 实例", entity.position());
        this.entity = entity;
        player = new MediaPlayer();
        player.setDecoderConfiguration(new DecoderConfiguration(new DecoderConfiguration.Builder()));
        player.bindAudioSource(audioSource);
    }

    public void initializeGraphics() {
        if (this.texture == null) {
            this.texture = new VideoTexture(ResourceLocation.fromNamespaceAndPath("mcedia", "player_" + hashCode()));
            player.bindTexture(this.texture);
        }
    }

    public static long parseToMicros(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            throw new IllegalArgumentException("Time string cannot be null or empty");
        }
        String[] parts = timeStr.split(":");
        long totalSeconds = 0;
        try {
            if (parts.length == 1) totalSeconds = Long.parseLong(parts[0]) * 3600;
            else if (parts.length == 2) totalSeconds = Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60;
            else if (parts.length == 3) totalSeconds = Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
            else throw new IllegalArgumentException("Invalid time format: " + timeStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in time string: " + timeStr, e);
        }
        return totalSeconds * 1_000_000L;
    }

    public void updateInputUrl(String content) {
        try {
            if (Objects.equals(content, this.inputContent)) {
                return;
            }
            this.inputContent = content;

            if (content == null || content.isBlank()) {
                open(null);
                return;
            }

            var args = content.split("\n");
            var url = args[0].trim();

            if (!url.toLowerCase().startsWith("http")) {
                LOGGER.warn("无效的输入URL: '{}'，将停止播放。", url);
                open(null); // 调用 open(null) 来安全地停止当前播放
                return;
            }

            if (Objects.equals(url, playingUrl)) {
                return;
            }
            open(url);
        } catch (Exception e) {
            LOGGER.error("Failed to update input URL", e);
        }
    }

    public void resetOffset() { this.offsetX = 0; this.offsetY = 0; this.offsetZ = 0; this.scale = 1; }
    public void resetAudioOffset() { this.audioOffsetX = 0; this.audioOffsetY = 0; this.audioOffsetZ = 0; this.audioMaxVolume = 5; this.audioRangeMin = 2; this.audioRangeMax = 500; }
    public void updateOther(String flags) { this.player.setLooping(flags.contains("looping")); }
    public void updateOffset(String offset) { try { var vars = offset.split("\n"); offsetX = Float.parseFloat(vars[0]); offsetY = Float.parseFloat(vars[1]); offsetZ = Float.parseFloat(vars[2]); scale = Float.parseFloat(vars[3]); } catch (Exception ignored) {} }
    public void updateAudioOffset(String config) { try { var vars = config.split("\n"); audioOffsetX = Float.parseFloat(vars[0]); audioOffsetY = Float.parseFloat(vars[1]); audioOffsetZ = Float.parseFloat(vars[2]); audioMaxVolume = Float.parseFloat(vars[3]); audioRangeMin = Float.parseFloat(vars[4]); audioRangeMax = Float.parseFloat(vars[5]); } catch (Exception ignored) {} }

    public void updateQuality(String quality) {
        if (quality == null || quality.isBlank()) {
            this.desiredQuality = "自动";
        } else {
            this.desiredQuality = quality.trim();
        }
    }

    private long getBaseDuration() {
        if (inputContent == null) return 0;
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

    public long getServerDuration() {
        try {
            String displayName = entity.getMainHandItem().getDisplayName().getString();
            var nameParts = displayName.split(":");
            if (nameParts.length > 1) {
                long startTime = Long.parseLong(nameParts[1].substring(0, nameParts[1].length() - 1));
                long duration = System.currentTimeMillis() - startTime;
                LOGGER.info("从 {} 开始播放", duration);
                return duration < 1000 ? 0 : duration * 1000;
            }
        } catch (Exception e) { return 0; }
        return 0;
    }

    public long getDuration() { return getBaseDuration() + getServerDuration(); }

    public void update() {
        try {
            var mainHandBook = entity.getItemInHand(InteractionHand.MAIN_HAND);
            if (mainHandBook.getItem() instanceof WritableBookItem) {
                var components = mainHandBook.get(DataComponents.WRITABLE_BOOK_CONTENT);
                updateInputUrl(components != null ? components.getPages(Minecraft.getInstance().isTextFilteringEnabled()).findFirst().orElse(null) : null);
            } else {
                updateInputUrl(null); // 明确传递null
            }

            var offHandBook = entity.getItemInHand(InteractionHand.OFF_HAND);
            if (offHandBook.getItem() instanceof WritableBookItem) {
                var components = offHandBook.get(DataComponents.WRITABLE_BOOK_CONTENT);
                if (!Objects.equals(components, preOffHandBookComponent)) {
                    preOffHandBookComponent = components;
                    if (components != null) {
                        var pages = components.getPages(Minecraft.getInstance().isTextFilteringEnabled()).toList();
                        if (!pages.isEmpty()) updateOffset(pages.get(0));
                        if (pages.size() > 1) updateAudioOffset(pages.get(1));
                        if (pages.size() > 2) updateOther(pages.get(2));
                        if (pages.size() > 3) {
                            updateQuality(pages.get(3));
                        } else {
                            updateQuality("自动");
                        }
                    }
                }
            } else {

                if (preOffHandBookComponent != null) {
                    preOffHandBookComponent = null;
                    resetOffset();
                    resetAudioOffset();
                    updateQuality("自动");
                }
            }
        } catch (Exception ignored) {}
    }

    private float halfW = 1.777f;
    public void renderScreen(PoseStack poseStack, MultiBufferSource bufferSource, int i) { if (texture == null) return; VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull((player.getMedia() != null) ? this.texture.getResourceLocation() : idleScreen)); var matrix = poseStack.last().pose(); consumer.addVertex(matrix, -halfW, -1, 0).setLight(i).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1); consumer.addVertex(matrix, halfW, -1, 0).setLight(i).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1); consumer.addVertex(matrix, halfW, 1, 0).setLight(i).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1); consumer.addVertex(matrix, -halfW, 1, 0).setLight(i).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1); }
    public void renderProgressBar(PoseStack poseStack, MultiBufferSource bufferSource, float progress, int i) { float barHeight = 1f / 50f, barY = -1f, barLeft = -halfW, barRight = halfW, barBottom = barY - barHeight; VertexConsumer black = bufferSource.getBuffer(RenderType.debugQuads()); int blackColor = 0xFF000000; black.addVertex(poseStack.last().pose(), barLeft, barBottom, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1); black.addVertex(poseStack.last().pose(), barRight, barBottom, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1); black.addVertex(poseStack.last().pose(), barRight, barY, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1); black.addVertex(poseStack.last().pose(), barLeft, barY, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1); float progressRight = barLeft + (barRight - barLeft) * Math.max(0, Math.min(progress, 1)); if (progress > 0) { VertexConsumer white = bufferSource.getBuffer(RenderType.debugQuads()); int whiteColor = 0xFFFFFFFF; white.addVertex(poseStack.last().pose(), barLeft, barBottom, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1); white.addVertex(poseStack.last().pose(), progressRight, barBottom, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1); white.addVertex(poseStack.last().pose(), progressRight, barY, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1); white.addVertex(poseStack.last().pose(), barLeft, barY, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1); } }
    public float rotationToFactor(float rotation) { return rotation < 0 ? -rotation / 360f : (360.0f - rotation) / 360f; }

    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i) {
        var size = state.scale * scale;
        var audioOffsetRotated = new Vector3f(audioOffsetX, audioOffsetY, audioOffsetZ).rotateY(state.yRot);
        var volumeFactor = 1 - rotationToFactor(state.leftArmPose.x());
        var speedFactor = rotationToFactor(state.leftArmPose.y());
        speed = speedFactor < 0.1f ? 1f : (speedFactor > 0.5f ? 1f - (1f - speedFactor) * 2f : (speedFactor - 0.1f) / 0.4f * 8f);
        player.setSpeed(speed);
        var volume = volumeFactor * audioMaxVolume;
        synchronized (player) {
            Media media = player.getMedia();
            if (media != null) {
                media.uploadVideo();
                if (media.getHeight() > 0) { // 防止除零错误
                    halfW = media.getAspectRatio();
                }
            } else {
                halfW = 1.777f;
            }
        }
        audioSource.setVolume(volume);
        audioSource.setRange(audioRangeMin, audioRangeMax);
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
        if (Objects.equals(mediaUrl, playingUrl)) {
            return;
        }
        playingUrl = mediaUrl;

        if (mediaUrl == null) {
            close();
            return;
        }

        long duration = getDuration();
        String displayName = entity.getMainHandItem().getDisplayName().getString();
        var poster = displayName.length() > 1 && displayName.contains(":") ?
                Arrays.stream(displayName.substring(1).split(":")).findFirst().orElse("未知") : "未知";
        Mcedia.msgToPlayer(poster + "点播: " + mediaUrl);
        LOGGER.info("准备播放 {}，清晰度: {}", mediaUrl, desiredQuality);

        player.openAsyncWithVideoInfo(() -> {
                    try {
                        String bilibiliCookie = McediaConfig.BILIBILI_COOKIE;
                        return MediaProviderRegistry.getInstance().resolve(mediaUrl, bilibiliCookie, this.desiredQuality);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, () -> McediaConfig.BILIBILI_COOKIE)
                .exceptionally(e -> {
                    LOGGER.warn("打开视频失败", e);
                    Mcedia.msgToPlayer("无法解析或播放: " + mediaUrl);
                    return null;
                }).thenRun(() -> {
                    player.play();
                    player.seek(duration);
                    player.setSpeed(speed);
                }).exceptionally(e -> {
                    LOGGER.warn("播放视频失败", e);
                    Mcedia.msgToPlayer("无法解析或播放: " + mediaUrl);
                    return null;
                });
    }

    public void close() {
        playingUrl = null;
        player.closeAsync();
    }
}
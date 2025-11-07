package top.tobyprime.mcedia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.AudioSource;
import top.tobyprime.mcedia.BilibiliAuthRequiredException;
import top.tobyprime.mcedia.core.DecoderConfiguration;
import top.tobyprime.mcedia.core.Media;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.provider.MediaProviderRegistry;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlayerAgent {
    private static final ResourceLocation idleScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAgent.class);

    private final ArmorStand entity;
    public String playingUrl;
    private ItemStack preOffHandItemStack = ItemStack.EMPTY;

    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private float scale = 1;
    private float audioOffsetX = 0, audioOffsetY = 0, audioOffsetZ = 0;
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

    private long lastSeekTime = 0;
    private static final long SEEK_COOLDOWN_MS = 200;
    private long timestampFromUrlUs = 0;
//    private float lastSeekBodyYRot = Float.NEGATIVE_INFINITY;

    private volatile boolean isLoopingInProgress = false;

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
            if (parts.length == 1) {
                totalSeconds = Long.parseLong(parts[0]) * 3600;
            } else if (parts.length == 2) {
                totalSeconds = Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60;
            } else if (parts.length == 3) {
                totalSeconds = Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
            } else {
                throw new IllegalArgumentException("Invalid time format: " + timeStr);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in time string: " + timeStr, e);
        }
        return totalSeconds * 1_000_000L;
    }

    public void updateInputUrl(String firstPageContent) {
        try {
            if (Objects.equals(firstPageContent, this.inputContent)) {
                return;
            }
            this.inputContent = firstPageContent;
            if (firstPageContent == null || firstPageContent.isBlank()) {
                open(null);
                return;
            }
            var args = firstPageContent.split("\n");
            var url = args[0].trim();
            if (!url.toLowerCase().startsWith("http")) {
                LOGGER.warn("无效的输入URL: '{}'，将停止播放。", url);
                open(null);
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

    @Nullable
    private List<String> getBookPages(ItemStack bookStack) {
        boolean isTextFilteringEnabled = Minecraft.getInstance().isTextFilteringEnabled();
        if (bookStack.is(Items.WRITABLE_BOOK)) {
            WritableBookContent content = bookStack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (content != null) {
                return content.getPages(isTextFilteringEnabled).toList();
            }
        } else if (bookStack.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = bookStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null) {
                return content.getPages(isTextFilteringEnabled).stream()
                        .map(Component::getString)
                        .collect(Collectors.toList());
            }
        }
        return null;
    }

    public void update() {
        try {
            ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
            List<String> mainHandPages = getBookPages(mainHandItem);
            updateInputUrl(mainHandPages != null && !mainHandPages.isEmpty() ? mainHandPages.get(0) : null);

            ItemStack offHandItem = entity.getItemInHand(InteractionHand.OFF_HAND);
            if (!ItemStack.matches(offHandItem, preOffHandItemStack)) {
                preOffHandItemStack = offHandItem.copy();
                List<String> offHandPages = getBookPages(offHandItem);
                if (offHandPages != null) {
                    if (!offHandPages.isEmpty()) updateOffset(offHandPages.get(0));
                    if (offHandPages.size() > 1) updateAudioOffset(offHandPages.get(1));
                    if (offHandPages.size() > 2) updateOther(offHandPages.get(2));
                    if (offHandPages.size() > 3) {
                        updateQuality(offHandPages.get(3));
                    } else {
                        updateQuality("自动");
                    }
                } else {
                    resetOffset();
                    resetAudioOffset();
                    updateQuality("自动");
                }
            }
        } catch (Exception ignored) {
        }
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
            LOGGER.debug("获取base duration失败", e);
        }
        return duration;
    }

    public long getServerDuration() {
        try {
            String displayName = entity.getMainHandItem().getDisplayName().getString();
            var nameParts = displayName.split(":");
            if (nameParts.length > 1) {
                long startTime = Long.parseLong(nameParts[1].trim());
                long duration = System.currentTimeMillis() - startTime;
                return duration < 1000 ? 0 : duration * 1000;
            }
        } catch (Exception e) {
            LOGGER.warn("无法从物品名称解析服务器时长: '{}'", entity.getMainHandItem().getDisplayName().getString(), e);
            return 0;
        }
        return 0;
    }

    public long getDuration() {
        return getBaseDuration() + getServerDuration() + timestampFromUrlUs;
    }

    private long parseBiliTimestampToUs(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return 0;
        }
        long totalSeconds = 0;
        Pattern h = Pattern.compile("(\\d+)h");
        Pattern m = Pattern.compile("(\\d+)m");
        Pattern s = Pattern.compile("(\\d+)s");
        Matcher hMatcher = h.matcher(timestamp);
        if (hMatcher.find()) totalSeconds += Long.parseLong(hMatcher.group(1)) * 3600;
        Matcher mMatcher = m.matcher(timestamp);
        if (mMatcher.find()) totalSeconds += Long.parseLong(mMatcher.group(1)) * 60;
        Matcher sMatcher = s.matcher(timestamp);
        if (sMatcher.find()) totalSeconds += Long.parseLong(sMatcher.group(1));
        return totalSeconds * 1_000_000L;
    }

    public void tick() {
        update();

        Media currentMedia = player.getMedia();
        if (currentMedia != null && !currentMedia.isLiveStream() && currentMedia.isEnded()) {
            // 检查是否需要循环，并确保没有正在进行的循环操作
            if (player.looping && !this.isLoopingInProgress) {
                // 设置标志位，防止重复触发
                this.isLoopingInProgress = true;
                LOGGER.info("媒体播放结束。正在重新开始循环...");
                // 使用我们之前的可靠方法来重启播放
                startPlayback(true);
            }
        }
    }

    private float halfW = 1.777f;

    public void renderScreen(PoseStack poseStack, MultiBufferSource bufferSource, int i) {
        if (texture == null) return;
        ResourceLocation screenTexture = (player.getMedia() != null) ? this.texture.getResourceLocation() : idleScreen;
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(screenTexture));
        var matrix = poseStack.last().pose();

        consumer.addVertex(matrix, -halfW, -1, 0).setLight(i).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, -1, 0).setLight(i).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, halfW, 1, 0).setLight(i).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
        consumer.addVertex(matrix, -halfW, 1, 0).setLight(i).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1);
    }

    public void renderProgressBar(PoseStack poseStack, MultiBufferSource bufferSource, float progress, int i) {
        float barHeight = 1f / 50f;
        float barY = -1f;
        float barLeft = -halfW;
        float barRight = halfW;
        float barBottom = barY - barHeight;

        VertexConsumer black = bufferSource.getBuffer(RenderType.debugQuads());
        int blackColor = 0xFF000000;
        black.addVertex(poseStack.last().pose(), barLeft, barBottom, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barRight, barBottom, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barRight, barY, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);
        black.addVertex(poseStack.last().pose(), barLeft, barY, 0).setColor(blackColor).setLight(i).setNormal(0, 0, 1);

        float progressRight = barLeft + (barRight - barLeft) * Math.max(0, Math.min(progress, 1));
        if (progress > 0) {
            VertexConsumer white = bufferSource.getBuffer(RenderType.debugQuads());
            int whiteColor = 0xFFFFFFFF;
            white.addVertex(poseStack.last().pose(), barLeft, barBottom, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), progressRight, barBottom, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), progressRight, barY, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
            white.addVertex(poseStack.last().pose(), barLeft, barY, 1e-3f).setColor(whiteColor).setLight(i).setNormal(0, 0, 1);
        }
    }

    public float rotationToFactor(float rotation) {
        return rotation < 0 ? -rotation / 360f : (360.0f - rotation) / 360f;
    }

    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i) {
        var size = state.scale * scale;
        var audioOffsetRotated = new Vector3f(audioOffsetX, audioOffsetY, audioOffsetZ).rotateY(state.yRot);
        var volumeFactor = 1 - rotationToFactor(state.leftArmPose.x());
        var speedFactor = rotationToFactor(state.leftArmPose.y());
        speed = speedFactor < 0.1f ? 1f : (speedFactor > 0.5f ? 1f - (1f - speedFactor) * 2f : (speedFactor - 0.1f) / 0.4f * 8f);

        Media currentMedia = player.getMedia();
//        if (currentMedia != null && !currentMedia.isLiveStream()) {
//            boolean isSeekingActivated = state.rightArmPose.x() < -80.0f;
//            if (isSeekingActivated) {
//                float currentBodyYRot = state.bodyPose.y();
//
//                // 只有当身体角度发生变化时 (设置一个小的阈值以忽略抖动)
//                if (Math.abs(currentBodyYRot - lastSeekBodyYRot) > 0.1f) {
//                    long currentTime = System.currentTimeMillis();
//                    if (currentTime - lastSeekTime > SEEK_COOLDOWN_MS) {
//                        float progress = (currentBodyYRot + 180.0f) / 360.0f;
//                        long totalDurationUs = currentMedia.getLengthUs();
//                        long targetUs = (long) (totalDurationUs * progress);
//
//                        player.seek(targetUs);
//                        player.play();
//                        lastSeekTime = currentTime;
//                        // 在执行 seek 后，立刻记住当前的角度
//                        lastSeekBodyYRot = currentBodyYRot;
//                    }
//                }
//            } else {
//                // 如果没有激活拖动，就重置“记忆”，
//                // 这样下一次激活时就能立刻响应第一次拖动。
//                lastSeekBodyYRot = Float.NEGATIVE_INFINITY;
//            }
//        }
        player.setSpeed(speed);
        var volume = volumeFactor * audioMaxVolume;

        synchronized (player) {
            Media media = player.getMedia();
            if (media != null) {
                media.uploadVideo();
                if (media.getHeight() > 0) {
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

    public void open(@Nullable String mediaUrl) {
        if (Objects.equals(mediaUrl, playingUrl)) {
            return;
        }

        this.timestampFromUrlUs = 0;
        if (mediaUrl != null) {
            Pattern pattern = Pattern.compile("[?&]t=([^&]+)");
            Matcher matcher = pattern.matcher(mediaUrl);
            if (matcher.find()) {
                String timestampStr = matcher.group(1);
                this.timestampFromUrlUs = parseBiliTimestampToUs(timestampStr);
                LOGGER.info("从URL中解析到空降时间: {} us", this.timestampFromUrlUs);
            }
        }

        playingUrl = mediaUrl;
        this.isLoopingInProgress = false;
        startPlayback(false);
    }

    private void startPlayback(boolean isLooping) {
        if (playingUrl == null) {
            close();
            return;
        }

        long duration = isLooping ? 0 : getDuration();
        LOGGER.info(isLooping ? "Looping playback..." : "准备播放 {}，清晰度: {}，起始时间: {} us", playingUrl, desiredQuality, duration);

        final String finalMediaUrl = playingUrl;
        CompletableFuture<VideoInfo> videoInfoFuture = player.openAsyncWithVideoInfo(() -> {
            try {
                return MediaProviderRegistry.getInstance().resolve(finalMediaUrl, McediaConfig.BILIBILI_COOKIE, this.desiredQuality);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, () -> McediaConfig.BILIBILI_COOKIE);

        videoInfoFuture.handle((videoInfo, throwable) -> {
            if (throwable != null) {
                Throwable cause = throwable.getCause();
                LOGGER.warn("打开视频失败", throwable.getCause());
                if (cause instanceof BilibiliAuthRequiredException) {
                    // 如果是需要登录的异常
                    Mcedia.msgToPlayer("§e[Mcedia] §f该视频需要登录或会员。请使用 §a/mcedia login §f登录。");
                    LOGGER.warn("播放失败: {}", cause.getMessage());
                } else {
                    LOGGER.warn("打开视频失败", cause);
                }
                if (!isLooping) {
                    Mcedia.msgToPlayer("§c[Mcedia] §f无法解析或播放: " + finalMediaUrl);
                }
                this.isLoopingInProgress = false;
            } else {
                // 播放成功
                if (!isLooping) {
                    Style clickableStyle = Style.EMPTY
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(finalMediaUrl)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击打开原视频链接")));
                    Component message = Component.literal("§a[Mcedia] §f正在播放: ")
                            .append(Component.literal(videoInfo.getTitle()).withStyle(clickableStyle.withColor(ChatFormatting.YELLOW)))
                            .append(Component.literal(" §f- "))
                            .append(Component.literal(videoInfo.getAuthor()).withStyle(clickableStyle.withColor(ChatFormatting.AQUA)));
                    Mcedia.msgToPlayer(message);
                }
                if (duration > 100_000L) { // 100_000 us = 100 ms
                    LOGGER.info("检测到非零起始时间，正在移动到 {} us", duration);
                    player.seek(duration);
                }
                player.play();
                player.setSpeed(speed);

                this.isLoopingInProgress = false;
            }
            return null;
        }).exceptionally(e -> {
            LOGGER.warn("播放视频时发生未知错误", e);
            if (!isLooping) {
                Mcedia.msgToPlayer("§c[Mcedia] §f播放时发生错误: " + finalMediaUrl);
            }
            this.isLoopingInProgress = false;
            return null;
        });
    }

    public void close() {
        playingUrl = null;
        isLoopingInProgress = false;
        player.closeAsync();
    }

    public void closeSync() {
        playingUrl = null;
        isLoopingInProgress = false;
        player.closeSync();
    }
}
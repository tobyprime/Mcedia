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
import top.tobyprime.mcedia.core.*;
import top.tobyprime.mcedia.BilibiliAuthRequiredException;
import top.tobyprime.mcedia.provider.MediaProviderRegistry;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlayerAgent {
    private static final ResourceLocation idleScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAgent.class);
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");

    private final ArmorStand entity;
    private final MediaPlayer player;
    private final VideoCacheManager cacheManager;
    private final AudioSource audioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);

    @Nullable
    private VideoTexture texture = null;
    public String playingUrl;
    private String inputContent = null;
    private ItemStack preOffHandItemStack = ItemStack.EMPTY;

    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private float scale = 1;
    private float audioOffsetX = 0, audioOffsetY = 0, audioOffsetZ = 0;
    private float audioMaxVolume = 5f;
    private float audioRangeMin = 2;
    private float audioRangeMax = 500;
    public float speed = 1;
    private String desiredQuality = "自动";
    private boolean shouldCacheForLoop = false;
    private volatile boolean isLoopingInProgress = false;
    private final AtomicBoolean isTextureReady = new AtomicBoolean(false);

    public PlayerAgent(ArmorStand entity) {
        LOGGER.info("在 {} 注册了一个 Mcedia Player 实例", entity.position());
        this.entity = entity;
        this.cacheManager = new VideoCacheManager(Mcedia.getCacheDirectory());
        this.player = new MediaPlayer();
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
        int len = parts.length;
        int hours = 0, minutes = 0, seconds = 0;
        try {
            if (len == 1) hours = Integer.parseInt(parts[0]);
            else if (len == 2) { hours = Integer.parseInt(parts[0]); minutes = Integer.parseInt(parts[1]); }
            else if (len == 3) { hours = Integer.parseInt(parts[0]); minutes = Integer.parseInt(parts[1]); seconds = Integer.parseInt(parts[2]); }
            else throw new IllegalArgumentException("Invalid time format: " + timeStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in time string: " + timeStr, e);
        }
        return (hours * 3600L + minutes * 60L + seconds) * 1_000_000L;
    }

    private long getBaseDuration() {
        long duration = 0;
        try {
            if (inputContent == null) return 0;
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
            var args = entity.getMainHandItem().getDisplayName().getString().split(":");
            var duration = System.currentTimeMillis() - Long.parseLong(args[1].substring(0, args[1].length() - 1));
            if (duration < 1000) return 0;
            return duration * 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    public long getDuration() {
        return getBaseDuration() + getServerDuration();
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
                    updateQuality(offHandPages.size() > 3 ? offHandPages.get(3) : "自动");
                } else {
                    resetOffset();
                    resetAudioOffset();
                    updateQuality("自动");
                }
            }
        } catch (Exception ignored) {}
    }

    public void tick() {
        update();
        Media currentMedia = player.getMedia();
        if (currentMedia != null && currentMedia.isEnded() && player.looping && !this.isLoopingInProgress) {
            this.isLoopingInProgress = true;
            LOGGER.info("媒体播放结束，开始循环...");
            startPlayback(true);
        }
    }

    private void updateInputUrl(String firstPageContent) {
        if (Objects.equals(firstPageContent, this.inputContent)) return;
        this.inputContent = firstPageContent;

        if (firstPageContent == null || firstPageContent.isBlank()) {
            open(null);
            return;
        }

        Matcher matcher = URL_PATTERN.matcher(firstPageContent);
        if (matcher.find()) {
            String url = matcher.group(1);
            if (!Objects.equals(url, playingUrl)) {
                open(url);
            }
        } else if (playingUrl != null) {
            open(null);
        }
    }

    public void open(@Nullable String mediaUrl) {
        if (Objects.equals(mediaUrl, playingUrl)) return;

        cacheManager.cleanup();
        playingUrl = mediaUrl;
        isLoopingInProgress = false;
        startPlayback(false);
    }

    private void startPlayback(boolean isLooping) {
        this.isTextureReady.set(false);
        if (isLooping && shouldCacheForLoop && cacheManager.isCached()) {
            VideoInfo cachedInfo = cacheManager.getCachedVideoInfo();
            if (cachedInfo != null) {
                LOGGER.info("从缓存循环播放...");
                player.closeSync();
                player.openSync(cachedInfo, null);
                player.play();
                player.setSpeed(speed);
                this.isLoopingInProgress = false;
                this.isTextureReady.set(true);
                return;
            }
        }

        if (playingUrl == null) {
            close();
            return;
        }

        player.closeAsync();

        final String finalMediaUrl = playingUrl;

        LOGGER.info(isLooping ? "正在重新加载循环..." : "准备播放 {}...", finalMediaUrl);

        CompletableFuture<VideoInfo> videoInfoFuture = player.openAsyncWithVideoInfo(
                () -> {
                    try {
                        return MediaProviderRegistry.getInstance().resolve(finalMediaUrl, McediaConfig.BILIBILI_COOKIE, this.desiredQuality);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> McediaConfig.BILIBILI_COOKIE, 0
        );

        videoInfoFuture.handle((videoInfo, throwable) -> {
            if (throwable != null) {
                handlePlaybackFailure(throwable, finalMediaUrl, isLooping);
            } else {
                handlePlaybackSuccess(videoInfo, finalMediaUrl, isLooping);
            }
            return null;
        });
    }

    private void handlePlaybackSuccess(VideoInfo videoInfo, String finalMediaUrl, boolean isLooping) {
        Media media = player.getMedia();
        if (media == null) {
            LOGGER.error("视频加载成功但Media对象为空，这是一个严重错误。");
            return;
        }

        if (shouldCacheForLoop && !media.isLiveStream() && !cacheManager.isCached() && !cacheManager.isCaching()) {
            cacheManager.cacheVideoAsync(videoInfo, McediaConfig.BILIBILI_COOKIE)
                    .handle((unused, cacheThrowable) -> {
                        if (cacheThrowable != null) Mcedia.msgToPlayer("§e[Mcedia] §c视频后台缓存失败。");
                        else Mcedia.msgToPlayer("§a[Mcedia] §f视频已缓存，下次循环将从本地播放。");
                        return null;
                    });
        }
        if (media != null && texture != null && media.getWidth() > 0) {
            texture.prepareAndPrewarm(media.getWidth(), media.getHeight(), () -> this.isTextureReady.set(true));
        }
        if (!isLooping) {
            Style style = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(finalMediaUrl)));
            Component msg = Component.literal("§a[Mcedia] §f播放: ").append(Component.literal(videoInfo.getTitle()).withStyle(style.withColor(ChatFormatting.YELLOW)));
            Mcedia.msgToPlayer(msg);
        }

        if (media.isLiveStream()) {
            LOGGER.info("直播流加载成功，直接开始播放。");
        } else {
            long durationToSeek = isLooping ? 0 : getDuration();
            LOGGER.info("视频加载成功，将在 {} us 处开始播放。", durationToSeek);
            if (durationToSeek > 0) {
                player.seek(durationToSeek);
            }
        }

        player.play();
        player.setSpeed(speed);
        this.isLoopingInProgress = false;
    }

    private void handlePlaybackFailure(Throwable throwable, String finalMediaUrl, boolean isLooping) {
        LOGGER.warn("打开视频失败", throwable.getCause());
        if (throwable.getCause() instanceof BilibiliAuthRequiredException) {
            Mcedia.msgToPlayer("§e[Mcedia] §f该视频需要登录或会员。请使用 §a/mcedia login §f登录。");
        } else if (!isLooping) {
            Mcedia.msgToPlayer("§c[Mcedia] §f无法解析或播放: " + finalMediaUrl);
        }
        this.isLoopingInProgress = false;
    }

    private float halfW = 1.777f;
    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i) {
//        if (!this.isTextureReady.get()) return;

        var size = state.scale * scale;
        var audioOffsetRotated = new Vector3f(audioOffsetX, audioOffsetY, audioOffsetZ).rotateY(state.yRot);
        var volumeFactor = 1 - (state.leftArmPose.x() < 0 ? -state.leftArmPose.x() / 360f : (360.0f - state.leftArmPose.x()) / 360f);
        var speedFactor = state.leftArmPose.y() < 0 ? -state.leftArmPose.y() / 360f : (360.0f - state.leftArmPose.y()) / 360f;
        speed = speedFactor < 0.1f ? 1f : (speedFactor > 0.5f ? 1f - (1f - speedFactor) * 2f : (speedFactor - 0.1f) / 0.4f * 8f);
        player.setSpeed(speed);
        var volume = volumeFactor * audioMaxVolume;

        synchronized (player) {
            Media media = player.getMedia();
            if (media != null) {
                media.uploadVideo();
                if (media.getHeight() > 0) halfW = media.getAspectRatio();
            } else halfW = 1.777f;
        }
        audioSource.setVolume(volume);
        audioSource.setRange(audioRangeMin, audioRangeMax);
        audioSource.setPos(((float) state.x + audioOffsetRotated.x), ((float) state.y + audioOffsetRotated.y), ((float) state.z + audioOffsetRotated.z));

        poseStack.pushPose();
        try {
            poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.yRot), 0, 0));
            poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.headPose.x()), (float) Math.toRadians(-state.headPose.y()), (float) Math.toRadians(-state.headPose.z())));
            poseStack.translate(offsetX, offsetY + 1.02 * state.scale, offsetZ + 0.6 * state.scale);
            poseStack.scale(size, size, size);

            renderScreen(poseStack, bufferSource, i);

            if (player.getMedia() != null && this.isTextureReady.get()) {
                renderProgressBar(poseStack, bufferSource, player.getProgress(), i);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private void renderScreen(PoseStack poseStack, MultiBufferSource bufferSource, int i) {
        if (texture == null) return;
        ResourceLocation screenTexture = (player.getMedia() != null && this.isTextureReady.get())
                ? this.texture.getResourceLocation()
                : idleScreen;
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(screenTexture));
        var matrix = poseStack.last().pose();
        consumer.addVertex(matrix, -halfW, -1, 0).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, halfW, -1, 0).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, halfW, 1, 0).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, -halfW, 1, 0).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
    }

    private void renderProgressBar(PoseStack poseStack, MultiBufferSource bufferSource, float progress, int i) {
        float barHeight = 1f / 50f, barY = -1f, barLeft = -halfW, barRight = halfW;
        VertexConsumer black = bufferSource.getBuffer(RenderType.debugQuads());
        black.addVertex(poseStack.last().pose(), barLeft, barY - barHeight, 0).setColor(0xFF000000).setLight(i);
        black.addVertex(poseStack.last().pose(), barRight, barY - barHeight, 0).setColor(0xFF000000).setLight(i);
        black.addVertex(poseStack.last().pose(), barRight, barY, 0).setColor(0xFF000000).setLight(i);
        black.addVertex(poseStack.last().pose(), barLeft, barY, 0).setColor(0xFF000000).setLight(i);
        if (progress > 0) {
            VertexConsumer white = bufferSource.getBuffer(RenderType.debugQuads());
            float progressRight = barLeft + (barRight - barLeft) * Math.max(0, Math.min(progress, 1));
            white.addVertex(poseStack.last().pose(), barLeft, barY - barHeight, 1e-3f).setColor(-1).setLight(i);
            white.addVertex(poseStack.last().pose(), progressRight, barY - barHeight, 1e-3f).setColor(-1).setLight(i);
            white.addVertex(poseStack.last().pose(), progressRight, barY, 1e-3f).setColor(-1).setLight(i);
            white.addVertex(poseStack.last().pose(), barLeft, barY, 1e-3f).setColor(-1).setLight(i);
        }
    }

    public void close() { playingUrl = null; isLoopingInProgress = false; cacheManager.cleanup(); player.closeAsync(); }
    public void closeSync() { playingUrl = null; isLoopingInProgress = false; cacheManager.cleanup(); player.closeSync(); }
    public ArmorStand getEntity() { return this.entity; }
    @Nullable
    private List<String> getBookPages(ItemStack bookStack) {
        boolean isTextFilteringEnabled = Minecraft.getInstance().isTextFilteringEnabled();
        if (bookStack.is(Items.WRITABLE_BOOK)) {
            WritableBookContent content = bookStack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (content != null) return content.getPages(isTextFilteringEnabled).toList();
        } else if (bookStack.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = bookStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null) return content.getPages(isTextFilteringEnabled).stream().map(Component::getString).collect(Collectors.toList());
        }
        return null;
    }
    public void resetOffset() { this.offsetX = 0; this.offsetY = 0; this.offsetZ = 0; this.scale = 1; }
    public void resetAudioOffset() { this.audioOffsetX = 0; this.audioOffsetY = 0; this.audioOffsetZ = 0; this.audioMaxVolume = 5; this.audioRangeMin = 2; this.audioRangeMax = 500; }
    public void updateOther(String flags) { boolean looping = flags.contains("looping"); this.player.setLooping(looping); this.shouldCacheForLoop = looping && McediaConfig.CACHING_ENABLED; if (!looping && cacheManager.isCached()) cacheManager.cleanup(); }
    public void updateQuality(String quality) { this.desiredQuality = (quality == null || quality.isBlank()) ? "自动" : quality.trim(); }
    public void updateOffset(String offset) { try { var vars = offset.split("\n"); offsetX = Float.parseFloat(vars[0]); offsetY = Float.parseFloat(vars[1]); offsetZ = Float.parseFloat(vars[2]); scale = Float.parseFloat(vars[3]); } catch (Exception ignored) {} }
    public void updateAudioOffset(String config) { try { var vars = config.split("\n"); audioOffsetX = Float.parseFloat(vars[0]); audioOffsetY = Float.parseFloat(vars[1]); audioOffsetZ = Float.parseFloat(vars[2]); audioMaxVolume = Float.parseFloat(vars[3]); audioRangeMin = Float.parseFloat(vars[4]); audioRangeMax = Float.parseFloat(vars[5]); } catch (Exception ignored) {} }
}
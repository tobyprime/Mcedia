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
import top.tobyprime.mcedia.provider.IMediaProvider;
import top.tobyprime.mcedia.provider.MediaProviderRegistry;
import top.tobyprime.mcedia.provider.VideoInfo;
import top.tobyprime.mcedia.video_fetcher.UrlExpander;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlayerAgent {
    private static final ResourceLocation idleScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/idle_screen.png");
    private static final ResourceLocation errorScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/error.png");
    private static final ResourceLocation loadingScreen = ResourceLocation.fromNamespaceAndPath("mcedia", "textures/gui/loading.png");
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAgent.class);
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");
    private long timestampFromUrlUs = 0;
    private boolean isPausedByBasePlate = false;
    private final Queue<String> playlist = new LinkedList<>();
    private String currentPlaylistContent = "";
    private int playlistOriginalSize = 0;
    private final AtomicLong playbackToken = new AtomicLong(0);

    private final ArmorStand entity;
    private final MediaPlayer player;
    private final VideoCacheManager cacheManager;
    private final AudioSource audioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);

    @Nullable
    private VideoTexture texture = null;
    public String playingUrl;
    private ItemStack preOffHandItemStack = ItemStack.EMPTY;

    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private float scale = 1;
    private final AudioSource primaryAudioSource;
    private final AudioSource secondaryAudioSource;
    private boolean isSecondarySourceActive = false;
    private float audioOffsetX = 0, audioOffsetY = 0, audioOffsetZ = 0;
    private float audioMaxVolume = 5f;
    private float audioRangeMin = 2;
    private float audioRangeMax = 500;
    private float audioOffsetX2 = 0, audioOffsetY2 = 0, audioOffsetZ2 = 0;
    private float audioMaxVolume2 = 5f;
    private float audioRangeMin2 = 2;
    private float audioRangeMax2 = 500;
    public float speed = 1;
    private String desiredQuality = "自动";
    private boolean shouldCacheForLoop = false;
    private volatile boolean isLoopingInProgress = false;
    private final AtomicBoolean isTextureReady = new AtomicBoolean(false);

    private enum PlaybackStatus {
        IDLE,      // 空闲，无播放任务
        LOADING,   // 正在加载（展开URL、解析、下载）
        PLAYING,   // 正在播放或暂停
        FAILED     // 上一个加载任务失败
    }
    private volatile PlaybackStatus currentStatus = PlaybackStatus.IDLE;

    public PlayerAgent(ArmorStand entity) {
        LOGGER.info("在 {} 注册了一个 Mcedia Player 实例", entity.position());
        this.entity = entity;
        this.cacheManager = Mcedia.getInstance().getCacheManager();
        this.player = new MediaPlayer();
        player.setDecoderConfiguration(new DecoderConfiguration(new DecoderConfiguration.Builder()));
        this.primaryAudioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);
        this.secondaryAudioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);
        player.bindAudioSource(primaryAudioSource);
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

    private long getBaseDurationForUrl(String urlToCheck) {
        if (urlToCheck == null || currentPlaylistContent == null || currentPlaylistContent.isEmpty()) {
            return 0;
        }

        try {
            String[] lines = currentPlaylistContent.split("\n");

            for (int i = 0; i < lines.length; i++) {
                // 检查当前行是否包含我们正在播放的URL
                if (lines[i].contains(urlToCheck)) {
                    // 如果找到了，检查它是否还有下一行
                    if (i + 1 < lines.length) {
                        String nextLine = lines[i + 1].trim();

                        // 如果下一行是空的或者是另一个URL，那么它不是时间戳
                        if (nextLine.isEmpty() || nextLine.startsWith("http")) {
                            return 0;
                        }

                        // 尝试将下一行解析为时间
                        try {
                            long duration = parseToMicros(nextLine);
                            LOGGER.info("为 '{}' 成功解析到下一行的时间戳: {} us", urlToCheck, duration);
                            return duration;
                        } catch (IllegalArgumentException e) {
                            // 下一行不是有效的时间格式，忽略
                            return 0;
                        }
                    }
                    // 如果URL是最后一行，那么它没有下一行，直接退出循环
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("在解析基础时长时发生未知错误", e);
        }

        // 如果循环结束都没找到，返回0
        return 0;
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

    public long getDuration(String forUrl) {
        long baseDuration = getBaseDurationForUrl(forUrl);
        return baseDuration + getServerDuration() + this.timestampFromUrlUs;
    }

    public void update() {
        try {
            ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
            List<String> bookPages = getBookPages(mainHandItem);
            String newPlaylistContent = bookPages != null ? String.join("\n", bookPages) : "";
            if (!newPlaylistContent.equals(currentPlaylistContent)) {
                LOGGER.info("检测到播放列表变更，强制中断并更新...");
                currentPlaylistContent = newPlaylistContent;
                updatePlaylist(bookPages);
                playNextInQueue(); // 立即开始播放新列表
            }

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

    private void updatePlaylist(List<String> pages) {
        playlist.clear();
        playlistOriginalSize = 0;
        if (pages == null || pages.isEmpty()) {
            open(null);
            return;
        }

        for (String pageContent : pages) {
            if (pageContent == null || pageContent.isBlank()) continue;
            Matcher matcher = URL_PATTERN.matcher(pageContent);
            while (matcher.find()) {
                String url = matcher.group(1);
                playlist.offer(url);
                playlistOriginalSize++;
                LOGGER.info("已将URL添加到播放列表: {}", url);
            }
        }
        LOGGER.info("播放列表更新完成，共找到 {} 个媒体项目。", playlistOriginalSize);
    }

    public void tick() {
        update();

        Media currentMedia = player.getMedia();
        if (currentMedia != null && currentMedia.isEnded() && !this.isLoopingInProgress) {

            if (player.looping && playingUrl != null) {
                // [CRITICAL FIX] 统一处理所有循环逻辑
                this.isLoopingInProgress = true;

                if (playlistOriginalSize > 1) {
                    LOGGER.info("列表循环: 重新将 '{}' 添加到队尾并播放下一个。", playingUrl);
                    playlist.offer(playingUrl);
                    playNextInQueue();
                } else {
                    LOGGER.info("单曲循环: 重新播放 '{}'。", playingUrl);
                    open(playingUrl);
                }
            } else if (!player.looping && !playlist.isEmpty()) {
                LOGGER.info("当前视频播放结束，尝试播放列表中的下一个。");
                playNextInQueue();
            } else {
                LOGGER.info("播放列表已为空，播放结束。");
                open(null);
            }
        }
    }

    private void playNextInQueue() {
        String nextUrl = playlist.poll();
        if (nextUrl != null) {
            this.open(nextUrl);
            this.startPlayback(false);
        } else {
            LOGGER.info("播放列表已为空，播放结束。");
            this.open(null);
            this.currentStatus = PlaybackStatus.IDLE;
            player.closeSync();
        }
    }

    public void open(@Nullable String mediaUrl) {
        playingUrl = mediaUrl;
        isLoopingInProgress = false;
    }

    private void startPlayback(boolean isLooping) {
        // 1. 为本次播放请求生成一个唯一的令牌
        final long currentToken = this.playbackToken.incrementAndGet();

        this.isTextureReady.set(false);
        this.isPausedByBasePlate = false;

        // 2. 将关闭操作作为异步链的第一步，以避免阻塞主线程
        player.closeAsync().thenRun(() -> {
            // 3. 在关闭完成后，立刻检查令牌是否已过时。如果过时，则中止后续所有操作。
            if (playbackToken.get() != currentToken) {
                LOGGER.debug("Playback token {} is outdated, aborting start.", currentToken);
                return;
            }

            if (playingUrl == null) {
                return; // URL为空，关闭后无事可做
            }

            final String initialUrl = playingUrl;

            // 4. 检查缓存
            if (McediaConfig.CACHING_ENABLED && cacheManager.isCached(initialUrl)) {
                VideoInfo cachedInfo = cacheManager.getCachedVideoInfo(initialUrl);
                if (cachedInfo != null) {
                    LOGGER.info("正在从缓存播放: {}", initialUrl);

                    // 使用 openSync，因为它是一个快速的本地操作
                    player.openSync(cachedInfo, null, 0);

                    Media media = player.getMedia();
                    if (media != null) {
                        if (texture != null && media.getWidth() > 0) {
                            // 纹理准备依然需要异步，并捕获令牌
                            texture.prepareAndPrewarm(media.getWidth(), media.getHeight(), () -> {
                                if (playbackToken.get() == currentToken) {
                                    this.isTextureReady.set(true);
                                }
                            });
                        }
                        if (!isLooping && !media.isLiveStream()) {
                            IMediaProvider provider = MediaProviderRegistry.getInstance().getProviderForUrl(initialUrl);
                            if (provider == null || provider.isSeekSupported()) {
                                long durationToSeek = getDuration(initialUrl);
                                if (durationToSeek > 0) player.seek(durationToSeek);
                            }
                        }
                        player.play();
                        player.setSpeed(speed);
                        this.isLoopingInProgress = isLooping;
                    } else {
                        LOGGER.error("从缓存打开媒体后未能获取Media实例，回退到网络播放。");
                        fallbackToNetworkPlayback(initialUrl, isLooping, currentToken);
                    }
                    return; // 从缓存播放成功，返回
                }
            }

            // 5. 如果缓存未命中，则回退到网络播放流程
            fallbackToNetworkPlayback(initialUrl, isLooping, currentToken);
        });
    }

    private void fallbackToNetworkPlayback(String initialUrl, boolean isLooping, long currentToken) {
        UrlExpander.expand(initialUrl)
                .thenAccept(expandedUrl -> {
                    // 在每个异步回调的开始，都检查令牌
                    if (playbackToken.get() != currentToken) {
                        LOGGER.debug("Playback token {} is outdated, aborting URL expansion callback.", currentToken);
                        return;
                    }

                    this.timestampFromUrlUs = parseTimestampFromUrl(expandedUrl);
                    IMediaProvider provider = MediaProviderRegistry.getInstance().getProviderForUrl(expandedUrl);
                    if (provider != null) {
                        String warning = provider.getSafetyWarning();
                        if (warning != null && !warning.isEmpty()) Mcedia.msgToPlayer(warning);
                    }

                    LOGGER.info(isLooping ? "正在重新加载循环..." : "准备从网络播放 {}...", expandedUrl);

                    // openAsyncWithVideoInfo 内部现在会安全地关闭旧实例
                    CompletableFuture<VideoInfo> videoInfoFuture = player.openAsyncWithVideoInfo(
                            () -> {
                                // 在耗时的网络操作前再次检查令牌
                                if (playbackToken.get() != currentToken) {
                                    // 抛出异常以中断 CompletableFuture 链
                                    throw new IllegalStateException("Playback aborted by new request before resolving URL.");
                                }
                                try {
                                    return MediaProviderRegistry.getInstance().resolve(expandedUrl, McediaConfig.BILIBILI_COOKIE, this.desiredQuality);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            () -> McediaConfig.BILIBILI_COOKIE, 0
                    );

                    videoInfoFuture.handle((videoInfo, throwable) -> {
                        // 最终回调，同样需要检查令牌
                        if (playbackToken.get() != currentToken) {
                            LOGGER.debug("Playback token {} is outdated, aborting final handler.", currentToken);
                            return null;
                        }

                        if (throwable != null) {
                            // 检查是否是我们自己为了中止流程而抛出的异常
                            if (!(throwable.getCause() instanceof IllegalStateException)) {
                                handlePlaybackFailure(throwable, expandedUrl, isLooping);
                            }
                        } else {
                            handlePlaybackSuccess(videoInfo, expandedUrl, isLooping, provider);
                        }
                        return null;
                    });
                })
                .exceptionally(ex -> {
                    if (playbackToken.get() == currentToken) { // 只报告当前任务的错误
                        LOGGER.error("处理URL时发生严重错误: {}", initialUrl, ex);
                        handlePlaybackFailure(ex, initialUrl, isLooping);
                    }
                    return null;
                });
    }

    private long parseTimestampFromUrl(String url) {
        if (url == null) return 0;
        try {
            Pattern pattern = Pattern.compile("[?&]t=([^&]+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                String timestampStr = matcher.group(1);
                LOGGER.info("从URL中解析到时间戳: {}", timestampStr);
                return parseBiliTimestampToUs(timestampStr);
            }
        } catch (Exception e) {
            LOGGER.warn("解析URL时间戳失败", e);
        }
        return 0;
    }

    /**
     * 将B站的时间戳字符串 (如 1m30s, 90) 转换为微秒
     */
    private long parseBiliTimestampToUs(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return 0;

        long totalSeconds = 0;
        // 优先匹配包含 h/m/s 的格式
        if (timestamp.matches(".*[hms].*")) {
            Pattern h = Pattern.compile("(\\d+)h");
            Pattern m = Pattern.compile("(\\d+)m");
            Pattern s = Pattern.compile("(\\d+)s");
            Matcher hMatcher = h.matcher(timestamp);
            if (hMatcher.find()) totalSeconds += Long.parseLong(hMatcher.group(1)) * 3600;
            Matcher mMatcher = m.matcher(timestamp);
            if (mMatcher.find()) totalSeconds += Long.parseLong(mMatcher.group(1)) * 60;
            Matcher sMatcher = s.matcher(timestamp);
            if (sMatcher.find()) totalSeconds += Long.parseLong(sMatcher.group(1));
        } else {
            // 否则，认为是纯秒数
            try {
                totalSeconds = Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                LOGGER.warn("无法将时间戳 '{}' 解析为秒数。", timestamp);
                return 0;
            }
        }

        long totalUs = totalSeconds * 1_000_000L;
        LOGGER.info("解析出的时间戳为 {} us", totalUs);
        return totalUs;
    }

    private void handlePlaybackSuccess(VideoInfo videoInfo, String finalMediaUrl, boolean isLooping, @Nullable IMediaProvider provider) {
        this.currentStatus = PlaybackStatus.PLAYING;
        Media media = player.getMedia();
        if (media == null) {
            LOGGER.error("视频加载成功但Media对象为空，这是一个严重错误。");
            return;
        }

        if (shouldCacheForLoop && !media.isLiveStream() && !cacheManager.isCached(finalMediaUrl) && !cacheManager.isCaching(finalMediaUrl)) {
            LOGGER.info("正在为循环播放在后台缓存视频: {}", finalMediaUrl);
            cacheManager.cacheVideoAsync(finalMediaUrl, videoInfo, McediaConfig.BILIBILI_COOKIE)
                    .handle((unused, cacheThrowable) -> {
                        if (cacheThrowable != null) Mcedia.msgToPlayer("§e[Mcedia] §c视频后台缓存失败: " + finalMediaUrl);
                        else Mcedia.msgToPlayer("§a[Mcedia] §f视频已缓存: " + finalMediaUrl);
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

        if (!media.isLiveStream()) {
            if (provider != null && provider.isSeekSupported()) {
                long durationToSeek = isLooping ? 0 : getDuration(playingUrl);
                if (durationToSeek > 0) {
                    LOGGER.info("视频加载成功，将在 {} us 处开始播放 (支持跳转)。", durationToSeek);
                    player.seek(durationToSeek);
                } else {
                    LOGGER.info("视频加载成功，将从头开始播放。");
                }
            } else {
                LOGGER.warn("当前视频源 ({}) 不支持跳转操作，将从头开始播放。", provider != null ? provider.getClass().getSimpleName() : "未知直链");
            }
        } else {
            LOGGER.info("直播流加载成功，直接开始播放。");
        }

        player.play();
        player.setSpeed(speed);
        this.isLoopingInProgress = false;
    }

    private void handlePlaybackFailure(Throwable throwable, String finalMediaUrl, boolean isLooping) {
        this.currentStatus = PlaybackStatus.FAILED;
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
        Media media = player.getMedia();
        if (media != null) {
            boolean shouldBePaused = !state.showBasePlate;
            if (shouldBePaused && !media.isPaused()) {
                player.pause();
                this.isPausedByBasePlate = true;
            } else if (!shouldBePaused && media.isPaused() && this.isPausedByBasePlate) {
                player.play();
                this.isPausedByBasePlate = false;
            }
        }
        var size = state.scale * scale;
        var volumeFactor = 1 - (state.leftArmPose.x() < 0 ? -state.leftArmPose.x() / 360f : (360.0f - state.leftArmPose.x()) / 360f);
        var speedFactor = state.leftArmPose.y() < 0 ? -state.leftArmPose.y() / 360f : (360.0f - state.leftArmPose.y()) / 360f;
        speed = speedFactor < 0.1f ? 1f : (speedFactor > 0.5f ? 1f - (1f - speedFactor) * 2f : (speedFactor - 0.1f) / 0.4f * 8f);
        player.setSpeed(speed);

        var primaryAudioOffsetRotated = new Vector3f(audioOffsetX, audioOffsetY, audioOffsetZ).rotateY(state.yRot);
        primaryAudioSource.setVolume(audioMaxVolume * volumeFactor);
        primaryAudioSource.setRange(audioRangeMin, audioRangeMax);
        primaryAudioSource.setPos(((float) state.x + primaryAudioOffsetRotated.x), ((float) state.y + primaryAudioOffsetRotated.y), ((float) state.z + primaryAudioOffsetRotated.z));
        if (isSecondarySourceActive) {
            var secondaryAudioOffsetRotated = new Vector3f(audioOffsetX2, audioOffsetY2, audioOffsetZ2).rotateY(state.yRot);
            secondaryAudioSource.setVolume(audioMaxVolume2 * volumeFactor);
            secondaryAudioSource.setRange(audioRangeMin2, audioRangeMax2);
            secondaryAudioSource.setPos(((float) state.x + secondaryAudioOffsetRotated.x), ((float) state.y + secondaryAudioOffsetRotated.y), ((float) state.z + secondaryAudioOffsetRotated.z));
        }

        synchronized (player) {
            Media currentMedia = player.getMedia();
            if (media != null) {
                media.uploadVideo();
                if (media.getHeight() > 0) halfW = media.getAspectRatio();
            } else halfW = 1.777f;
        }

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
        ResourceLocation screenTexture;
        switch (currentStatus) {
            case LOADING:
                screenTexture = loadingScreen;
                break;
            case FAILED:
                screenTexture = errorScreen;
                break;
            case PLAYING:
                if (player.getMedia() != null && this.isTextureReady.get()) {
                    screenTexture = this.texture.getResourceLocation();
                } else {
                    screenTexture = loadingScreen;
                }
                break;
            case IDLE:
            default:
                screenTexture = idleScreen;
                break;
        }
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
    public void closeSync() {
        this.currentStatus = PlaybackStatus.IDLE;
        playingUrl = null;
        isLoopingInProgress = false;
        player.closeSync();
        LOGGER.info("PlayerAgent已关闭，实体位于 {}", entity.position());
    }
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
    public void updateOther(String flags) { boolean looping = flags.contains("looping"); this.player.setLooping(looping); this.shouldCacheForLoop = looping && McediaConfig.CACHING_ENABLED; if (!looping && cacheManager.isCached(playingUrl)) cacheManager.cleanup(); }
    public void updateQuality(String quality) { this.desiredQuality = (quality == null || quality.isBlank()) ? "自动" : quality.trim(); }
    public void updateOffset(String offset) { try { var vars = offset.split("\n"); offsetX = Float.parseFloat(vars[0]); offsetY = Float.parseFloat(vars[1]); offsetZ = Float.parseFloat(vars[2]); scale = Float.parseFloat(vars[3]); } catch (Exception ignored) {} }
    public void updateAudioOffset(String config) {
        if (config == null) return;
        String[] blocks = config.split("\\n\\s*\\n");

        try {
            if (blocks.length > 0 && !blocks[0].isBlank()) {
                String[] vars = blocks[0].split("\n");
                audioOffsetX = Float.parseFloat(vars[0]);
                audioOffsetY = Float.parseFloat(vars[1]);
                audioOffsetZ = Float.parseFloat(vars[2]);
                audioMaxVolume = Float.parseFloat(vars[3]);
                audioRangeMin = Float.parseFloat(vars[4]);
                audioRangeMax = Float.parseFloat(vars[5]);
            }
            if (blocks.length > 1 && !blocks[1].isBlank()) {
                String[] vars = blocks[1].split("\n");
                audioOffsetX2 = Float.parseFloat(vars[0]);
                audioOffsetY2 = Float.parseFloat(vars[1]);
                audioOffsetZ2 = Float.parseFloat(vars[2]);
                audioMaxVolume2 = Float.parseFloat(vars[3]);
                audioRangeMin2 = Float.parseFloat(vars[4]);
                audioRangeMax2 = Float.parseFloat(vars[5]);
                if (!isSecondarySourceActive) {
                    player.bindAudioSource(secondaryAudioSource);
                    isSecondarySourceActive = true;
                    LOGGER.info("已启用并配置副声源。");
                }
            } else {
                if (isSecondarySourceActive) {
                    player.unbindAudioSource(secondaryAudioSource);
                    isSecondarySourceActive = false;
                    LOGGER.info("已禁用副声源。");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析声源配置失败，请检查格式。", e);
        }
    }
}
package top.tobyprime.mcedia;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.agent.PlayerConfigManager;
import top.tobyprime.mcedia.agent.PlayerRenderer;
import top.tobyprime.mcedia.agent.PlaylistManager;
import top.tobyprime.mcedia.core.AudioSource;
import top.tobyprime.mcedia.core.Media;
import top.tobyprime.mcedia.core.MediaPlayer;
import top.tobyprime.mcedia.interfaces.IMediaInfo;
import top.tobyprime.mcedia.interfaces.IMediaProvider;
import top.tobyprime.mcedia.manager.BilibiliAuthManager;
import top.tobyprime.mcedia.manager.DanmakuManager;
import top.tobyprime.mcedia.provider.*;
import top.tobyprime.mcedia.video_fetcher.BilibiliBangumiFetcher;
import top.tobyprime.mcedia.video_fetcher.DanmakuFetcher;
import top.tobyprime.mcedia.video_fetcher.UrlExpander;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerAgent {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAgent.class);

    // --- 核心对象 ---
    private final ArmorStand entity;
    private final MediaPlayer player;
    private final DanmakuManager danmakuManager;
    private final AudioSource primaryAudioSource;
    private final AudioSource secondaryAudioSource;
    @Nullable
    private VideoTexture texture;

    // --- 管理器 ---
    private final PlayerConfigManager configManager;
    private final PlaylistManager playlistManager;
    private final PlayerRenderer renderer;

    // --- 状态与缓存字段 ---
    private String playingUrl;
    private ItemStack preMainHandItemStack = ItemStack.EMPTY;
    private ItemStack preOffHandItemStack = ItemStack.EMPTY;
    @Nullable
    private BilibiliBangumiInfo currentBangumiInfo = null;
    private long timestampFromUrlUs = 0;

    private final AtomicLong playbackToken = new AtomicLong(0);
    private volatile boolean isReconnecting = false;
    private PlaybackSource currentSource = PlaybackSource.BOOK;
    private volatile PlaybackStatus currentStatus = PlaybackStatus.IDLE;

    private final AtomicBoolean isTextureReady = new AtomicBoolean(false);
    private boolean isTextureInitialized = false;
    private boolean isPausedByBasePlate = false;
    private boolean hasPerformedInitialCheck = false;
    private int saveProgressTicker = 0;

    // --- 内部数据结构 ---
    public enum PlaybackSource {BOOK, COMMAND}

    public enum PlaybackStatus {IDLE, LOADING, PLAYING, FAILED}

    public enum BiliPlaybackMode {NONE, SINGLE_PART, VIDEO_PLAYLIST, BANGUMI_SERIES}

    public static class PlaybackItem {
        public final String originalUrl;
        public int pNumber = 1;
        public long timestampUs = 0;
        public BiliPlaybackMode mode = BiliPlaybackMode.NONE;
        @Nullable
        public String desiredQuality = null;
        @Nullable
        public String seasonId = null;

        public PlaybackItem(String url) {
            this.originalUrl = url;
        }
    }

    public PlayerAgent(ArmorStand entity) {
        LOGGER.info("在 {} 注册了一个 Mcedia Player 实例", entity.position());
        this.entity = entity;
        this.player = new MediaPlayer();
        this.danmakuManager = new DanmakuManager();
        this.primaryAudioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);
        this.secondaryAudioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);
        player.bindAudioSource(primaryAudioSource);

        this.configManager = new PlayerConfigManager(this);
        this.playlistManager = new PlaylistManager(this);
        this.renderer = new PlayerRenderer();

        this.configManager.updateConfigFrom(entity.getItemInHand(InteractionHand.OFF_HAND));
        this.preOffHandItemStack = entity.getItemInHand(InteractionHand.OFF_HAND).copy();
        this.preMainHandItemStack = entity.getItemInHand(InteractionHand.MAIN_HAND).copy();
    }

    public void initializeGraphics() {
        if (this.texture == null) {
            this.texture = new VideoTexture(ResourceLocation.fromNamespaceAndPath("mcedia", "player_" + hashCode()));
            player.bindTexture(this.texture);
        }
    }

    public void tick() {
        if (!hasPerformedInitialCheck && Minecraft.getInstance().player != null) {
            hasPerformedInitialCheck = true;
            LOGGER.info("为实体 {} 执行首次加载检查...", entity.getUUID());
            // 强制使用当前手上的书更新播放列表，并启动播放
            if (playlistManager.updatePlaylistFromBook(entity.getItemInHand(InteractionHand.MAIN_HAND), this.currentSource)) {
                playlistManager.startPlaylist();
            }
        }
        try {
            update();
            Media currentMedia = player.getMedia();
            if (currentMedia != null && !isReconnecting) {
                if (McediaConfig.isResumeOnReloadEnabled() && !currentMedia.isLiveStream() && currentMedia.isPlaying()) {
                    if (++saveProgressTicker >= 100) {
                        saveProgressTicker = 0;
                        Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), currentMedia.getDurationUs());
                    }
                }
                if (currentMedia.needsReconnect()) {
                    LOGGER.warn("检测到媒体流中断，正在尝试自动重连: {}", playingUrl);
                    isReconnecting = true;
                    startPlayback(playingUrl, false, currentMedia.getDurationUs(), configManager.desiredQuality);
                    return;
                }
                if (currentMedia.isEnded()) {
                    playlistManager.handleMediaEnd();
                }
            }
        } catch (Exception e) {
            LOGGER.error("在 PlayerAgent.tick() 中发生未捕获的异常", e);
        }
    }

    public void update() {
        ItemStack offHandItem = entity.getItemInHand(InteractionHand.OFF_HAND);
        if (!ItemStack.matches(offHandItem, preOffHandItemStack)) {
            LOGGER.info("检测到副手配置书变更，正在应用新设置...");
            PlayerConfigManager.ConfigChangeType changeType = configManager.updateConfigFrom(offHandItem);
            this.preOffHandItemStack = offHandItem.copy();
            Media currentMedia = player.getMedia();
            if (currentMedia == null || playingUrl == null) return; // 如果没在播放，任何变更都不需要立即动作

            switch (changeType) {
                case RELOAD_MEDIA:
                    LOGGER.info("检测到清晰度变更: '{}'，正在软重载...", configManager.desiredQuality);
                    long seekTo = !currentMedia.isLiveStream() ? currentMedia.getDurationUs() : 0;
                    startPlayback(playingUrl, false, seekTo, configManager.desiredQuality);
                    break;

                case HOT_UPDATE:
                    LOGGER.info("检测到可热更新的配置变更 (例如字幕)...");
                    // 调用一个新的方法来处理热更新
                    handleHotUpdate(currentMedia.getMediaInfo());
                    break;

                case NONE:
                    // 无需操作
                    break;
            }
        }

        ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
        if (!ItemStack.matches(mainHandItem, preMainHandItemStack)) {
            this.preMainHandItemStack = mainHandItem.copy();
            if (playlistManager.updatePlaylistFromBook(mainHandItem, this.currentSource)) {
                LOGGER.info("检测到播放列表(书本)变更，强制中断并更新...");
                playlistManager.startPlaylist();
            }
        }
    }

    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i) {
        renderer.render(state, bufferSource, poseStack, i, this);
    }

    // --- 核心播放流程 ---

    public void startPlayback(String url, boolean isLooping, long initialSeekUs, String quality) {
        if (url == null || url.isBlank()) {
            stopPlayback();
            return;
        }

        this.playingUrl = url;
        this.isReconnecting = false;
        this.currentStatus = PlaybackStatus.LOADING;
        final long currentToken = this.playbackToken.incrementAndGet();
        this.isTextureReady.set(false);
        this.isPausedByBasePlate = false;

        player.closeAsync().thenRun(() -> {
            if (playbackToken.get() != currentToken) {
                LOGGER.debug("Playback token {} is outdated, aborting start.", currentToken);
                return;
            }
            if (playingUrl == null) return;

            final String initialUrl = playingUrl;
            if (McediaConfig.isCachingEnabled() && Mcedia.getInstance().getCacheManager().isCached(initialUrl)) {
                VideoInfo cachedInfo = Mcedia.getInstance().getCacheManager().getCachedVideoInfo(initialUrl);
                if (cachedInfo != null) {
                    LOGGER.info("正在从缓存播放: {}", initialUrl);
                    player.openSync(cachedInfo, null, 0);
                    Media media = player.getMedia();
                    if (media != null) {
                        if (texture != null && media.getWidth() > 0) {
                            texture.prepareAndPrewarm(media.getWidth(), media.getHeight(), () -> {
                                if (playbackToken.get() == currentToken) {
                                    this.isTextureReady.set(true);
                                }
                            });
                        }
                        if (!isLooping && !media.isLiveStream()) {
                            IMediaProvider provider = MediaProviderRegistry.getInstance().getProviderForUrl(initialUrl);
                            if (provider == null || provider.isSeekSupported()) {
                                if (initialSeekUs > 0) player.seek(initialSeekUs);
                            }
                        }
                        handlePlaybackSuccess(cachedInfo, initialUrl, isLooping, null);
                    } else {
                        LOGGER.error("从缓存打开媒体后未能获取Media实例，回退到网络播放。");
                        fallbackToNetworkPlayback(initialUrl, isLooping, currentToken, initialSeekUs, quality);
                    }
                    return;
                }
            }
            fallbackToNetworkPlayback(initialUrl, isLooping, currentToken, initialSeekUs, quality);
        });
    }

    private void fallbackToNetworkPlayback(String url, boolean isLooping, long token, long initialSeekUs, String quality) {
        CompletableFuture<VideoInfo> videoInfoFuture = UrlExpander.expand(url).thenComposeAsync(expandedUrl -> {
            if (playbackToken.get() != token) {
                return CompletableFuture.failedFuture(new IllegalStateException("Playback aborted by new request."));
            }
            this.timestampFromUrlUs = parseTimestampFromUrl(expandedUrl);
            IMediaProvider provider = MediaProviderRegistry.getInstance().getProviderForUrl(expandedUrl);
            if (provider != null) {
                String warning = provider.getSafetyWarning();
                if (warning != null && !warning.isEmpty()) Mcedia.msgToPlayer(warning);
            }
            LOGGER.info(isLooping ? "正在重新加载循环..." : "准备从网络播放 {}...", expandedUrl);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    final String cookie = BilibiliAuthManager.getInstance().getCookie();
                    if (provider == null) throw new UnsupportedOperationException("No provider found for URL: " + expandedUrl);

                    return provider.resolve(expandedUrl, cookie, quality);
                } catch (Exception e) {
                    Throwable rootCause = e;
                    while (rootCause.getCause() != null) {
                        rootCause = rootCause.getCause();
                    }
                    if (rootCause instanceof BilibiliAuthRequiredException && !"none".equalsIgnoreCase(McediaConfig.getYtdlpBrowserCookie())) {
                        LOGGER.warn("原生解析需要登录，正在回退到 YtDlpProvider 尝试使用浏览器 Cookie...");
                        try {
                            IMediaProvider ytdlpProvider = new YtDlpProvider();
                            return ytdlpProvider.resolve(expandedUrl, null, quality);
                        } catch (Exception ytdlpException) {
                            throw new RuntimeException("YtDlpProvider 回退失败", ytdlpException);
                        }
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }, Mcedia.getInstance().getBackgroundExecutor());
        }, Mcedia.getInstance().getBackgroundExecutor());

        videoInfoFuture.handleAsync((videoInfo, throwable) -> {
            if (playbackToken.get() != token) {
                LOGGER.debug("Playback token {} is outdated, aborting final handler.", token);
                return null;
            }
            if (throwable != null) {
                if (!(throwable.getCause() instanceof IllegalStateException)) {
                    Minecraft.getInstance().execute(() -> handlePlaybackFailure(throwable, url, isLooping));
                }
            } else {
                try {
                    player.openSync(videoInfo, null, 0);
                    Minecraft.getInstance().execute(() -> {
                        if (playbackToken.get() != token) {
                            player.closeAsync();
                            return;
                        }
                        IMediaProvider provider = MediaProviderRegistry.getInstance().getProviderForUrl(url);
                        if (!isLooping && initialSeekUs > 0 && (provider == null || provider.isSeekSupported())) {
                            player.seek(initialSeekUs);
                        }
                        handlePlaybackSuccess(videoInfo, url, isLooping, provider);
                    });
                } catch (Exception e) {
                    LOGGER.error("在后台线程执行 openSync 时失败", e);
                    Minecraft.getInstance().execute(() -> handlePlaybackFailure(e, url, isLooping));
                }
            }
            return null;
        }, Mcedia.getInstance().getBackgroundExecutor());
    }

    private void handlePlaybackSuccess(VideoInfo videoInfo, String finalMediaUrl, boolean isLooping, @Nullable IMediaProvider provider) {
        this.currentStatus = PlaybackStatus.PLAYING;
        this.isTextureInitialized = false;
        Media media = player.getMedia();
        if (media == null) {
            LOGGER.error("视频加载成功但Media对象为空，这是一个严重错误。");
            handlePlaybackFailure(new IllegalStateException("Media is null after successful open"), finalMediaUrl, isLooping);
            return;
        }

        handleHotUpdate(videoInfo);

        CompletableFuture.runAsync(() -> {
            danmakuManager.clear();
            if (this.configManager.danmakuEnable && videoInfo.getCid() > 0) {
                LOGGER.info("正在获取B站内容(cid={})的弹幕...", videoInfo.getCid());
                DanmakuFetcher.fetchDanmaku(videoInfo.getCid()).thenAcceptAsync(danmakuManager::load, Minecraft.getInstance()::execute);
            }
        });

        if (configManager.shouldCacheForLoop && !media.isLiveStream() && !Mcedia.getInstance().getCacheManager().isCached(finalMediaUrl) && !Mcedia.getInstance().getCacheManager().isCaching(finalMediaUrl)) {
            LOGGER.info("正在为循环播放在后台缓存视频: {}", finalMediaUrl);
            String cookie = (provider != null && provider.getClass().getSimpleName().toLowerCase().contains("bilibili")) ? McediaConfig.getBilibiliCookie() : null;
            Mcedia.getInstance().getCacheManager().cacheVideoAsync(finalMediaUrl, videoInfo, cookie).handle((unused, cacheThrowable) -> {
                if (cacheThrowable != null) Mcedia.msgToPlayer("§e[Mcedia] §c视频后台缓存失败: " + finalMediaUrl);
                else Mcedia.msgToPlayer("§a[Mcedia] §f视频已缓存: " + finalMediaUrl);
                return null;
            });
        }

        if (!isLooping) {
            MutableComponent hoverText = Component.literal("点击可在浏览器中打开");
            List<QualityInfo> qualities = videoInfo.getAvailableQualities();
            String currentQuality = videoInfo.getCurrentQuality();
            if (qualities != null && !qualities.isEmpty()) {
                hoverText.append(Component.literal("\n\n§f可用清晰度:").withStyle(ChatFormatting.GRAY));
                for (QualityInfo quality : qualities) {
                    boolean isCurrent = Objects.equals(quality.description, currentQuality);
                    ChatFormatting color = isCurrent ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA;
                    String prefix = isCurrent ? "\n> " : "\n- ";
                    hoverText.append(Component.literal(prefix + quality.description).withStyle(color));
                }
            }
            HoverEvent hoverEvent = new HoverEvent.ShowText(hoverText);
            ClickEvent clickEvent = new ClickEvent.OpenUrl(URI.create(finalMediaUrl));
            Style clickableStyle = Style.EMPTY.withClickEvent(clickEvent).withHoverEvent(hoverEvent);
            MutableComponent msg = Component.literal("§a[Mcedia] §f播放: ");
            msg.append(Component.literal(videoInfo.getTitle()).withStyle(clickableStyle.withColor(ChatFormatting.YELLOW)));
            if (videoInfo.isMultiPart() && videoInfo.getPartName() != null && !videoInfo.getPartName().isEmpty()) {
                msg.append(Component.literal(" (P" + videoInfo.getPartNumber() + ": " + videoInfo.getPartName() + ")").withStyle(clickableStyle.withColor(ChatFormatting.GOLD)));
            }
            msg.append(Component.literal(" - ").withStyle(ChatFormatting.WHITE));
            msg.append(Component.literal(videoInfo.getAuthor()).withStyle(clickableStyle.withColor(ChatFormatting.AQUA)));
            if (videoInfo.getCurrentQuality() != null) {
                msg.append(Component.literal(" [").withStyle(ChatFormatting.GRAY)).append(Component.literal(videoInfo.getCurrentQuality()).withStyle(ChatFormatting.DARK_PURPLE)).append(Component.literal("]").withStyle(ChatFormatting.GRAY));
            }
            Mcedia.msgToPlayer(msg);
        }

        player.play();
    }

    private void handlePlaybackFailure(Throwable throwable, String initialUrl, boolean isLooping) {
        this.currentStatus = PlaybackStatus.FAILED;
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        LOGGER.warn("打开视频失败, 原始链接: {}, 根本原因: {}", initialUrl, rootCause.getMessage());
        if (rootCause instanceof BilibiliAuthRequiredException) {
            Mcedia.msgToPlayer("§c[Mcedia] §f无法解析或播放: " + initialUrl);
            Mcedia.msgToPlayer("§e[Mcedia] §f该内容需要登录或会员。请使用 §a/mcedia login §f重新登录。");
        } else if (!isLooping) {
            Mcedia.msgToPlayer("§c[Mcedia] §f无法解析或播放: " + initialUrl);
        }
    }

    public void stopPlayback() {
        this.currentStatus = PlaybackStatus.IDLE;
        this.playingUrl = null;
        this.currentBangumiInfo = null;
        this.danmakuManager.clear();
        player.closeAsync();
    }

    public CompletableFuture<Void> shutdownAsync() {
        LOGGER.info("正在异步关闭 PlayerAgent，实体位于 {}", entity.position());
        if (McediaConfig.isResumeOnReloadEnabled()) {
            Media currentMedia = player.getMedia();
            if (currentMedia != null && !currentMedia.isLiveStream() && currentMedia.getDurationUs() > 0) {
                Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), currentMedia.getDurationUs());
            }
        }
        stopPlayback();
        return CompletableFuture.runAsync(() -> {
            player.closeSync();
            player.shutdown();
        }, Mcedia.getInstance().getBackgroundExecutor());
    }

    private void handleHotUpdate(IMediaInfo mediaInfo) {
        if (mediaInfo == null) return;

        final String currentUrl = this.playingUrl;

        Mcedia.getInstance().getBackgroundExecutor().submit(() -> {
            // --- 弹幕逻辑 ---
            danmakuManager.clear();
            if (this.configManager.danmakuEnable && mediaInfo instanceof VideoInfo vi && vi.getCid() > 0) {
                LOGGER.info("Loading danmaku for cid={}", vi.getCid());
                DanmakuFetcher.fetchDanmaku(vi.getCid()).thenAcceptAsync(danmakuManager::load, Minecraft.getInstance()::execute);
            }
        });
    }

    // --- 指令处理方法---

    public void commandPause() {
        if (player.getMedia() != null) {
            player.pause();
            Mcedia.msgToPlayer("§e[Mcedia] §f播放已暂停。");
        }
    }

    public void commandResume() {
        if (player.getMedia() != null) {
            player.play();
            Mcedia.msgToPlayer("§a[Mcedia] §f播放已恢复。");
        }
    }

    public void commandStop() {
        playlistManager.commandPlaylistClear();
    }

    public void commandSkip() {
        playlistManager.commandSkip();
    }

    public void commandSeek(long seekUs) {
        Media media = player.getMedia();
        if (media != null && !media.isLiveStream()) {
            player.seek(seekUs);
            danmakuManager.seek(seekUs);
            long totalSeconds = seekUs / 1_000_000L;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            String time;
            if (hours > 0) {
                time = String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else {
                time = String.format("%02d:%02d", minutes, seconds);
            }
            Mcedia.msgToPlayer("§f[Mcedia] §7已跳转到 " + time);
        } else {
            Mcedia.msgToPlayer("§c[Mcedia] §f当前媒体不支持跳转。");
        }
    }

    public void commandSetVolume(float volumePercent) {
        if (volumePercent >= 0 && volumePercent <= 100) {
            configManager.audioMaxVolume = (volumePercent / 100.0f) * 10.0f;
            Mcedia.msgToPlayer(String.format("§f[Mcedia] §7音量已设置为 %.0f%%", volumePercent));
        } else {
            Mcedia.msgToPlayer("§c[Mcedia] §f音量百分比必须在 0-100 之间。");
        }
    }

    public void commandSetUrl(String url) {
        playlistManager.commandSetUrl(url);
    }

    public void commandSetOffset(float x, float y, float z, float scale) {
        configManager.offsetX = x;
        configManager.offsetY = y;
        configManager.offsetZ = z;
        configManager.scale = scale;
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7屏幕偏移已设置为 (%.2f, %.2f, %.2f)，缩放为 %.2f。", x, y, z, scale));
    }

    public void commandSetLooping(boolean enabled) {
        player.setLooping(enabled);
        configManager.shouldCacheForLoop = enabled && McediaConfig.isCachingEnabled();
        Mcedia.msgToPlayer(enabled ? "§a[Mcedia] §f已开启循环。" : "§e[Mcedia] §f已关闭循环。");
    }

    public void commandSetAudioPrimary(float x, float y, float z, float maxVol, float minRange, float maxRange) {
        configManager.audioOffsetX = x;
        configManager.audioOffsetY = y;
        configManager.audioOffsetZ = z;
        configManager.audioMaxVolume = maxVol;
        configManager.audioRangeMin = minRange;
        configManager.audioRangeMax = maxRange;
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7主声源已设置为: 偏移(%.2f, %.2f, %.2f), 音量 %.1f, 范围 [%.1f, %.1f]。", x, y, z, maxVol, minRange, maxRange));
    }

    public void commandSetAudioSecondary(float x, float y, float z, float maxVol, float minRange, float maxRange) {
        configManager.audioOffsetX2 = x;
        configManager.audioOffsetY2 = y;
        configManager.audioOffsetZ2 = z;
        configManager.audioMaxVolume2 = maxVol;
        configManager.audioRangeMin2 = minRange;
        configManager.audioRangeMax2 = maxRange;
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7副声源参数已更新为: 偏移(%.2f, %.2f, %.2f), 音量 %.1f, 范围 [%.1f, %.1f]。", x, y, z, maxVol, minRange, maxRange));
        if (!configManager.isSecondarySourceActive) {
            Mcedia.msgToPlayer("§7提示: 副声源当前未启用，使用 §a/mcedia control enable secondary_audio §7来启用。");
        }
    }

    public boolean commandToggleAudioSecondary() {
        if (configManager.isSecondarySourceActive) {
            player.unbindAudioSource(secondaryAudioSource);
            configManager.isSecondarySourceActive = false;
            Mcedia.msgToPlayer("§e[Mcedia] §f副声源已禁用。");
            return false;
        } else {
            player.bindAudioSource(secondaryAudioSource);
            configManager.isSecondarySourceActive = true;
            Mcedia.msgToPlayer("§a[Mcedia] §f副声源已启用。");
            return true;
        }
    }

    public boolean isSecondaryAudioActive() {
        return configManager.isSecondarySourceActive;
    }

    public Component getStatusComponent() {
        Media media = player.getMedia();
        MutableComponent status = Component.literal("§6--- Mcedia Player Status ---\n");
        if (media == null) {
            status.append("§e状态: §f空闲 (无播放)\n");
            return status;
        }
        IMediaInfo info = media.getMediaInfo();
        if (playingUrl != null) {
            MutableComponent hoverText = Component.literal("点击可在浏览器中打开");
            if (info != null) {
                List<QualityInfo> qualities = info.getAvailableQualities();
                String currentQuality = info.getCurrentQuality();
                if (qualities != null && !qualities.isEmpty()) {
                    hoverText.append(Component.literal("\n\n§f可用清晰度:").withStyle(ChatFormatting.GRAY));
                    for (QualityInfo quality : qualities) {
                        boolean isCurrent = Objects.equals(quality.description, currentQuality);
                        hoverText.append(Component.literal((isCurrent ? "\n> " : "\n- ") + quality.description).withStyle(isCurrent ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA));
                    }
                }
            }
            status.append(Component.literal("§eURL: ").append(Component.literal(playingUrl).withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(hoverText)).withClickEvent(new ClickEvent.OpenUrl(URI.create(playingUrl))).withColor(ChatFormatting.AQUA)))).append("\n");
        }
        if (info != null) {
            if (info.getTitle() != null && !info.getTitle().isBlank())
                status.append("§e标题: §f" + info.getTitle() + "\n");
            if (info.getAuthor() != null && !info.getAuthor().isBlank())
                status.append("§e作者: §f" + info.getAuthor() + "\n");
        }
        if (media.isLiveStream()) {
            long d = media.getDurationUs() / 1000000;
            status.append(String.format("§e进度: §a%02d:%02d:%02d\n", d / 3600, (d % 3600) / 60, d % 60));
        } else {
            long c = media.getDurationUs() / 1000000, t = media.getLengthUs() / 1000000;
            status.append(String.format("§e进度: §a%02d:%02d:%02d §f/ §7%02d:%02d:%02d\n", c / 3600, (c % 3600) / 60, c % 60, t / 3600, (t % 3600) / 60, t % 60));
        }
        if (info != null && info.getCurrentQuality() != null)
            status.append("§e清晰度: §d" + info.getCurrentQuality() + "\n");
        status.append("§e状态: " + (player.isBuffering() ? "§6缓冲中" : (media.isPaused() ? "§e已暂停" : "§a播放中")) + "\n");
        status.append("§6--- 实体与组件状态 ---\n");
        float yRotRad = (float) -Math.toRadians(this.entity.getYRot());
        Vector3f screenOff = new Vector3f(configManager.offsetX, configManager.offsetY, configManager.offsetZ).rotateY(yRotRad);
        status.append(String.format("§e屏幕坐标: §f(%.2f, %.2f, %.2f)\n", entity.getX() + screenOff.x, entity.getY() + screenOff.y + 1.02 * entity.getScale(), entity.getZ() + screenOff.z));
        Vector3f pAudioOff = new Vector3f(configManager.audioOffsetX, configManager.audioOffsetY, configManager.audioOffsetZ).rotateY(yRotRad);
        status.append(String.format("§e主声源: §f(%.2f, %.2f, %.2f)\n", entity.getX() + pAudioOff.x, entity.getY() + pAudioOff.y, entity.getZ() + pAudioOff.z));
        if (configManager.isSecondarySourceActive) {
            Vector3f sAudioOff = new Vector3f(configManager.audioOffsetX2, configManager.audioOffsetY2, configManager.audioOffsetZ2).rotateY(yRotRad);
            status.append(String.format("§e副声源: §a已启用 §f(%.2f, %.2f, %.2f)\n", entity.getX() + sAudioOff.x, entity.getY() + sAudioOff.y, entity.getZ() + sAudioOff.z));
        } else status.append("§e副声源: §7未启用\n");
        if (configManager.danmakuEnable) {
            status.append(String.format("§e弹幕: §a开启 §7(类型: %s)\n", (configManager.showScrollingDanmaku ? "滚 " : "") + (configManager.showTopDanmaku ? "顶 " : "") + (configManager.showBottomDanmaku ? "底" : "").trim()));
        } else status.append("§e弹幕: §7关闭\n");
        status.append(String.format("§e自动连播: %s\n", (configManager.videoAutoplay ? "§a开启" : "§7关闭")));
        status.append("§6-------------------------\n");
        return status;
    }

    public PresetData getPresetData() {
        PresetData d = new PresetData();
        d.screenX = configManager.offsetX;
        d.screenY = configManager.offsetY;
        d.screenZ = configManager.offsetZ;
        d.screenScale = configManager.scale;
        d.primaryAudio.x = configManager.audioOffsetX;
        d.primaryAudio.y = configManager.audioOffsetY;
        d.primaryAudio.z = configManager.audioOffsetZ;
        d.primaryAudio.maxVol = configManager.audioMaxVolume;
        d.primaryAudio.minRange = configManager.audioRangeMin;
        d.primaryAudio.maxRange = configManager.audioRangeMax;
        d.secondaryAudio.x = configManager.audioOffsetX2;
        d.secondaryAudio.y = configManager.audioOffsetY2;
        d.secondaryAudio.z = configManager.audioOffsetZ2;
        d.secondaryAudio.maxVol = configManager.audioMaxVolume2;
        d.secondaryAudio.minRange = configManager.audioRangeMin2;
        d.secondaryAudio.maxRange = configManager.audioRangeMax2;
        d.secondaryAudioEnabled = configManager.isSecondarySourceActive;
        return d;
    }

    public void applyPreset(PresetData d) {
        commandSetOffset(d.screenX, d.screenY, d.screenZ, d.screenScale);
        commandSetAudioPrimary(d.primaryAudio.x, d.primaryAudio.y, d.primaryAudio.z, d.primaryAudio.maxVol, d.primaryAudio.minRange, d.primaryAudio.maxRange);
        commandSetAudioSecondary(d.secondaryAudio.x, d.secondaryAudio.y, d.secondaryAudio.z, d.secondaryAudio.maxVol, d.secondaryAudio.minRange, d.secondaryAudio.maxRange);
        if (d.secondaryAudioEnabled != isSecondaryAudioActive()) commandToggleAudioSecondary();
        Mcedia.msgToPlayer("§a[Mcedia] §f预设已应用。");
    }

    public void commandPlaylistAdd(String url) {
        playlistManager.commandPlaylistAdd(url);
    }

    public void commandPlaylistInsert(int index, String url) {
        playlistManager.commandPlaylistInsert(index, url);
    }

    public void commandPlaylistRemove(int index) {
        playlistManager.commandPlaylistRemove(index);
    }

    public void commandPlaylistClear() {
        playlistManager.commandPlaylistClear();
    }

    public Component commandPlaylistList(int page) {
        return playlistManager.commandPlaylistList(page);
    }

    public void commandSetAutoplay(boolean enabled) {
        configManager.videoAutoplay = enabled;
        Mcedia.msgToPlayer(enabled ? "§a[Mcedia] §f自动连播已开启。" : "§e[Mcedia] §f自动连播已关闭。");
    }

    public void commandSetDanmakuEnabled(boolean enabled) {
        if (configManager.danmakuEnable == enabled) return;
        configManager.danmakuEnable = enabled;
        if (enabled) {
            Mcedia.msgToPlayer("§a[Mcedia] §f弹幕已开启。");
            Media m = player.getMedia();
            if (m != null && danmakuManager.isListEmpty()) {
                IMediaInfo i = m.getMediaInfo();
                if (i instanceof VideoInfo vi && vi.getCid() > 0) {
                    Mcedia.msgToPlayer("§7[Mcedia] 正在后台加载弹幕...");
                    DanmakuFetcher.fetchDanmaku(vi.getCid()).thenAcceptAsync(danmakuManager::load, Minecraft.getInstance()::execute);
                }
            }
        } else Mcedia.msgToPlayer("§e[Mcedia] §f弹幕已关闭。");
    }

    public void commandSetDanmakuArea(float percent) {
        configManager.danmakuDisplayArea = Math.clamp(percent / 100.0f, 0.0f, 1.0f);
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7弹幕显示区域已设置为 %.0f%%。", percent));
    }

    public void commandSetDanmakuOpacity(float percent) {
        configManager.danmakuOpacity = Math.clamp(percent / 100.0f, 0.0f, 1.0f);
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7弹幕不透明度已设置为 %.0f%%。", percent));
    }

    public void commandSetDanmakuFontScale(float scale) {
        configManager.danmakuFontScale = Math.max(0.1f, scale);
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7弹幕字体缩放已设置为 %.2f。", scale));
    }

    public void commandSetDanmakuSpeedScale(float scale) {
        configManager.danmakuSpeedScale = Math.max(0.1f, scale);
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7弹幕速度缩放已设置为 %.2f。", scale));
    }

    public void commandSetDanmakuTypeVisible(String type, boolean visible) {
        String typeName = "未知";
        switch (type.toLowerCase()) {
            case "scrolling":
                configManager.showScrollingDanmaku = visible;
                typeName = "滚动弹幕";
                break;
            case "top":
                configManager.showTopDanmaku = visible;
                typeName = "顶部弹幕";
                break;
            case "bottom":
                configManager.showBottomDanmaku = visible;
                typeName = "底部弹幕";
                break;
        }
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7%s已§%s。", typeName, visible ? "a开启" : "e关闭"));
    }

    public void commandSetScreenLightLevel(int level) {
        if (level >= 0 && level <= 15) {
            configManager.customLightLevel = level;
            Mcedia.msgToPlayer(String.format("§a[Mcedia] §f屏幕光照等级已设置为 §e%d§f。", level));
        } else {
            configManager.customLightLevel = -1;
            Mcedia.msgToPlayer("§e[Mcedia] §f屏幕光照已重置为§7跟随世界光照§f。");
        }
    }

    public long getServerDuration() {
        try {
            var args = entity.getMainHandItem().getDisplayName().getString().split(":");
            return (System.currentTimeMillis() - Long.parseLong(args[1].substring(0, args[1].length() - 1))) * 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    public long parseTimestampToMicros(String ts) {
        if (ts == null || ts.isBlank()) return -1;
        ts = ts.trim();
        String[] p = ts.split(":");
        long h = 0, m = 0, s = 0;
        try {
            if (p.length == 1) s = Long.parseLong(p[0]);
            else if (p.length == 2) {
                m = Long.parseLong(p[0]);
                s = Long.parseLong(p[1]);
            } else if (p.length == 3) {
                h = Long.parseLong(p[0]);
                m = Long.parseLong(p[1]);
                s = Long.parseLong(p[2]);
            } else return -1;
        } catch (NumberFormatException e) {
            return -1;
        }
        return (h * 3600L + m * 60L + s) * 1000000L;
    }

    private long parseTimestampFromUrl(String url) {
        if (url == null) return 0;
        try {
            Matcher m = Pattern.compile("[?&]t=([^&]+)").matcher(url);
            if (m.find()) return parseBiliTimestampToUs(m.group(1));
        } catch (Exception e) {
            LOGGER.warn("解析URL时间戳失败", e);
        }
        return 0;
    }

    public long parseBiliTimestampToUs(String t) {
        if (t == null || t.isBlank()) return 0;
        long s = 0;
        if (t.matches(".*[hms].*")) {
            Matcher h = Pattern.compile("(\\d+)h").matcher(t);
            if (h.find()) s += Long.parseLong(h.group(1)) * 3600;
            Matcher m = Pattern.compile("(\\d+)m").matcher(t);
            if (m.find()) s += Long.parseLong(m.group(1)) * 60;
            Matcher ss = Pattern.compile("(\\d+)s").matcher(t);
            if (ss.find()) s += Long.parseLong(ss.group(1));
        } else {
            try {
                s = Long.parseLong(t);
            } catch (Exception e) {
                return 0;
            }
        }
        return s * 1000000L;
    }

    public void switchToCommandSource() {
        this.currentSource = PlaybackSource.COMMAND;
        if (Minecraft.getInstance().player != null)
            Mcedia.msgToPlayer("§e[Mcedia] §f播放列表已切换为指令模式。书本内容将被忽略，直到放入新书。");
    }

    public void switchToBookSource() {
        this.currentSource = PlaybackSource.BOOK;
    }

    public ArmorStand getEntity() {
        return this.entity;
    }

    public MediaPlayer getPlayer() {
        return this.player;
    }

    public DanmakuManager getDanmakuManager() {
        return this.danmakuManager;
    }

    public PlayerConfigManager getConfigManager() {
        return this.configManager;
    }

    public PlaylistManager getPlaylistManager() {
        return this.playlistManager;
    }

    public AudioSource getPrimaryAudioSource() {
        return this.primaryAudioSource;
    }

    public AudioSource getSecondaryAudioSource() {
        return this.secondaryAudioSource;
    }

    @Nullable
    public VideoTexture getTexture() {
        return this.texture;
    }

    @Nullable
    public String getPlayingUrl() {
        return this.playingUrl;
    }

    @Nullable
    public BilibiliBangumiInfo getCurrentBangumiInfo() {
        return this.currentBangumiInfo;
    }

    public PlaybackStatus getStatus() {
        return this.currentStatus;
    }

    public boolean isTextureReady() {
        return this.isTextureReady.get();
    }

    public void setTextureReady(boolean ready) {
        this.isTextureReady.set(ready);
    }

    public boolean isTextureInitialized() {
        return this.isTextureInitialized;
    }

    public void setTextureInitialized(boolean initialized) {
        this.isTextureInitialized = initialized;
    }

    public boolean isPausedByBasePlate() {
        return this.isPausedByBasePlate;
    }

    public void setPausedByBasePlate(boolean paused) {
        this.isPausedByBasePlate = paused;
    }
}
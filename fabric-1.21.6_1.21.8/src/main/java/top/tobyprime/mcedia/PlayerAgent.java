package top.tobyprime.mcedia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.client.McediaRenderTypes;
import top.tobyprime.mcedia.core.*;
import top.tobyprime.mcedia.interfaces.IMediaInfo;
import top.tobyprime.mcedia.manager.DanmakuManager;
import top.tobyprime.mcedia.manager.VideoCacheManager;
import top.tobyprime.mcedia.provider.*;
import top.tobyprime.mcedia.video_fetcher.BilibiliBangumiFetcher;
import top.tobyprime.mcedia.video_fetcher.DanmakuFetcher;
import top.tobyprime.mcedia.video_fetcher.UrlExpander;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
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
    private static final String RICKROLL_URL = "https://www.bilibili.com/video/BV1GJ411x7h7";
    private static final String BAD_APPLE_URL = "https://www.bilibili.com/video/BV1xx411c79H";
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^(\\d{1,2}:)?(\\d{1,2}):(\\d{1,2})$");
    private static final Pattern P_NUMBER_PATTERN = Pattern.compile("^[pP]?(\\d+)$");
    private static final int ITEMS_PER_PAGE_PLAYLIST = 7;

    private long timestampFromUrlUs = 0;
    private boolean isPausedByBasePlate = false;
    private final Queue<PlaybackItem> playlist = new LinkedList<>();
    private PlaybackItem currentPlayingItem = null;
    private String currentPlaylistContent = "";
    private int playlistOriginalSize = 0;
    private final AtomicLong playbackToken = new AtomicLong(0);
    private volatile boolean isReconnecting = false;
    private volatile boolean isApplyingConfigChange = false;

    private enum PlaybackSource {
        BOOK,
        COMMAND
    }

    private PlaybackSource currentSource = PlaybackSource.BOOK;

    private final ArmorStand entity;
    private final MediaPlayer player;
    private final VideoCacheManager cacheManager;
    private final AudioSource audioSource = new AudioSource(Mcedia.getInstance().getAudioExecutor()::schedule);
    private final DanmakuManager danmakuManager = new DanmakuManager();

    @Nullable
    private VideoTexture texture = null;
    public String playingUrl;
    private ItemStack preOffHandItemStack = ItemStack.EMPTY;
    @Nullable
    private BilibiliBangumiInfo currentBangumiInfo = null;
    private boolean videoAutoplay = false;

    private boolean danmakuEnable = false; // 默认开启
    private boolean showScrollingDanmaku = true;
    private boolean showTopDanmaku = true;
    private boolean showBottomDanmaku = true;
    private float danmakuDisplayArea = 1.0f; // 1.0 = 100%
    private float danmakuOpacity = 1.0f;     // 1.0 = 100%
    private float danmakuFontScale = 1.0f;    // 1.0 = 默认大小
    private float danmakuSpeedScale = 1.0f;

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
    private long lastRenderTime = 0;
    private int saveProgressTicker = 0;
    private int customLightLevel = -1;
    private String desiredQuality = "自动";
    private String previousQuality = "自动";
    private String qualityForNextPlayback = "自动";
    private boolean shouldCacheForLoop = false;
    private volatile boolean isLoopingInProgress = false;
    private final AtomicBoolean isTextureReady = new AtomicBoolean(false);
    private boolean isTextureInitialized = false;

    private long finalSeekTimestampUs = 0;

    private enum PlaybackStatus {
        IDLE,
        LOADING,
        PLAYING,
        FAILED
    }

    private enum BiliPlaybackMode {
        NONE,
        SINGLE_PART,
        VIDEO_PLAYLIST,
        BANGUMI_SERIES
    }

    private static class PlaybackItem {
        final String originalUrl;
        int pNumber = 1;
        long timestampUs = 0;
        BiliPlaybackMode mode = BiliPlaybackMode.NONE;
        @Nullable String desiredQuality = null;
        @Nullable String seasonId = null;

        PlaybackItem(String url) {
            this.originalUrl = url;
        }
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
        preloadOffHandConfig();
    }

    private void preloadOffHandConfig() {
        ItemStack offHandItem = entity.getItemInHand(InteractionHand.OFF_HAND);
        preOffHandItemStack = offHandItem.copy();
        List<String> offHandPages = getBookPages(offHandItem);

        if (offHandPages != null) {
            if (!offHandPages.isEmpty()) updateOffset(offHandPages.get(0));
            if (offHandPages.size() > 1) updateAudioOffset(offHandPages.get(1));
            if (offHandPages.size() > 2) updateOther(offHandPages.get(2));
            if (offHandPages.size() > 4) updateDanmakuConfig(offHandPages.get(4));
            else updateDanmakuConfig(null);

            String newQuality = (offHandPages.size() > 3) ? offHandPages.get(3) : null;
            this.desiredQuality = (newQuality == null || newQuality.isBlank()) ? "自动" : newQuality.trim();
            this.previousQuality = this.desiredQuality;
        } else {
            resetOffset();
            resetAudioOffset();
            resetDanmakuConfig();
            this.desiredQuality = "自动";
            this.previousQuality = "自动";
        }
    }

    public void initializeGraphics() {
        if (this.texture == null) {
            this.texture = new VideoTexture(ResourceLocation.fromNamespaceAndPath("mcedia", "player_" + hashCode()));
            player.bindTexture(this.texture);
        }
    }

    public void tick() {
        try {
            update();
            Media currentMedia = player.getMedia();
            if (currentMedia != null && !isReconnecting) {
                if (McediaConfig.RESUME_ON_RELOAD_ENABLED) {
                    if (!currentMedia.isLiveStream() && currentMedia.isPlaying()) {
                        saveProgressTicker++;
                        if (saveProgressTicker >= 100) {
                            saveProgressTicker = 0;
                            Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), currentMedia.getDurationUs());
                        }
                    }
                }
                if (currentMedia.needsReconnect()) {
                    LOGGER.warn("检测到媒体流中断，正在尝试自动重连: {}", playingUrl);
                    isReconnecting = true;
                    this.open(playingUrl);
                    this.startPlayback(false);
                    return;
                }
                if (currentMedia.isEnded()) {
                    if (currentPlayingItem == null) {
                        playNextInQueue();
                        return;
                    }
                    if (this.videoAutoplay) {
                        if (currentPlayingItem.mode == BiliPlaybackMode.BANGUMI_SERIES) {
                            if (playNextBangumiEpisode()) {
                                return;
                            }
                        }
                        if (currentPlayingItem.mode == BiliPlaybackMode.SINGLE_PART && currentPlayingItem.originalUrl.contains("bilibili.com/video/")) {
                            if (playNextBilibiliVideoPart(playingUrl, false)) {
                                return;
                            }
                        }
                    }
                    if (player.looping) {
                        if (!playlist.isEmpty()) {
                            LOGGER.info("列表循环: 将 '{}' 添加到队尾并播放下一个。", currentPlayingItem.originalUrl);
                            playlist.offer(currentPlayingItem);
                            playNextInQueue();
                        } else {
                            LOGGER.info("单项循环: 重新播放 '{}'。", currentPlayingItem.originalUrl);
                            this.open(playingUrl);
                            this.startPlayback(true);
                        }
                    } else if (!playlist.isEmpty()) {
                        LOGGER.info("顺序播放: 播放列表下一项。");
                        playNextInQueue();
                    } else {
                        LOGGER.info("播放列表已结束。");
                        currentPlayingItem = null;
                        open(null);
                        player.closeAsync();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("在 PlayerAgent.tick() 中发生未捕获的异常", e);
            }
    }

    public static long parseTimestampToMicros(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return -1;
        }
        timeStr = timeStr.trim();
        String[] parts = timeStr.split(":");
        long hours = 0, minutes = 0, seconds = 0;

        try {
            if (parts.length == 1) {
                seconds = Long.parseLong(parts[0]);
            } else if (parts.length == 2) {
                minutes = Long.parseLong(parts[0]);
                seconds = Long.parseLong(parts[1]);
            } else if (parts.length == 3) {
                hours = Long.parseLong(parts[0]);
                minutes = Long.parseLong(parts[1]);
                seconds = Long.parseLong(parts[2]);
            } else {
                return -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return (hours * 3600L + minutes * 60L + seconds) * 1_000_000L;
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

    public void update() {
        if (isApplyingConfigChange) {
            return;
        }
        try {
            ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
            List<String> bookPages = getBookPages(mainHandItem);
            String newPlaylistContent = bookPages != null ? String.join("\n", bookPages) : "";
            if (bookPages != null && !bookPages.isEmpty()) {
                this.currentSource = PlaybackSource.BOOK;
            }
            if (!newPlaylistContent.equals(currentPlaylistContent)) {
                LOGGER.info("检测到播放列表(书本)变更，强制中断并更新...");
                currentPlaylistContent = newPlaylistContent;
                player.closeAsync().thenRun(() -> {
                    updatePlaylist(bookPages);
                    playNextInQueue();
                });
                return;
            }

            ItemStack offHandItem = entity.getItemInHand(InteractionHand.OFF_HAND);
            if (!ItemStack.matches(offHandItem, preOffHandItemStack)) {
                LOGGER.info("检测到副手配置书变更，正在应用新设置...");
                isApplyingConfigChange = true;
                List<String> offHandPages = getBookPages(offHandItem);
                if (offHandPages != null) {
                    if (!offHandPages.isEmpty()) updateOffset(offHandPages.get(0));
                    if (offHandPages.size() > 1) updateAudioOffset(offHandPages.get(1));
                    if (offHandPages.size() > 2) updateOther(offHandPages.get(2));
                    if (offHandPages.size() > 4) updateDanmakuConfig(offHandPages.get(4));
                    else resetDanmakuConfig();
                } else {
                    resetOffset();
                    resetAudioOffset();
                    resetDanmakuConfig();
                    updateOther(null);
                }

                String newQualityFromBook = (offHandPages != null && offHandPages.size() > 3) ? offHandPages.get(3) : null;
                String newQuality = (newQualityFromBook == null || newQualityFromBook.isBlank()) ? "自动" : newQualityFromBook.trim();

                boolean qualityChanged = !this.desiredQuality.equals(newQuality);

                if (qualityChanged) {
                    LOGGER.info("检测到清晰度变更: '{}' -> '{}'，正在软重载...", this.desiredQuality, newQuality);
                    this.previousQuality = this.desiredQuality;
                    this.desiredQuality = newQuality;

                    if (player.getMedia() != null && playingUrl != null) {
                        Media currentMedia = player.getMedia();
                        long seekTo = (currentMedia != null && !currentMedia.isLiveStream()) ? currentMedia.getDurationUs() : 0;
                        this.qualityForNextPlayback = this.desiredQuality;
                        this.finalSeekTimestampUs = seekTo;

                        this.open(playingUrl);
                        this.startPlayback(false);
                    } else {
                        isApplyingConfigChange = false;
                        preOffHandItemStack = offHandItem.copy();
                    }
                } else {
                    isApplyingConfigChange = false;
                    preOffHandItemStack = offHandItem.copy();
                }
            }
        } catch (Exception ignored) {
            if (isApplyingConfigChange) {
                isApplyingConfigChange = false;
            }
        }
    }

    private void updatePlaylist(List<String> pages) {
        playlist.clear();
        playlistOriginalSize = 0;
        if (pages == null || pages.isEmpty()) {
            open(null);
            return;
        }

        List<String> lines = new ArrayList<>();
        for (String page : pages) {
            if (page != null && !page.isBlank()) {
                lines.addAll(Arrays.asList(page.split("\n")));
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            String currentLine = lines.get(i).trim();
            if (currentLine.isEmpty()) continue;

            Matcher urlMatcher = URL_PATTERN.matcher(currentLine);
            PlaybackItem item = null;
            if (currentLine.equalsIgnoreCase("rickroll")) {
                item = new PlaybackItem(RICKROLL_URL);
            } else if (currentLine.equalsIgnoreCase("badapple")) {
                item = new PlaybackItem(BAD_APPLE_URL);
            } else if (urlMatcher.find()) {
                item = new PlaybackItem(urlMatcher.group(0).trim());
            }

            if (item == null) continue;

            boolean isBiliVideo = item.originalUrl.contains("bilibili.com/video/");
            boolean isBiliBangumi = item.originalUrl.contains("bilibili.com/bangumi/play/");

            if (isBiliVideo) {
                item.mode = BiliPlaybackMode.SINGLE_PART;
            } else if (isBiliBangumi) {
                item.mode = BiliPlaybackMode.BANGUMI_SERIES;
                Pattern ssPattern = Pattern.compile("/ss(\\d+)");
                Matcher ssMatcher = ssPattern.matcher(item.originalUrl);
                if (ssMatcher.find()) {
                    item.seasonId = ssMatcher.group(1);
                }
            }

            int nextLineIndex = i + 1;
            while (nextLineIndex < lines.size()) {
                String parameterLine = lines.get(nextLineIndex).trim();
                if (parameterLine.isEmpty() || URL_PATTERN.matcher(parameterLine).find() || parameterLine.equalsIgnoreCase("rickroll") || parameterLine.equalsIgnoreCase("badapple")) {
                    break;
                }

                String[] parts = parameterLine.split("\\s+");
                List<String> remainingParts = new ArrayList<>(Arrays.asList(parts));

                Iterator<String> iterator = remainingParts.iterator();
                while (iterator.hasNext()) {
                    String part = iterator.next();
                    Matcher timeMatcher = TIMESTAMP_PATTERN.matcher(part);
                    Matcher pNumMatcher = P_NUMBER_PATTERN.matcher(part);
                    if (timeMatcher.matches() || part.matches("^\\d+$")) {
                        item.timestampUs = parseTimestampToMicros(part);
                        iterator.remove();
                    } else if (pNumMatcher.matches()) {
                        if (isBiliVideo && item.mode == BiliPlaybackMode.VIDEO_PLAYLIST || isBiliBangumi) {
                            item.pNumber = Integer.parseInt(pNumMatcher.group(1));
                        }
                        iterator.remove();
                    }
                }
                if (!remainingParts.isEmpty()) {
                    item.desiredQuality = String.join(" ", remainingParts);
                }

                nextLineIndex++;
            }
            playlist.offer(item);
            playlistOriginalSize++;
            LOGGER.info("添加项目: URL='{}', Mode={}, P={}, Timestamp={}us, Quality='{}'", item.originalUrl, item.mode, item.pNumber, item.timestampUs, item.desiredQuality);
            i = nextLineIndex - 1;
        }
        LOGGER.info("播放列表更新完成，共找到 {} 个媒体项目。", playlistOriginalSize);
    }

    private void playNextInQueue() {
        if (McediaConfig.RESUME_ON_RELOAD_ENABLED && this.currentPlayingItem != null) {
            Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), 0);
        }
        PlaybackItem nextItem = playlist.poll();
        if (nextItem != null) {
            this.currentPlayingItem = nextItem;
            String urlToPlay = nextItem.originalUrl;

            if (nextItem.mode == BiliPlaybackMode.VIDEO_PLAYLIST) {
                if (!urlToPlay.contains("?p=") && !urlToPlay.contains("&p=")) {
                    urlToPlay += (urlToPlay.contains("?") ? "&" : "?") + "p=" + nextItem.pNumber;
                }
            } else if (nextItem.mode == BiliPlaybackMode.BANGUMI_SERIES && nextItem.seasonId != null && nextItem.pNumber > 1) {
                urlToPlay += (urlToPlay.contains("?") ? "&" : "?") + "p=" + nextItem.pNumber;
            }

            this.finalSeekTimestampUs = 0;
            long serverSyncTime = getServerDuration();
            if (serverSyncTime > 0) {
                this.finalSeekTimestampUs = serverSyncTime;
                LOGGER.info("应用服务器实时同步时间: {}us", serverSyncTime);
                if (McediaConfig.RESUME_ON_RELOAD_ENABLED) {
                    Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), 0);
                }
            } else {
                if (McediaConfig.RESUME_ON_RELOAD_ENABLED) {
                    long resumeTime = Mcedia.getInstance().loadPlayerProgress(this.entity.getUUID());
                    if (resumeTime > 0) {
                        this.finalSeekTimestampUs += resumeTime;
                        LOGGER.info("读取到断点续播时间: {}us", resumeTime);
                    }
                }
                this.finalSeekTimestampUs += nextItem.timestampUs;
                this.finalSeekTimestampUs += parseBiliTimestampToUs(nextItem.originalUrl);
            }

            this.qualityForNextPlayback = (nextItem.desiredQuality != null && !nextItem.desiredQuality.isBlank())
                    ? nextItem.desiredQuality
                    : this.desiredQuality;

            LOGGER.info("准备播放: URL='{}', Mode={}, P={}, 最终跳转时间={}us", urlToPlay, nextItem.mode, nextItem.pNumber, this.finalSeekTimestampUs);

            this.open(urlToPlay);
            this.startPlayback(false);
        } else {
            this.currentPlayingItem = null;
            LOGGER.info("播放列表已为空，播放结束。");
            this.open(null);
            this.currentStatus = PlaybackStatus.IDLE;
            player.closeAsync();
        }
    }

    private boolean playNextBilibiliVideoPart(String finishedUrl, boolean forceLoopFromStart) {
        String bvid = parseBvidFromUrl(finishedUrl);
        int currentP = parsePNumberFromUrl(finishedUrl);
        if (bvid == null) {
            return false;
        }
        final int nextP = currentP + 1;

        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid))
                        .build();
                HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject json = new JSONObject(response.body());
                if (json.getInt("code") == 0) {
                    JSONArray pages = json.getJSONObject("data").getJSONArray("pages");
                    if (nextP <= pages.length()) {
                        String nextUrl = "https://www.bilibili.com/video/" + bvid + "?p=" + nextP;
                        LOGGER.info("B站分P连播: 找到下一P ({}/{})，正在加载...", nextP, pages.length());
                        this.currentPlayingItem.pNumber = nextP;
                        this.finalSeekTimestampUs = 0;
                        Minecraft.getInstance().execute(() -> {
                            this.open(nextUrl);
                            this.startPlayback(false);
                        });
                        return false;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("检查B站下一P时出错", e);
            }
            return false;
        });
        try {
            return future.join();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean playNextBangumiEpisode() {
        if (currentBangumiInfo == null) return false;

        BilibiliBangumiInfo.Episode nextEpisode = currentBangumiInfo.getNextEpisode();

        if (nextEpisode != null) {
            LOGGER.info("番剧连播: 找到下一集 '{}', 正在加载...", nextEpisode.title);
            String nextUrl = "https://www.bilibili.com/bangumi/play/ep" + nextEpisode.epId;
            this.finalSeekTimestampUs = 0;
            Minecraft.getInstance().execute(() -> {
                this.open(nextUrl);
                this.startPlayback(false);
            });
            return true;
        } else {
            LOGGER.info("番剧 '{}' 已播放完毕。", currentBangumiInfo.title);
            return false;
        }
    }

    /**
     * 从B站URL中解析BV号
     */
    private String parseBvidFromUrl(String url) {
        Pattern pattern = Pattern.compile("video/(BV[a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从B站URL中解析P号，如果不存在则默认为1
     */
    private int parsePNumberFromUrl(String url) {
        try {
            Pattern pattern = Pattern.compile("[?&]p=(\\d+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
        }
        return 1;
    }

    public void open(@Nullable String mediaUrl) {
        playingUrl = mediaUrl;
        isReconnecting = false;
    }

    private void startPlayback(boolean isLooping) {
        this.isReconnecting = false;
        this.currentStatus = PlaybackStatus.LOADING;
        final long currentToken = this.playbackToken.incrementAndGet();
        this.isTextureReady.set(false);
        this.isPausedByBasePlate = false;
        player.closeAsync().thenRun(() -> {
            if (playbackToken.get() != currentToken) {
                LOGGER.debug("Playback token {} is outdated, aborting start.", currentToken);
                isApplyingConfigChange = false;
                return;
            }

            if (playingUrl == null) {
                isApplyingConfigChange = false;
                return;
            }

            final String initialUrl = playingUrl;
            if (McediaConfig.CACHING_ENABLED && cacheManager.isCached(initialUrl)) {
                VideoInfo cachedInfo = cacheManager.getCachedVideoInfo(initialUrl);
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
                                long durationToSeek = this.finalSeekTimestampUs;
                                if (durationToSeek > 0) player.seek(durationToSeek);
                            }
                        }
                        player.play();
                        player.setSpeed(speed);
                        this.preOffHandItemStack = this.entity.getItemInHand(InteractionHand.OFF_HAND).copy();
                        this.isApplyingConfigChange = false;
                    } else {
                        LOGGER.error("从缓存打开媒体后未能获取Media实例，回退到网络播放。");
                        fallbackToNetworkPlayback(initialUrl, isLooping, currentToken);
                    }
                    return;
                }
            }
            fallbackToNetworkPlayback(initialUrl, isLooping, currentToken);
        });
    }

    private void fallbackToNetworkPlayback(String initialUrl, boolean isLooping, long currentToken) {
        CompletableFuture<VideoInfo> videoInfoFuture = UrlExpander.expand(initialUrl)
                .thenComposeAsync(expandedUrl -> {
                    if (playbackToken.get() != currentToken) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Playback aborted by new request."));
                    }

                    this.timestampFromUrlUs = parseTimestampFromUrl(expandedUrl);
                    IMediaProvider provider = MediaProviderRegistry.getInstance().getProviderForUrl(expandedUrl);

                    if (provider != null) {
                        String warning = provider.getSafetyWarning();
                        if (warning != null && !warning.isEmpty()) Mcedia.msgToPlayer(warning);
                    }
                    LOGGER.info(isLooping ? "正在重新加载循环..." : "准备从网络播放 {}...", expandedUrl);

                    final String cookie = (provider instanceof BilibiliVideoProvider || provider instanceof BilibiliBangumiProvider)
                            ? McediaConfig.BILIBILI_COOKIE : null;
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            if (provider == null) {
                                throw new UnsupportedOperationException("No provider found for URL: " + expandedUrl);
                            }
                            if (provider instanceof BilibiliBangumiProvider) {
                                BilibiliBangumiInfo bangumiInfo = BilibiliBangumiFetcher.fetch(expandedUrl, cookie, this.qualityForNextPlayback);
                                this.currentBangumiInfo = bangumiInfo;
                                return bangumiInfo.getVideoInfo();
                            } else {
                                this.currentBangumiInfo = null;
                                return provider.resolve(expandedUrl, cookie, this.qualityForNextPlayback);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, Mcedia.getInstance().getBackgroundExecutor());
                }, Mcedia.getInstance().getBackgroundExecutor());

        videoInfoFuture.handleAsync((videoInfo, throwable) -> {
            if (playbackToken.get() != currentToken) {
                LOGGER.debug("Playback token {} is outdated, aborting final handler.", currentToken);
                return null;
            }

            if (throwable != null) {
                if (!(throwable.getCause() instanceof IllegalStateException)) {
                    Minecraft.getInstance().execute(() -> {
                        handlePlaybackFailure(throwable, initialUrl, initialUrl, isLooping);
                    });
                }
            } else {
                try {
                    player.openSync(videoInfo, null, 0);
                    Minecraft.getInstance().execute(() -> {
                        if (playbackToken.get() != currentToken) {
                            LOGGER.debug("Playback token {} became outdated during main thread scheduling.", currentToken);
                            player.closeAsync();
                            return;
                        }
                        IMediaProvider provider = MediaProviderRegistry.getInstance().getProviderForUrl(initialUrl);
                        handlePlaybackSuccess(videoInfo, initialUrl, isLooping, provider);
                    });
                } catch (Exception e) {
                    LOGGER.error("在后台线程执行 openSync 时失败", e);
                    Minecraft.getInstance().execute(() -> {
                        handlePlaybackFailure(e, initialUrl, initialUrl, isLooping);
                    });
                }
            }
            return null;
        }, Mcedia.getInstance().getBackgroundExecutor());
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
        this.isTextureInitialized = false;
        Media media = player.getMedia();
        if (media == null) {
            LOGGER.error("视频加载成功但Media对象为空，这是一个严重错误。");
            return;
        }

        CompletableFuture.runAsync(() -> {
            danmakuManager.clear();
            if (this.danmakuEnable && videoInfo.getCid() > 0) {
                LOGGER.info("正在获取B站内容(cid={})的弹幕...", videoInfo.getCid());
                DanmakuFetcher.fetchDanmaku(videoInfo.getCid())
                        .thenAcceptAsync(danmakuManager::load, Minecraft.getInstance()::execute);
            }
        });

        if (shouldCacheForLoop && !media.isLiveStream() && !cacheManager.isCached(finalMediaUrl) && !cacheManager.isCaching(finalMediaUrl)) {
            LOGGER.info("正在为循环播放在后台缓存视频: {}", finalMediaUrl);
            String cookie = (provider != null && provider.getClass().getSimpleName().toLowerCase().contains("bilibili"))
                    ? McediaConfig.BILIBILI_COOKIE
                    : null;
            cacheManager.cacheVideoAsync(finalMediaUrl, videoInfo, cookie)
                    .handle((unused, cacheThrowable) -> {
                        if (cacheThrowable != null)
                            Mcedia.msgToPlayer("§e[Mcedia] §c视频后台缓存失败: " + finalMediaUrl);
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
            Style clickableStyle = Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(finalMediaUrl)))
                    .withHoverEvent(hoverEvent);
            MutableComponent msg = Component.literal("§a[Mcedia] §f播放: ");
            msg.append(Component.literal(videoInfo.getTitle()).withStyle(clickableStyle.withColor(ChatFormatting.YELLOW)));
            if (videoInfo.isMultiPart() && videoInfo.getPartName() != null && !videoInfo.getPartName().isEmpty()) {
                msg.append(Component.literal(" (P" + videoInfo.getPartNumber() + ": " + videoInfo.getPartName() + ")")
                        .withStyle(clickableStyle.withColor(ChatFormatting.GOLD)));
            }
            msg.append(Component.literal(" - ").withStyle(ChatFormatting.WHITE));
            msg.append(Component.literal(videoInfo.getAuthor()).withStyle(clickableStyle.withColor(ChatFormatting.AQUA)));
            if (videoInfo.getCurrentQuality() != null) {
                msg.append(Component.literal(" [").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(videoInfo.getCurrentQuality()).withStyle(ChatFormatting.DARK_PURPLE))
                        .append(Component.literal("]").withStyle(ChatFormatting.GRAY));
            }
            Mcedia.msgToPlayer(msg);
        }

        if (!media.isLiveStream()) {
            if (provider != null && provider.isSeekSupported()) {
                long durationToSeek = isLooping ? 0 : this.finalSeekTimestampUs;
                if (durationToSeek > 0) {
                    LOGGER.info("视频加载成功，将在 {} us 处开始播放 (支持跳转)。", durationToSeek);
                    player.seek(durationToSeek);
                    danmakuManager.seek(durationToSeek);
                } else {
                    LOGGER.info("视频加载成功，将从头开始播放。");
                    danmakuManager.seek(0);
                }
            } else {
                LOGGER.warn("当前视频源 ({}) 不支持跳转操作，将从头开始播放。", provider != null ? provider.getClass().getSimpleName() : "未知直链");
            }
        } else {
            LOGGER.info("直播流加载成功，直接开始播放。");
        }

        player.play();
        player.setSpeed(speed);
        this.preOffHandItemStack = this.entity.getItemInHand(InteractionHand.OFF_HAND).copy();
        this.isApplyingConfigChange = false;
    }

    private void handlePlaybackFailure(Throwable throwable, String initialUrl, String finalMediaUrl, boolean isLooping) {
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
        this.preOffHandItemStack = this.entity.getItemInHand(InteractionHand.OFF_HAND).copy();
        this.isApplyingConfigChange = false;
    }

    private float halfW = 1.777f;

    public void render(ArmorStandRenderState state, MultiBufferSource bufferSource, PoseStack poseStack, int i) {
//        if (!this.isTextureReady.get()) return;
        Media media = player.getMedia();
        if (media != null) {
            if (!isTextureInitialized && texture != null && media.getWidth() > 0 && media.getHeight() > 0) {
                LOGGER.debug("检测到视频尺寸 ({}x{}), 正在初始化纹理...", media.getWidth(), media.getHeight());
                texture.prepareAndPrewarm(media.getWidth(), media.getHeight(), () -> this.isTextureReady.set(true));
                isTextureInitialized = true;
            }
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

        float yRotRadians = (float) -Math.toRadians(state.yRot);
        var primaryAudioOffsetRotated = new Vector3f(audioOffsetX, audioOffsetY, audioOffsetZ).rotateY(yRotRadians);
        primaryAudioSource.setVolume(audioMaxVolume * volumeFactor);
        primaryAudioSource.setRange(audioRangeMin, audioRangeMax);
        primaryAudioSource.setPos(((float) state.x + primaryAudioOffsetRotated.x), ((float) state.y + primaryAudioOffsetRotated.y), ((float) state.z + primaryAudioOffsetRotated.z));
        if (isSecondarySourceActive) {
            var secondaryAudioOffsetRotated = new Vector3f(audioOffsetX2, audioOffsetY2, audioOffsetZ2).rotateY(yRotRadians);
            secondaryAudioSource.setVolume(audioMaxVolume2 * volumeFactor);
            secondaryAudioSource.setRange(audioRangeMin2, audioRangeMax2);
            secondaryAudioSource.setPos(((float) state.x + secondaryAudioOffsetRotated.x), ((float) state.y + secondaryAudioOffsetRotated.y), ((float) state.z + secondaryAudioOffsetRotated.z));
        }
        long now = System.nanoTime();
        if (lastRenderTime == 0) lastRenderTime = now;
        float deltaTime = (now - lastRenderTime) / 1_000_000_000.0f;
        lastRenderTime = now;

        if (media != null && media.isPlaying() && danmakuEnable) {
            danmakuManager.update(deltaTime, media.getDurationUs(), this.halfW, this.danmakuFontScale, this.danmakuSpeedScale,
                    this.showScrollingDanmaku, this.showTopDanmaku, this.showBottomDanmaku);
        }
        synchronized (player) {
            Media currentMedia = player.getMedia();
            if (media != null) {
                media.uploadVideo();
                if (media.getHeight() > 0) halfW = media.getAspectRatio();
            } else halfW = 1.777f;
        }

        int finalLightValue;
        if (this.customLightLevel != -1) {
            finalLightValue = LightTexture.pack(this.customLightLevel, this.customLightLevel);
        } else {
            finalLightValue = i;
        }

        poseStack.pushPose();
        try {
            poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.yRot), 0, 0));
            poseStack.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-state.headPose.x()), (float) Math.toRadians(-state.headPose.y()), (float) Math.toRadians(-state.headPose.z())));
            poseStack.translate(offsetX, offsetY + 1.02 * state.scale, offsetZ + 0.6 * state.scale);
            poseStack.scale(size, size, size);

            renderScreen(poseStack, bufferSource, finalLightValue);

            if (player.getMedia() != null && this.isTextureReady.get()) {
                if(danmakuEnable) {
                    renderDanmakuWithClipping(poseStack, bufferSource, finalLightValue);
                }
                renderProgressBar(poseStack, bufferSource, player.getProgress(), finalLightValue);
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
                if (player.isBuffering()) {
                    screenTexture = loadingScreen;
                } else if (player.getMedia() != null && this.isTextureReady.get()) {
                    screenTexture = this.texture.getResourceLocation();
                } else {
                    screenTexture = idleScreen;
                }
                break;
            case IDLE:
            default:
                screenTexture = idleScreen;
                break;
        }
        RenderType renderType = RenderType.entityCutoutNoCull(screenTexture);

        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        var matrix = poseStack.last().pose();

        consumer.addVertex(matrix, -halfW, -1, 0).setUv(0, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, halfW, -1, 0).setUv(1, 1).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, halfW, 1, 0).setUv(1, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
        consumer.addVertex(matrix, -halfW, 1, 0).setUv(0, 0).setColor(-1).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(0, 0, 1).setLight(i);
    }

    private void renderProgressBar(PoseStack poseStack, MultiBufferSource bufferSource, float progress, int i) {
        float barHeight = 1f / 50f;
        float barY = -1f;
        float barLeft = -halfW;
        float barRight = halfW;
        float zOffsetBg = 0.002f;
        float zOffsetFg = 0.003f;

        VertexConsumer consumer = bufferSource.getBuffer(McediaRenderTypes.PROGRESS_BAR);

        consumer.addVertex(poseStack.last().pose(), barLeft, barY - barHeight, zOffsetBg).setColor(0.0f, 0.0f, 0.0f, 0.5f).setLight(i);
        consumer.addVertex(poseStack.last().pose(), barRight, barY - barHeight, zOffsetBg).setColor(0.0f, 0.0f, 0.0f, 0.5f).setLight(i);
        consumer.addVertex(poseStack.last().pose(), barRight, barY, zOffsetBg).setColor(0.0f, 0.0f, 0.0f, 0.5f).setLight(i);
        consumer.addVertex(poseStack.last().pose(), barLeft, barY, zOffsetBg).setColor(0.0f, 0.0f, 0.0f, 0.5f).setLight(i);

        if (progress > 0) {
            float progressRight = barLeft + (barRight - barLeft) * Math.max(0, Math.min(progress, 1));
            consumer.addVertex(poseStack.last().pose(), barLeft, barY - barHeight, zOffsetFg).setColor(1.0f, 1.0f, 1.0f, 1.0f).setLight(i);
            consumer.addVertex(poseStack.last().pose(), progressRight, barY - barHeight, zOffsetFg).setColor(1.0f, 1.0f, 1.0f, 1.0f).setLight(i);
            consumer.addVertex(poseStack.last().pose(), progressRight, barY, zOffsetFg).setColor(1.0f, 1.0f, 1.0f, 1.0f).setLight(i);
            consumer.addVertex(poseStack.last().pose(), barLeft, barY, zOffsetFg).setColor(1.0f, 1.0f, 1.0f, 1.0f).setLight(i);
        }
    }

    private void renderDanmakuWithClipping(PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        renderDanmakuText(poseStack, bufferSource, light);
    }

    private void renderDanmakuText(PoseStack poseStack, MultiBufferSource bufferSource, int light) {
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.004f);

        Font font = Minecraft.getInstance().font;
        float videoHeightUnits = 2.0f;

        // 从全局配置读取基础轨道数，并根据游戏内设置缩放
        int dynamicTrackCount = (int) (McediaConfig.DANMAKU_BASE_TRACK_COUNT / this.danmakuFontScale);
        if (dynamicTrackCount < 1) dynamicTrackCount = 1;

        float desiredDanmakuHeight = videoHeightUnits / dynamicTrackCount;
        float scale = desiredDanmakuHeight / font.lineHeight;

        poseStack.scale(scale, -scale, scale);

        // 计算缩放后的渲染坐标系尺寸
        float scaledScreenWidth = (halfW * 2) / scale;
        float scaledScreenHeight = videoHeightUnits / scale;
        float screenLeftEdge = -scaledScreenWidth / 2;
        float screenRightEdge = scaledScreenWidth / 2;

        // 计算最终的透明度
        int alpha = (int) (Mth.clamp(this.danmakuOpacity, 0, 1) * 255);
        int alphaMask = alpha << 24;

        // 遍历唯一的活跃弹幕列表
        for (Danmaku danmaku : danmakuManager.getActiveDanmaku()) {
            float theoreticalXPos = -scaledScreenWidth / 2 + danmaku.x * scaledScreenWidth;

            float yPos;
            if (danmaku.type == Danmaku.DanmakuType.BOTTOM) {
                // 底部弹幕的Y坐标从下向上计算
                yPos = scaledScreenHeight / 2 - (danmaku.y * this.danmakuDisplayArea * scaledScreenHeight) - font.lineHeight;
            } else {
                // 滚动和顶部弹幕的Y坐标从上向下计算
                yPos = -scaledScreenHeight / 2 + (danmaku.y * this.danmakuDisplayArea * scaledScreenHeight);
            }

            int finalColor = alphaMask | (danmaku.color & 0x00FFFFFF);

            // 根据弹幕类型应用不同的渲染策略
            if (danmaku.type == Danmaku.DanmakuType.SCROLLING) {
                // --- 滚动弹幕应用平滑像素裁剪逻辑 ---
                float danmakuWidthInPixels = font.width(danmaku.text);

                // 如果弹幕完全在屏幕可视范围之外，则跳过
                if (theoreticalXPos > screenRightEdge || theoreticalXPos + danmakuWidthInPixels < screenLeftEdge) {
                    continue;
                }

                String textToRender = danmaku.text;
                float finalXPos = theoreticalXPos;

                // 处理左边界裁剪 (弹幕从左边消失)
                if (theoreticalXPos < screenLeftEdge) {
                    float overflowWidth = screenLeftEdge - theoreticalXPos;
                    int charsToClip = 0;
                    float clippedWidth = 0;
                    for (int i = 0; i < danmaku.text.length(); i++) {
                        clippedWidth += font.width(String.valueOf(danmaku.text.charAt(i)));
                        if (clippedWidth >= overflowWidth) {
                            charsToClip = i;
                            float prevCharsWidth = font.width(danmaku.text.substring(0, charsToClip));
                            finalXPos = theoreticalXPos + prevCharsWidth;
                            break;
                        }
                    }
                    if (charsToClip > 0 && charsToClip <= danmaku.text.length()) {
                        textToRender = danmaku.text.substring(charsToClip);
                    }
                }

                // 处理右边界裁剪 (弹幕从右边出现)
                float rightEdgePos = finalXPos + font.width(textToRender);
                if (rightEdgePos > screenRightEdge) {
                    float overflowWidth = rightEdgePos - screenRightEdge;
                    int charsToClip = 0;
                    float clippedWidth = 0;
                    for (int i = textToRender.length() - 1; i >= 0; i--) {
                        clippedWidth += font.width(String.valueOf(textToRender.charAt(i)));
                        if (clippedWidth >= overflowWidth) {
                            charsToClip = textToRender.length() - (i + 1);
                            break;
                        }
                    }
                    if (charsToClip > 0 && charsToClip <= textToRender.length()) {
                        textToRender = textToRender.substring(0, textToRender.length() - charsToClip);
                    }
                }

                if (textToRender.isEmpty()) {
                    continue;
                }

                font.drawInBatch(textToRender, finalXPos, yPos, finalColor, true, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);

            } else {
                // --- 固定弹幕 (顶部/底部) 的渲染逻辑 ---
                // 直接居中渲染，不需要裁剪
                font.drawInBatch(danmaku.text, theoreticalXPos, yPos, finalColor, true, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, light);
            }
        }

        poseStack.popPose();
    }

    private void updateDanmakuConfig(@Nullable String pageContent) {
        if (pageContent == null || pageContent.isBlank()) {
            resetDanmakuConfig();
            return;
        }

        String[] lines = pageContent.split("\n");

        this.danmakuEnable = lines.length > 0 && lines[0].contains("弹幕");

        if (lines.length > 1) this.danmakuDisplayArea = parsePercentage(lines[1]);
        else this.danmakuDisplayArea = 1.0f;

        if (lines.length > 2) this.danmakuOpacity = parsePercentage(lines[2]);
        else this.danmakuOpacity = 1.0f;

        if (lines.length > 3) this.danmakuFontScale = parseFloat(lines[3], 1.0f);
        else this.danmakuFontScale = 1.0f;

        if (lines.length > 4) this.danmakuSpeedScale = parseFloat(lines[4], 1.0f);
        else this.danmakuSpeedScale = 1.0f;

        this.showScrollingDanmaku = true;
        this.showTopDanmaku = true;
        this.showBottomDanmaku = true;

        if (lines.length > 5) {
            List<String> configLines = new ArrayList<>();
            for (int i = 5; i < lines.length; i++) {
                configLines.add(lines[i]);
            }
            String combinedConfig = String.join(" ", configLines).toLowerCase(); // 转为小写以便匹配

            if (combinedConfig.contains("屏蔽滚动")) {
                this.showScrollingDanmaku = false;
            } else if (combinedConfig.contains("显示滚动")) {
                this.showScrollingDanmaku = true;
            }

            if (combinedConfig.contains("屏蔽顶部")) {
                this.showTopDanmaku = false;
            } else if (combinedConfig.contains("显示顶部")) {
                this.showTopDanmaku = true;
            }

            if (combinedConfig.contains("屏蔽底部")) {
                this.showBottomDanmaku = false;
            } else if (combinedConfig.contains("显示底部")) {
                this.showBottomDanmaku = true;
            }
        }
    }

    private void resetDanmakuConfig() {
        this.danmakuEnable = false;
        this.danmakuDisplayArea = 1.0f;
        this.danmakuOpacity = 1.0f;
        this.danmakuFontScale = 1.0f;
        this.danmakuSpeedScale = 1.0f;
        this.showScrollingDanmaku = true;
        this.showTopDanmaku = true;
        this.showBottomDanmaku = true;
    }

    private float parsePercentage(String line) {
        try {
            String numericPart = line.replaceAll("[^\\d.]", "");
            if (numericPart.isBlank()) return 1.0f;
            float value = Float.parseFloat(numericPart);
            return Mth.clamp(value / 100.0f, 0.0f, 1.0f);
        } catch (NumberFormatException e) {
            return 1.0f;
        }
    }

    private float parseFloat(String line, float defaultValue) {
        try {
            String numericPart = line.replaceAll("[^\\d.]", "");
            if (numericPart.isBlank()) return defaultValue;
            return Float.parseFloat(numericPart);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public CompletableFuture<Void> shutdownAsync() {
        LOGGER.info("正在异步关闭 PlayerAgent，实体位于 {}", entity.position());
        this.currentStatus = PlaybackStatus.IDLE;
        playingUrl = null;
        isLoopingInProgress = false;

        return CompletableFuture.runAsync(() -> {
            // 在后台线程执行所有耗时操作
            if (McediaConfig.RESUME_ON_RELOAD_ENABLED) {
                Media currentMedia = player.getMedia();
                if (currentMedia != null && !currentMedia.isLiveStream() && currentMedia.getDurationUs() > 0) {
                    Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), currentMedia.getDurationUs());
                    LOGGER.debug("异步关闭：已保存最终播放进度。");
                } else {
                    Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), 0);
                }
            }
            danmakuManager.clear();
            player.closeSync(); // 在后台线程执行同步关闭，确保所有资源被释放
        }, Mcedia.getInstance().getBackgroundExecutor());
    }

    public void closeSync() {
        danmakuManager.clear();
        if (McediaConfig.RESUME_ON_RELOAD_ENABLED) {
            Media currentMedia = player.getMedia();
            if (currentMedia != null && !currentMedia.isLiveStream() && currentMedia.getDurationUs() > 0) {
                Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), currentMedia.getDurationUs());
                LOGGER.info("实例关闭，已保存最终播放进度。");
            } else {
                Mcedia.getInstance().savePlayerProgress(this.entity.getUUID(), 0);
            }
        }
        this.currentStatus = PlaybackStatus.IDLE;
        playingUrl = null;
        isLoopingInProgress = false;
        player.closeAsync();
        LOGGER.info("PlayerAgent已关闭，实体位于 {}", entity.position());
    }

    public ArmorStand getEntity() {
        return this.entity;
    }

    @Nullable
    private List<String> getBookPages(ItemStack bookStack) {
        boolean isTextFilteringEnabled = Minecraft.getInstance().isTextFilteringEnabled();
        if (bookStack.is(Items.WRITABLE_BOOK)) {
            WritableBookContent content = bookStack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (content != null) return content.getPages(isTextFilteringEnabled).toList();
        } else if (bookStack.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = bookStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null)
                return content.getPages(isTextFilteringEnabled).stream().map(Component::getString).collect(Collectors.toList());
        }
        return null;
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

    public void updateOther(String pageContent) {
        if (pageContent == null) {
            this.player.setLooping(false);
            this.shouldCacheForLoop = false;
            this.videoAutoplay = false;
            this.customLightLevel = -1;
            return;
        }
        String[] lines = pageContent.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().toLowerCase();
            if (i == 0 && line.equals("looping")) {
                this.player.setLooping(true);
                this.shouldCacheForLoop = McediaConfig.CACHING_ENABLED;
            } else if (i == 1 && line.equals("autoplay")) {
                this.videoAutoplay = true;
            } else if (line.startsWith("light:")) {
                try {
                    String valueStr = line.substring("light:".length()).trim();
                    int light = Integer.parseInt(valueStr);
                    this.customLightLevel = Mth.clamp(light, 0, 15);
                } catch (Exception e) {
                }
            } else if (line.equals("looping")) {
                this.player.setLooping(true);
                this.shouldCacheForLoop = McediaConfig.CACHING_ENABLED;
            } else if (line.equals("autoplay")) {
                this.videoAutoplay = true;
            }
        }
    }

    public boolean updateQuality(String quality) {
        this.previousQuality = this.desiredQuality;
        String newQuality = (quality == null || quality.isBlank()) ? "自动" : quality.trim();
        if (!this.desiredQuality.equals(newQuality)) {
            this.desiredQuality = newQuality;
            return true;
        }
        return false;
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
        danmakuManager.clear();
        open(null);
        player.closeAsync();
        Mcedia.msgToPlayer("§c[Mcedia] §f播放已停止。");
    }

    public void commandSkip() {
        if (!playlist.isEmpty()) {
            Mcedia.msgToPlayer("§f[Mcedia] §7正在跳过当前视频...");
            playNextInQueue();
        } else {
            Mcedia.msgToPlayer("§e[Mcedia] §f播放列表中没有下一个视频。");
        }
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
            this.audioMaxVolume = (volumePercent / 100.0f) * 10.0f;
            Mcedia.msgToPlayer(String.format("§f[Mcedia] §7音量已设置为 %.0f%%", volumePercent));
        } else {
            Mcedia.msgToPlayer("§c[Mcedia] §f音量百分比必须在 0-100 之间。");
        }
    }

    public void commandSetUrl(String url) {
        LOGGER.info("通过指令设置播放URL: {}", url);
        this.currentSource = PlaybackSource.COMMAND;
        List<String> pages = Collections.singletonList(url);
        this.currentPlaylistContent = String.join("\n", pages);
        player.closeAsync().thenRun(() -> {
            updatePlaylist(pages);
            playNextInQueue();
            Mcedia.msgToPlayer("§f[Mcedia] §7正在尝试播放新链接...");
        });
    }

    public void commandSetOffset(float x, float y, float z, float scale) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        this.scale = scale;
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7屏幕偏移已设置为 (%.2f, %.2f, %.2f)，缩放为 %.2f。", x, y, z, scale));
    }

    public void commandSetLooping(boolean enabled) {
        this.player.setLooping(enabled);
        this.shouldCacheForLoop = enabled && McediaConfig.CACHING_ENABLED;
        if (enabled) {
            Mcedia.msgToPlayer("§a[Mcedia] §f已开启循环。");
        } else {
            Mcedia.msgToPlayer("§e[Mcedia] §f已关闭循环。");
        }
    }

    public void commandSetAudioPrimary(float x, float y, float z, float maxVol, float minRange, float maxRange) {
        this.audioOffsetX = x;
        this.audioOffsetY = y;
        this.audioOffsetZ = z;
        this.audioMaxVolume = maxVol;
        this.audioRangeMin = minRange;
        this.audioRangeMax = maxRange;
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7主声源已设置为: 偏移(%.2f, %.2f, %.2f), 音量 %.1f, 范围 [%.1f, %.1f]。", x, y, z, maxVol, minRange, maxRange));
    }

    public void commandSetAudioSecondary(float x, float y, float z, float maxVol, float minRange, float maxRange) {
        this.audioOffsetX2 = x;
        this.audioOffsetY2 = y;
        this.audioOffsetZ2 = z;
        this.audioMaxVolume2 = maxVol;
        this.audioRangeMin2 = minRange;
        this.audioRangeMax2 = maxRange;
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7副声源参数已更新为: 偏移(%.2f, %.2f, %.2f), 音量 %.1f, 范围 [%.1f, %.1f]。", x, y, z, maxVol, minRange, maxRange));
        if (!isSecondarySourceActive) {
            Mcedia.msgToPlayer("§7提示: 副声源当前未启用，使用 §a/mcedia control enable secondary_audio §7来启用。");
        }
    }

    public boolean commandToggleAudioSecondary() {
        if (isSecondarySourceActive) {
            player.unbindAudioSource(secondaryAudioSource);
            isSecondarySourceActive = false;
            Mcedia.msgToPlayer("§e[Mcedia] §f副声源已禁用。");
            return false;
        } else {
            player.bindAudioSource(secondaryAudioSource);
            isSecondarySourceActive = true;
            Mcedia.msgToPlayer("§a[Mcedia] §f副声源已启用。");
            return true;
        }
    }

    public boolean isSecondaryAudioActive() {
        return this.isSecondarySourceActive;
    }

    public Component getStatusComponent() {
        Media media = player.getMedia();
        MutableComponent status = Component.literal("§6--- Mcedia Player Status ---\n");

        if (media == null) {
            status.append("§e状态: §f空闲 (无播放)\n");
            return status;
        }
        IMediaInfo info = media.getMediaInfo();
        // 播放信息
        if (playingUrl != null) {
            MutableComponent hoverText = Component.literal("点击可在浏览器中打开");
            if (info != null) {
                List<QualityInfo> qualities = info.getAvailableQualities();
                String currentQuality = info.getCurrentQuality();
                if (qualities != null && !qualities.isEmpty()) {
                    hoverText.append(Component.literal("\n\n§f可用清晰度:").withStyle(ChatFormatting.GRAY));
                    for (QualityInfo quality : qualities) {
                        boolean isCurrent = Objects.equals(quality.description, currentQuality);
                        ChatFormatting color = isCurrent ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA;
                        String prefix = isCurrent ? "\n> " : "\n- ";
                        hoverText.append(Component.literal(prefix + quality.description).withStyle(color));
                    }
                }
            }
            HoverEvent hoverEvent = new HoverEvent.ShowText(hoverText);
            ClickEvent clickEvent = new ClickEvent.OpenUrl(URI.create(playingUrl));
            Style interactiveStyle = Style.EMPTY.withHoverEvent(hoverEvent).withClickEvent(clickEvent);
            status.append(Component.literal("§eURL: ").append(Component.literal(playingUrl).withStyle(interactiveStyle.withColor(ChatFormatting.AQUA)))).append("\n");
        }
        if (info != null) {
            if (info.getTitle() != null && !info.getTitle().isBlank()) {
                status.append("§e标题: §f" + info.getTitle() + "\n");
            }
            if (info.getAuthor() != null && !info.getAuthor().isBlank()) {
                status.append("§e作者: §f" + info.getAuthor() + "\n");
            }
        }
        // 播放进度和状态
        if (media.isLiveStream()) {
            status.append("§e类型: §b直播流\n");
            long duration = media.getDurationUs() / 1_000_000;
            status.append(String.format("§e进度: §a%02d:%02d:%02d\n", duration / 3600, (duration % 3600) / 60, duration % 60));
        } else {
            status.append("§e类型: §b点播视频\n");
            long current = media.getDurationUs() / 1_000_000;
            long total = media.getLengthUs() / 1_000_000;
            status.append(String.format("§e进度: §a%02d:%02d:%02d §f/ §7%02d:%02d:%02d\n",
                    current / 3600, (current % 3600) / 60, current % 60,
                    total / 3600, (total % 3600) / 60, total % 60));
        }
        if (info != null && info.getCurrentQuality() != null) {
            status.append("§e清晰度: §d" + info.getCurrentQuality() + "\n");
        }
        status.append("§e状态: " + (player.isBuffering() ? "§6缓冲中" : (media.isPaused() ? "§e已暂停" : "§a播放中")) + "\n");
        // 实体与组件状态
        status.append("§6--- 实体与组件状态 ---\n");
        float yRotRadians = (float) -Math.toRadians(this.entity.getYRot());
        var screenOffsetRotated = new Vector3f(offsetX, offsetY, offsetZ).rotateY(yRotRadians);
        double screenX = this.entity.getX() + screenOffsetRotated.x();
        double screenY = this.entity.getY() + screenOffsetRotated.y() + 1.02 * this.entity.getScale();
        double screenZ = this.entity.getZ() + screenOffsetRotated.z();
        status.append(String.format("§e屏幕坐标: §f(%.2f, %.2f, %.2f)\n", screenX, screenY, screenZ));

        var primaryAudioOffsetRotated = new Vector3f(audioOffsetX, audioOffsetY, audioOffsetZ).rotateY(yRotRadians);
        double primaryAudioX = this.entity.getX() + primaryAudioOffsetRotated.x();
        double primaryAudioY = this.entity.getY() + primaryAudioOffsetRotated.y();
        double primaryAudioZ = this.entity.getZ() + primaryAudioOffsetRotated.z();
        status.append(String.format("§e主声源: §f(%.2f, %.2f, %.2f)\n", primaryAudioX, primaryAudioY, primaryAudioZ));

        if (isSecondarySourceActive) {
            var secondaryAudioOffsetRotated = new Vector3f(audioOffsetX2, audioOffsetY2, audioOffsetZ2).rotateY(yRotRadians);
            double secondaryAudioX = this.entity.getX() + secondaryAudioOffsetRotated.x();
            double secondaryAudioY = this.entity.getY() + secondaryAudioOffsetRotated.y();
            double secondaryAudioZ = this.entity.getZ() + secondaryAudioOffsetRotated.z();
            status.append(String.format("§e副声源: §a已启用 §f(%.2f, %.2f, %.2f)\n", secondaryAudioX, secondaryAudioY, secondaryAudioZ));
        } else {
            status.append("§e副声源: §7未启用\n");
        }
        if (danmakuEnable) {
            StringBuilder danmakuTypes = new StringBuilder();
            if (showScrollingDanmaku) danmakuTypes.append("滚 ");
            if (showTopDanmaku) danmakuTypes.append("顶 ");
            if (showBottomDanmaku) danmakuTypes.append("底");
            status.append(String.format("§e弹幕: §a开启 §7(类型: %s)\n", danmakuTypes.toString().trim()));
        } else {
            status.append("§e弹幕: §7关闭\n");
        }
        status.append(String.format("§e自动连播: %s\n", (videoAutoplay ? "§a开启" : "§7关闭")));
        status.append("§6-------------------------\n");
        return status;
    }

    public PresetData getPresetData() {
        PresetData data = new PresetData();
        data.screenX = this.offsetX;
        data.screenY = this.offsetY;
        data.screenZ = this.offsetZ;
        data.screenScale = this.scale;

        data.primaryAudio.x = this.audioOffsetX;
        data.primaryAudio.y = this.audioOffsetY;
        data.primaryAudio.z = this.audioOffsetZ;
        data.primaryAudio.maxVol = this.audioMaxVolume;
        data.primaryAudio.minRange = this.audioRangeMin;
        data.primaryAudio.maxRange = this.audioRangeMax;

        data.secondaryAudio.x = this.audioOffsetX2;
        data.secondaryAudio.y = this.audioOffsetY2;
        data.secondaryAudio.z = this.audioOffsetZ2;
        data.secondaryAudio.maxVol = this.audioMaxVolume2;
        data.secondaryAudio.minRange = this.audioRangeMin2;
        data.secondaryAudio.maxRange = this.audioRangeMax2;
        data.secondaryAudioEnabled = this.isSecondarySourceActive;

        return data;
    }

    public void applyPreset(PresetData data) {
        commandSetOffset(data.screenX, data.screenY, data.screenZ, data.screenScale);
        commandSetAudioPrimary(data.primaryAudio.x, data.primaryAudio.y, data.primaryAudio.z, data.primaryAudio.maxVol, data.primaryAudio.minRange, data.primaryAudio.maxRange);
        commandSetAudioSecondary(data.secondaryAudio.x, data.secondaryAudio.y, data.secondaryAudio.z, data.secondaryAudio.maxVol, data.secondaryAudio.minRange, data.secondaryAudio.maxRange);

        if (data.secondaryAudioEnabled && !this.isSecondarySourceActive) {
            commandToggleAudioSecondary();
        } else if (!data.secondaryAudioEnabled && this.isSecondarySourceActive) {
            commandToggleAudioSecondary();
        }
        Mcedia.msgToPlayer("§a[Mcedia] §f预设已应用。");
    }

    private void switchToCommandSource() {
        if (this.currentSource != PlaybackSource.COMMAND) {
            this.currentSource = PlaybackSource.COMMAND;
            Mcedia.msgToPlayer("§e[Mcedia] §f播放列表已切换为指令模式。书本内容将被忽略，直到放入新书。");
        }
    }

    public void commandPlaylistAdd(String url) {
        switchToCommandSource();
        PlaybackItem item = new PlaybackItem(url);
        playlist.offer(item);
        playlistOriginalSize++;
        Mcedia.msgToPlayer("§a[Mcedia] §f已将URL添加到播放列表末尾 (当前共 " + playlistOriginalSize + " 项)。");
    }

    public void commandPlaylistInsert(int index, String url) {
        switchToCommandSource();
        int realIndex = index - 1;
        if (realIndex < 0 || realIndex > playlist.size()) {
            Mcedia.msgToPlayer("§c[Mcedia] §f无效的插入位置。当前列表大小为 " + playlist.size() + "。");
            return;
        }

        List<PlaybackItem> tempList = new ArrayList<>(playlist);
        tempList.add(realIndex, new PlaybackItem(url));

        playlist.clear();
        playlist.addAll(tempList);
        playlistOriginalSize++;
        Mcedia.msgToPlayer("§a[Mcedia] §f已将URL插入到播放列表位置 §e" + index + "§f。");
    }

    public void commandPlaylistRemove(int index) {
        switchToCommandSource();
        int realIndex = index - 1;
        if (realIndex < 0 || realIndex >= playlist.size()) {
            Mcedia.msgToPlayer("§c[Mcedia] §f无效的移除位置。");
            return;
        }

        List<PlaybackItem> tempList = new ArrayList<>(playlist);
        PlaybackItem removed = tempList.remove(realIndex);

        playlist.clear();
        playlist.addAll(tempList);
        playlistOriginalSize--;
        Mcedia.msgToPlayer("§a[Mcedia] §f已从播放列表移除: §7" + removed.originalUrl);
    }

    public void commandPlaylistClear() {
        switchToCommandSource();
        playlist.clear();
        playlistOriginalSize = 0;
        commandStop();
        Mcedia.msgToPlayer("§e[Mcedia] §f播放列表已清空。");
    }

    public Component commandPlaylistList(int page) {
        List<PlaybackItem> fullList = new ArrayList<>();
        if (currentPlayingItem != null) {
            fullList.add(currentPlayingItem);
        }
        fullList.addAll(playlist);
        if (fullList.isEmpty()) {
            return Component.literal("§e[Mcedia] §f播放列表为空。");
        }
        int totalPages = (int) Math.ceil((double) fullList.size() / ITEMS_PER_PAGE_PLAYLIST);
        if (page > totalPages) page = totalPages;
        MutableComponent component = Component.literal("§6--- Mcedia 播放列表 (第 " + page + " / " + totalPages + " 页) ---\n");
        int startIndex = (page - 1) * ITEMS_PER_PAGE_PLAYLIST;
        for (int i = 0; i < ITEMS_PER_PAGE_PLAYLIST && (startIndex + i) < fullList.size(); i++) {
            int trueIndex = startIndex + i;
            PlaybackItem item = fullList.get(trueIndex);
            MutableComponent hoverText = Component.literal("§bURL: §f" + item.originalUrl + "\n");
            hoverText.append("§eTimestamp: §f" + (item.timestampUs / 1_000_000) + "s\n");
            if (item.mode != BiliPlaybackMode.NONE) {
                hoverText.append("§eP Number: §f" + item.pNumber + "\n");
            }
            if (item.desiredQuality != null) {
                hoverText.append("§dQuality (指定): §f" + item.desiredQuality);
            } else {
                hoverText.append("§dQuality (全局): §7" + this.desiredQuality);
            }
            ClickEvent clickEvent = new ClickEvent.SuggestCommand("/mcedia playlist remove " + (trueIndex + 1));
            String prefix;
            ChatFormatting color;
            if (currentPlayingItem != null && trueIndex == 0) {
                prefix = "§b> [播放中] §f";
                color = ChatFormatting.AQUA;
            } else {
                int displayIndex = (currentPlayingItem != null) ? trueIndex : trueIndex + 1;
                prefix = "§7" + displayIndex + ". §f";
                color = ChatFormatting.WHITE;
            }
            HoverEvent hoverEvent = new HoverEvent.ShowText(hoverText);
            MutableComponent line = Component.literal(prefix + item.originalUrl.substring(0, Math.min(item.originalUrl.length(), 35)) + "...")
                    .withStyle(color)
                    .setStyle(Style.EMPTY
                            .withHoverEvent(hoverEvent)
                            .withClickEvent(clickEvent));
            component.append(line).append("\n");
        }
        return component;
    }

    public void commandSetAutoplay(boolean enabled) {
        this.videoAutoplay = enabled;
        if (enabled) {
            Mcedia.msgToPlayer("§a[Mcedia] §f自动连播已开启。");
        } else {
            Mcedia.msgToPlayer("§e[Mcedia] §f自动连播已关闭。");
        }
    }

    public void commandSetDanmakuEnabled(boolean enabled) {
        this.danmakuEnable = enabled;
        if (enabled) {
            Mcedia.msgToPlayer("§a[Mcedia] §f弹幕已开启。");
        } else {
            Mcedia.msgToPlayer("§e[Mcedia] §f弹幕已关闭。");
        }
    }

    public void commandSetDanmakuArea(float percent) {
        this.danmakuDisplayArea = Mth.clamp(percent / 100.0f, 0.0f, 1.0f);
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7弹幕显示区域已设置为 %.0f%%。", percent));
    }

    public void commandSetDanmakuOpacity(float percent) {
        this.danmakuOpacity = Mth.clamp(percent / 100.0f, 0.0f, 1.0f);
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7弹幕不透明度已设置为 %.0f%%。", percent));
    }

    public void commandSetDanmakuFontScale(float scale) {
        this.danmakuFontScale = Math.max(0.1f, scale);
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7弹幕字体缩放已设置为 %.2f。", scale));
    }

    public void commandSetDanmakuSpeedScale(float scale) {
        this.danmakuSpeedScale = Math.max(0.1f, scale);
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7弹幕速度缩放已设置为 %.2f。", scale));
    }

    public void commandSetDanmakuTypeVisible(String type, boolean visible) {
        String typeName = "未知";
        switch (type.toLowerCase()) {
            case "scrolling":
                this.showScrollingDanmaku = visible;
                typeName = "滚动弹幕";
                break;
            case "top":
                this.showTopDanmaku = visible;
                typeName = "顶部弹幕";
                break;
            case "bottom":
                this.showBottomDanmaku = visible;
                typeName = "底部弹幕";
                break;
        }
        Mcedia.msgToPlayer(String.format("§f[Mcedia] §7%s已§%s。", typeName, visible ? "a开启" : "e关闭"));
    }

    public void commandSetScreenLightLevel(int level) {
        if (level >= 0 && level <= 15) {
            this.customLightLevel = level;
            Mcedia.msgToPlayer(String.format("§a[Mcedia] §f屏幕光照等级已设置为 §e%d§f。", level));
        } else {
            this.customLightLevel = -1;
            Mcedia.msgToPlayer("§e[Mcedia] §f屏幕光照已重置为§7跟随世界光照§f。");
        }
    }
}
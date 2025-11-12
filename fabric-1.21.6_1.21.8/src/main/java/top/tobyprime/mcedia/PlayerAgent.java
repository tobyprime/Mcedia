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
import net.minecraft.network.chat.*;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.*;
import top.tobyprime.mcedia.BilibiliAuthRequiredException;
import top.tobyprime.mcedia.interfaces.IMediaInfo;
import top.tobyprime.mcedia.provider.IMediaProvider;
import top.tobyprime.mcedia.provider.MediaProviderRegistry;
import top.tobyprime.mcedia.provider.VideoInfo;
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

import static com.ibm.icu.text.PluralRules.Operand.e;

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

    private long timestampFromUrlUs = 0;
    private boolean isPausedByBasePlate = false;
    private final Queue<PlaybackItem> playlist = new LinkedList<>();
    private PlaybackItem currentPlayingItem = null;
    private String currentPlaylistContent = "";
    private int playlistOriginalSize = 0;
    private final AtomicLong playbackToken = new AtomicLong(0);
    private volatile boolean isReconnecting = false;
    @Nullable
    private volatile String commandedUrl = null;

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
    private int saveProgressTicker = 0;
    private String desiredQuality = "自动";
    private String previousQuality = "自动";
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
        PLAYLIST_PART
    }

    private static class PlaybackItem {
        final String originalUrl;
        int pNumber = 1;
        long timestampUs = 0;
        BiliPlaybackMode mode = BiliPlaybackMode.NONE;
        PlaybackItem(String url) { this.originalUrl = url; }
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

            String newQuality = (offHandPages.size() > 3) ? offHandPages.get(3) : null;
            this.desiredQuality = (newQuality == null || newQuality.isBlank()) ? "自动" : newQuality.trim();
            this.previousQuality = this.desiredQuality;
        } else {
            resetOffset();
            resetAudioOffset();
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
            if (currentMedia.isEnded() && !this.isLoopingInProgress) {
                if (currentPlayingItem == null) {
                    playNextInQueue();
                    return;
                }
                if (currentPlayingItem.mode == BiliPlaybackMode.PLAYLIST_PART) {
                    playNextBilibiliPartOrLoop(playingUrl, player.looping);
                    this.isLoopingInProgress = true;
                    return;
                }
                if (player.looping && playingUrl != null) {
                    this.isLoopingInProgress = true;
                    if (playlistOriginalSize > 1 && currentPlayingItem.mode != BiliPlaybackMode.SINGLE_PART) {
                        LOGGER.info("列表循环: 重新将 '{}' 添加到队尾并播放下一个。", currentPlayingItem.originalUrl);
                        playlist.offer(currentPlayingItem);
                        playNextInQueue();
                    } else {
                        LOGGER.info("单曲/单P循环: 重新播放 '{}'。", currentPlayingItem.originalUrl);
                        this.open(playingUrl);
                        this.startPlayback(true);
                    }
                } else if (!player.looping && !playlist.isEmpty()) {
                    playNextInQueue();
                } else {
                    currentPlayingItem = null;
                    open(null);
                    player.closeAsync();
                }
            }
        }
    }

    public static long parseToMicros(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return 0;
        }
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
                return 0;
            }
        } catch (NumberFormatException e) {
            return 0;
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
                if (lines[i].contains(urlToCheck)) {
                    if (i + 1 < lines.length) {
                        String nextLine = lines[i + 1].trim();

                        if (nextLine.isEmpty() || nextLine.startsWith("http")) {
                            return 0;
                        }

                        try {
                            long duration = parseToMicros(nextLine);
                            LOGGER.info("为 '{}' 成功解析到下一行的时间戳: {} us", urlToCheck, duration);
                            return duration;
                        } catch (IllegalArgumentException e) {
                            return 0;
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("在解析基础时长时发生未知错误", e);
        }

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

//    public long getDuration(String forUrl) {
//        long baseDuration = getBaseDurationForUrl(forUrl);
//        return baseDuration + getServerDuration() + this.timestampFromUrlUs;
//    }

    public void update() {
        if (this.commandedUrl != null) {
            String url = this.commandedUrl;
            this.commandedUrl = null;
            LOGGER.info("接收到指令播放任务，强制更新URL: {}", url);
            player.closeAsync();
            playlist.clear();
            playlistOriginalSize = 0;
            currentPlayingItem = null;
            List<String> commandPage = Collections.singletonList(url);
            updatePlaylist(commandPage);
            currentPlaylistContent = url;
            playNextInQueue();
            return;
        }
        try {
            ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
            List<String> bookPages = getBookPages(mainHandItem);
            String newPlaylistContent = bookPages != null ? String.join("\n", bookPages) : "";
            if (!newPlaylistContent.equals(currentPlaylistContent)) {
                LOGGER.info("检测到播放列表变更，强制中断并更新...");
                currentPlaylistContent = newPlaylistContent;
                player.closeAsync();
                updatePlaylist(bookPages);
                playNextInQueue();
            }

            ItemStack offHandItem = entity.getItemInHand(InteractionHand.OFF_HAND);
            if (!ItemStack.matches(offHandItem, preOffHandItemStack)) {
                preOffHandItemStack = offHandItem.copy();
                List<String> offHandPages = getBookPages(offHandItem);
                boolean qualityChanged = false;
                if (offHandPages != null) {
                    if (!offHandPages.isEmpty()) updateOffset(offHandPages.get(0));
                    if (offHandPages.size() > 1) updateAudioOffset(offHandPages.get(1));
                    if (offHandPages.size() > 2) updateOther(offHandPages.get(2));
                    qualityChanged = updateQuality(offHandPages.size() > 3 ? offHandPages.get(3) : null);
                } else {
                    resetOffset();
                    resetAudioOffset();
                    qualityChanged = updateQuality(null);
                }

                if (qualityChanged && player.getMedia() != null && playingUrl != null) {
                    LOGGER.info("检测到清晰度变更: '{}' -> '{}'，正在软重载...", this.previousQuality, this.desiredQuality);

                    Media currentMedia = player.getMedia();
                    long seekTo = 0;
                    if (currentMedia != null && !currentMedia.isLiveStream()) {
                        seekTo = currentMedia.getDurationUs();
                    }

                    this.open(playingUrl);
                    this.startPlayback(false);
                    this.finalSeekTimestampUs = seekTo;
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

        List<String> lines = new ArrayList<>();
        for (String page : pages) {
            if (page != null && !page.isBlank()) {
                lines.addAll(Arrays.asList(page.split("\n")));
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            PlaybackItem item = null;
            if (line.equalsIgnoreCase("rickroll")) {
                item = new PlaybackItem(RICKROLL_URL);
            } else if (line.equalsIgnoreCase("badapple")) {
                item = new PlaybackItem(BAD_APPLE_URL);
            } else {
                Matcher urlMatcher = URL_PATTERN.matcher(line);
                if (urlMatcher.find()) {
                    item = new PlaybackItem(urlMatcher.group(0).trim());
                }
            }

            if (item == null) continue;

            boolean isBiliVideo = item.originalUrl.contains("bilibili.com/video/");
            if (isBiliVideo) {
                boolean hasPInUrl = item.originalUrl.contains("?p=") || item.originalUrl.contains("&p=");
                if (hasPInUrl) {
                    item.mode = BiliPlaybackMode.SINGLE_PART;
                } else {
                    item.mode = BiliPlaybackMode.PLAYLIST_PART;
                }
            }

            if (i + 1 < lines.size()) {
                String nextLine = lines.get(i + 1).trim();
                Matcher timeMatcher = TIMESTAMP_PATTERN.matcher(nextLine);
                Matcher pNumMatcher = P_NUMBER_PATTERN.matcher(nextLine);

                if (timeMatcher.matches()) {
                    try {
                        item.timestampUs = parseToMicros(nextLine);
                        i++;

                        if (item.mode == BiliPlaybackMode.PLAYLIST_PART && i + 1 < lines.size()) {
                            String nextNextLine = lines.get(i + 1).trim();
                            Matcher pNumMatcherAfterTime = P_NUMBER_PATTERN.matcher(nextNextLine);
                            if (pNumMatcherAfterTime.matches()) {
                                item.pNumber = Integer.parseInt(pNumMatcherAfterTime.group(1));
                                i++;
                            }
                        }
                    } catch (Exception ignored) { item.timestampUs = 0; }
                } else if (pNumMatcher.matches() && item.mode == BiliPlaybackMode.PLAYLIST_PART) {
                    item.pNumber = Integer.parseInt(pNumMatcher.group(1));
                    i++;
                }
            }

            playlist.offer(item);
            playlistOriginalSize++;
            LOGGER.info("添加项目到播放列表: URL='{}', Mode={}, P={}, Timestamp={}us", item.originalUrl, item.mode, item.pNumber, item.timestampUs);
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

            if (nextItem.mode == BiliPlaybackMode.PLAYLIST_PART) {
                if (!urlToPlay.contains("?p=") && !urlToPlay.contains("&p=")) {
                    urlToPlay += (urlToPlay.contains("?") ? "&" : "?") + "p=" + nextItem.pNumber;
                }
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

    private void playNextBilibiliPartOrLoop(String finishedUrl, boolean isLoopingEnabled) {
        String bvid = parseBvidFromUrl(finishedUrl);
        int currentP = parsePNumberFromUrl(finishedUrl);

        if (bvid == null) {
            Minecraft.getInstance().execute(this::playNextInQueue);
            return;
        }

        final int nextP = currentP + 1;

        CompletableFuture.runAsync(() -> {
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
                    } else {
                        if (isLoopingEnabled) {
                            LOGGER.info("B站合集循环: 已播完最后一P，回到P1。");
                            this.currentPlayingItem.pNumber = 1;
                            String firstUrl = "https://www.bilibili.com/video/" + bvid + "?p=1";

                            this.finalSeekTimestampUs = 0;

                            Minecraft.getInstance().execute(() -> {
                                this.open(firstUrl);
                                this.startPlayback(true);
                            });
                        } else {
                            LOGGER.info("B站合集已播完 (共 {} P)，尝试播放列表中的下一个。", pages.length());
                            Minecraft.getInstance().execute(this::playNextInQueue);
                        }
                    }
                } else {
                    Minecraft.getInstance().execute(this::playNextInQueue);
                }
            } catch (Exception e) {
                LOGGER.error("检查B站下一P时出错", e);
                Minecraft.getInstance().execute(this::playNextInQueue);
            }
        });
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
        isLoopingInProgress = false;
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
                return;
            }

            if (playingUrl == null) {
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
                        this.isLoopingInProgress = isLooping;
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
        UrlExpander.expand(initialUrl)
                .thenAccept(expandedUrl -> {
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

                    final String cookie = (provider != null && provider.getClass().getSimpleName().toLowerCase().contains("bilibili"))
                            ? McediaConfig.BILIBILI_COOKIE
                            : null;

                    CompletableFuture<VideoInfo> videoInfoFuture = player.openAsyncWithVideoInfo(
                            () -> {
                                if (playbackToken.get() != currentToken) {
                                    throw new IllegalStateException("Playback aborted by new request before resolving URL.");
                                }
                                try {
                                    if (provider == null) {
                                        throw new UnsupportedOperationException("No provider found for URL: " + expandedUrl);
                                    }
                                    return provider.resolve(expandedUrl, cookie, this.desiredQuality);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            () -> cookie, 0
                    );

                    videoInfoFuture.handle((videoInfo, throwable) -> {
                        if (playbackToken.get() != currentToken) {
                            LOGGER.debug("Playback token {} is outdated, aborting final handler.", currentToken);
                            return null;
                        }

                        if (throwable != null) {
                            if (!(throwable.getCause() instanceof IllegalStateException)) {
                                handlePlaybackFailure(throwable, initialUrl, expandedUrl, isLooping);
                            }
                        } else {
                            handlePlaybackSuccess(videoInfo, expandedUrl, isLooping, provider);
                        }
                        return null;
                    });
                })
                .exceptionally(ex -> {
                    if (playbackToken.get() == currentToken) {
                        LOGGER.error("处理URL时发生严重错误: {}", initialUrl, ex);
                        handlePlaybackFailure(ex, initialUrl, initialUrl, isLooping);
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
        this.isTextureInitialized = false;
        Media media = player.getMedia();
        if (media == null) {
            LOGGER.error("视频加载成功但Media对象为空，这是一个严重错误。");
            return;
        }

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
            List<String> qualities = videoInfo.getAvailableQualities();
            String currentQuality = videoInfo.getCurrentQuality();
            if (qualities != null && !qualities.isEmpty()) {
                hoverText.append(Component.literal("\n\n§f可用清晰度:").withStyle(ChatFormatting.GRAY));
                for (String quality : qualities) {
                    boolean isCurrent = Objects.equals(quality, currentQuality);
                    ChatFormatting color = isCurrent ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA;
                    String prefix = isCurrent ? "\n> " : "\n- ";

                    hoverText.append(Component.literal(prefix + quality).withStyle(color));
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

        this.isLoopingInProgress = false;
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

    public void close() {
        playingUrl = null;
        isLoopingInProgress = false;
        cacheManager.cleanup();
        player.closeAsync();
    }

    public void closeSync() {
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

    public void updateOther(String flags) {
        boolean looping = flags.contains("looping");
        this.player.setLooping(looping);
        this.shouldCacheForLoop = looping && McediaConfig.CACHING_ENABLED;
        if (!looping && cacheManager.isCached(playingUrl)) cacheManager.cleanup();
    }

    public boolean updateQuality(String quality) {
        this.previousQuality = this.desiredQuality;
        String newQuality = (quality == null || quality.isBlank()) ? "自动" : quality.trim();
        if (!this.desiredQuality.equals(newQuality)) {
            this.desiredQuality = newQuality;
            return true; // 清晰度发生了变化
        }
        return false; // 清晰度未变
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
            String time = String.format("%02d:%02d",
                    (seekUs / 1_000_000) / 60,
                    (seekUs / 1_000_000) % 60);
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
        List<String> pages = Collections.singletonList(url);
        this.currentPlaylistContent = String.join("\n", pages);
        updatePlaylist(pages);
        playNextInQueue();
        Mcedia.msgToPlayer("§f[Mcedia] §7正在尝试播放新链接...");
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

    public void commandEnableAudioSecondary() {
        if (!isSecondarySourceActive) {
            player.bindAudioSource(secondaryAudioSource);
            isSecondarySourceActive = true;
            Mcedia.msgToPlayer("§a[Mcedia] §f副声源已启用。");
        } else {
            Mcedia.msgToPlayer("§e[Mcedia] §f副声源已经是启用状态。");
        }
    }

    public void commandDisableAudioSecondary() {
        if (isSecondarySourceActive) {
            player.unbindAudioSource(secondaryAudioSource);
            isSecondarySourceActive = false;
            Mcedia.msgToPlayer("§e[Mcedia] §f副声源已禁用。");
        } else {
            Mcedia.msgToPlayer("§7[Mcedia] §8副声源当前未启用。");
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
        if (media == null) {
            return Component.literal("§e[Mcedia Status] §f当前无播放。");
        }
        IMediaInfo info = media.getMediaInfo();
        MutableComponent status = Component.literal("§6--- Mcedia Player Status ---\n");
        status.append("§eURL: §f" + playingUrl + "\n");
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
        status.append("§e状态: §f" + (player.isBuffering() ? "§6缓冲中" : (media.isPaused() ? "§e已暂停" : "§a播放中")) + "\n");
        status.append("§e解码: §f" + (player.getDecoderConfiguration().useHardwareDecoding ? "§b硬件" : "§7软件"));

        return status;
    }
}
package top.tobyprime.mcedia.agent;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.PlayerAgent;
import top.tobyprime.mcedia.provider.BilibiliBangumiInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlaylistManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaylistManager.class);

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");
    private static final String RICKROLL_URL = "https://www.bilibili.com/video/BV1GJ411x7h7";
    private static final String BAD_APPLE_URL = "https://www.bilibili.com/video/BV1xx411c79H";
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^(\\d{1,2}:)?(\\d{1,2}):(\\d{1,2})$");
    private static final Pattern P_NUMBER_PATTERN = Pattern.compile("^[pP]?(\\d+)$");
    private static final int ITEMS_PER_PAGE_PLAYLIST = 7;

    // --- 核心字段 ---
    private final PlayerAgent agent;
    private final Queue<PlayerAgent.PlaybackItem> playlist = new LinkedList<>();
    private PlayerAgent.PlaybackItem currentPlayingItem = null;
    private String currentPlaylistContent = "";
    private int playlistOriginalSize = 0;

    public PlaylistManager(PlayerAgent agent) {
        this.agent = agent;
    }

    // --- 核心流程控制方法 ---

    public boolean updatePlaylistFromBook(ItemStack mainHandItem, PlayerAgent.PlaybackSource currentSource) {
        if (currentSource == PlayerAgent.PlaybackSource.COMMAND) {
            return false;
        }

        List<String> bookPages = getBookPages(mainHandItem);
        String newPlaylistContent = bookPages != null ? String.join("\n", bookPages) : "";

        if (!newPlaylistContent.equals(currentPlaylistContent)) {
            currentPlaylistContent = newPlaylistContent;
            updatePlaylist(bookPages);
            return true;
        }
        return false;
    }

    public void startPlaylist() {
        agent.getPlayer().closeAsync().thenRun(this::playNextInQueue);
    }

    public void handleMediaEnd() {
        if (currentPlayingItem == null) {
            return;
        }
        if (agent.getConfigManager().videoAutoplay) {
            if (currentPlayingItem.mode == PlayerAgent.BiliPlaybackMode.BANGUMI_SERIES) {
                if (playNextBangumiEpisode()) {
                    return;
                }
            }
            if (currentPlayingItem.mode == PlayerAgent.BiliPlaybackMode.SINGLE_PART && agent.getPlayingUrl().contains("bilibili.com/video/")) {
                if (playNextBilibiliVideoPart(agent.getPlayingUrl(), false)) {
                    return;
                }
            }
        }
        if (agent.getPlayer().looping) {
            if (!playlist.isEmpty()) {
                LOGGER.info("列表循环: 将 '{}' 添加到队尾并播放下一个。", currentPlayingItem.originalUrl);
                playlist.offer(currentPlayingItem);
                playNextInQueue();
            } else {
                LOGGER.info("单项循环: 重新播放 '{}'。", currentPlayingItem.originalUrl);
                agent.startPlayback(currentPlayingItem.originalUrl, true, 0, agent.getConfigManager().desiredQuality);
            }
        } else if (!playlist.isEmpty()) {
            LOGGER.info("顺序播放: 播放列表下一项。");
            playNextInQueue();
        } else {
            LOGGER.info("播放列表已结束。");
            currentPlayingItem = null;
            agent.stopPlayback();
        }
    }

    // --- 内部实现方法 ---

    private void updatePlaylist(List<String> pages) {
        playlist.clear();
        playlistOriginalSize = 0;
        if (pages == null || pages.isEmpty()) {
            agent.stopPlayback();
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
            PlayerAgent.PlaybackItem item = null;
            if (currentLine.equalsIgnoreCase("rickroll")) {
                item = new PlayerAgent.PlaybackItem(RICKROLL_URL);
            } else if (currentLine.equalsIgnoreCase("badapple")) {
                item = new PlayerAgent.PlaybackItem(BAD_APPLE_URL);
            } else if (urlMatcher.find()) {
                item = new PlayerAgent.PlaybackItem(urlMatcher.group(0).trim());
            }

            if (item == null) continue;

            boolean isBiliVideo = item.originalUrl.contains("bilibili.com/video/");
            boolean isBiliBangumi = item.originalUrl.contains("bilibili.com/bangumi/play/");

            if (isBiliVideo) {
                item.mode = PlayerAgent.BiliPlaybackMode.SINGLE_PART;
            } else if (isBiliBangumi) {
                item.mode = PlayerAgent.BiliPlaybackMode.BANGUMI_SERIES;
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
                        item.timestampUs = agent.parseTimestampToMicros(part);
                        iterator.remove();
                    } else if (pNumMatcher.matches()) {
                        if (isBiliVideo && item.mode == PlayerAgent.BiliPlaybackMode.VIDEO_PLAYLIST || isBiliBangumi) {
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
            Mcedia.getInstance().savePlayerProgress(agent.getEntity().getUUID(), 0);
        }
        PlayerAgent.PlaybackItem nextItem = playlist.poll();
        if (nextItem != null) {
            this.currentPlayingItem = nextItem;
            String urlToPlay = nextItem.originalUrl;
            if (nextItem.mode == PlayerAgent.BiliPlaybackMode.VIDEO_PLAYLIST) {
                if (!urlToPlay.contains("?p=") && !urlToPlay.contains("&p=")) {
                    urlToPlay += (urlToPlay.contains("?") ? "&" : "?") + "p=" + nextItem.pNumber;
                }
            } else if (nextItem.mode == PlayerAgent.BiliPlaybackMode.BANGUMI_SERIES && nextItem.seasonId != null && nextItem.pNumber > 1) {
                urlToPlay += (urlToPlay.contains("?") ? "&" : "?") + "p=" + nextItem.pNumber;
            }

            long finalSeekTimestampUs = 0;
            long serverSyncTime = agent.getServerDuration();
            if (serverSyncTime > 0) {
                finalSeekTimestampUs = serverSyncTime;
                LOGGER.info("应用服务器实时同步时间: {}us", serverSyncTime);
                if (McediaConfig.RESUME_ON_RELOAD_ENABLED) {
                    Mcedia.getInstance().savePlayerProgress(agent.getEntity().getUUID(), 0);
                }
            } else {
                if (McediaConfig.RESUME_ON_RELOAD_ENABLED) {
                    long resumeTime = Mcedia.getInstance().loadPlayerProgress(agent.getEntity().getUUID());
                    if (resumeTime > 0) {
                        finalSeekTimestampUs += resumeTime;
                        LOGGER.info("读取到断点续播时间: {}us", resumeTime);
                    }
                }
                finalSeekTimestampUs += nextItem.timestampUs;
                finalSeekTimestampUs += agent.parseBiliTimestampToUs(nextItem.originalUrl);
            }

            String qualityForNextPlayback = (nextItem.desiredQuality != null && !nextItem.desiredQuality.isBlank())
                    ? nextItem.desiredQuality
                    : agent.getConfigManager().desiredQuality;

            LOGGER.info("准备播放: URL='{}', Mode={}, P={}, 最终跳转时间={}us", urlToPlay, nextItem.mode, nextItem.pNumber, finalSeekTimestampUs);

            agent.startPlayback(urlToPlay, false, finalSeekTimestampUs, qualityForNextPlayback);
        } else {
            this.currentPlayingItem = null;
            LOGGER.info("播放列表已为空，播放结束。");
            agent.stopPlayback();
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

                        if (currentPlayingItem != null) {
                            currentPlayingItem.pNumber = nextP;
                        }

                        Minecraft.getInstance().execute(() -> {
                            agent.startPlayback(nextUrl, false, 0, agent.getConfigManager().desiredQuality);
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
        if (agent.getCurrentBangumiInfo() == null) return false;

        BilibiliBangumiInfo.Episode nextEpisode = agent.getCurrentBangumiInfo().getNextEpisode();

        if (nextEpisode != null) {
            LOGGER.info("番剧连播: 找到下一集 '{}', 正在加载...", nextEpisode.title);
            String nextUrl = "https://www.bilibili.com/bangumi/play/ep" + nextEpisode.epId;
            Minecraft.getInstance().execute(() -> {
                agent.startPlayback(nextUrl, false, 0, agent.getConfigManager().desiredQuality);
            });
            return true;
        } else {
            LOGGER.info("番剧 '{}' 已播放完毕。", agent.getCurrentBangumiInfo().title);
            return false;
        }
    }

    // --- 指令处理方法 ---

    public void commandSetUrl(String url) {
        LOGGER.info("通过指令设置播放URL: {}", url);
        agent.switchToCommandSource();
        List<String> pages = Collections.singletonList(url);
        this.currentPlaylistContent = String.join("\n", pages);
        updatePlaylist(pages);
        startPlaylist();
        Mcedia.msgToPlayer("§f[Mcedia] §7正在尝试播放新链接...");
    }

    public void commandSkip() {
        if (!playlist.isEmpty()) {
            Mcedia.msgToPlayer("§f[Mcedia] §7正在跳过当前视频...");
            playNextInQueue();
        } else {
            Mcedia.msgToPlayer("§e[Mcedia] §f播放列表中没有下一个视频。");
        }
    }

    public void commandPlaylistAdd(String url) {
        agent.switchToCommandSource();
        PlayerAgent.PlaybackItem item = new PlayerAgent.PlaybackItem(url);
        playlist.offer(item);
        playlistOriginalSize++;
        Mcedia.msgToPlayer("§a[Mcedia] §f已将URL添加到播放列表末尾 (当前共 " + playlistOriginalSize + " 项)。");
    }

    public void commandPlaylistInsert(int index, String url) {
        agent.switchToCommandSource();
        int realIndex = index - 1;
        if (realIndex < 0 || realIndex > playlist.size()) {
            Mcedia.msgToPlayer("§c[Mcedia] §f无效的插入位置。当前列表大小为 " + playlist.size() + "。");
            return;
        }

        List<PlayerAgent.PlaybackItem> tempList = new ArrayList<>(playlist);
        tempList.add(realIndex, new PlayerAgent.PlaybackItem(url));

        playlist.clear();
        playlist.addAll(tempList);
        playlistOriginalSize++;
        Mcedia.msgToPlayer("§a[Mcedia] §f已将URL插入到播放列表位置 §e" + index + "§f。");
    }

    public void commandPlaylistRemove(int index) {
        agent.switchToCommandSource();
        int realIndex = index - 1;
        if (realIndex < 0 || realIndex >= playlist.size()) {
            Mcedia.msgToPlayer("§c[Mcedia] §f无效的移除位置。");
            return;
        }

        List<PlayerAgent.PlaybackItem> tempList = new ArrayList<>(playlist);
        PlayerAgent.PlaybackItem removed = tempList.remove(realIndex);

        playlist.clear();
        playlist.addAll(tempList);
        playlistOriginalSize--;
        Mcedia.msgToPlayer("§a[Mcedia] §f已从播放列表移除: §7" + removed.originalUrl);
    }

    public void commandPlaylistClear() {
        agent.switchToCommandSource();
        playlist.clear();
        playlistOriginalSize = 0;
        agent.commandStop();
        Mcedia.msgToPlayer("§e[Mcedia] §f播放列表已清空。");
    }

    public Component commandPlaylistList(int page) {
        List<PlayerAgent.PlaybackItem> fullList = new ArrayList<>();
        if (currentPlayingItem != null) {
            fullList.add(currentPlayingItem);
        }
        fullList.addAll(playlist);
        if (fullList.isEmpty()) {
            return Component.literal("§e[Mcedia] §f播放列表为空。");
        }
        int totalPages = (int) Math.ceil((double) fullList.size() / ITEMS_PER_PAGE_PLAYLIST);
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;
        MutableComponent component = Component.literal("§6--- Mcedia 播放列表 (第 " + page + " / " + totalPages + " 页) ---\n");
        int startIndex = (page - 1) * ITEMS_PER_PAGE_PLAYLIST;
        for (int i = 0; i < ITEMS_PER_PAGE_PLAYLIST && (startIndex + i) < fullList.size(); i++) {
            int trueIndex = startIndex + i;
            PlayerAgent.PlaybackItem item = fullList.get(trueIndex);
            MutableComponent hoverText = Component.literal("§bURL: §f" + item.originalUrl + "\n");
            hoverText.append("§eTimestamp: §f" + (item.timestampUs / 1_000_000) + "s\n");
            if (item.mode != PlayerAgent.BiliPlaybackMode.NONE) {
                hoverText.append("§eP Number: §f" + item.pNumber + "\n");
            }
            if (item.desiredQuality != null) {
                hoverText.append("§dQuality (指定): §f" + item.desiredQuality);
            } else {
                hoverText.append("§dQuality (全局): §7" + agent.getConfigManager().desiredQuality);
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

    // --- 辅助方法 ---

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

    private String parseBvidFromUrl(String url) {
        Pattern pattern = Pattern.compile("video/(BV[a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private int parsePNumberFromUrl(String url) {
        try {
            Pattern pattern = Pattern.compile("[?&]p=(\\d+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception ignored) {}
        return 1;
    }
}
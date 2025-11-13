package top.tobyprime.mcedia.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VideoCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoCacheManager.class);
    private static final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    private final Path cacheDir;

    // 使用 ConcurrentHashMap 作为缓存索引，以原始URL为键，存储缓存文件路径数组
    private final Map<String, Path[]> cachedFiles = new ConcurrentHashMap<>();

    public VideoCacheManager(Path cacheDirectory) {
        this.cacheDir = cacheDirectory;
        if (this.cacheDir != null) {
            try {
                Files.createDirectories(this.cacheDir);
            } catch (IOException e) {
                LOGGER.error("无法创建Mcedia缓存目录", e);
            }
        }
    }

    /**
     * 检查指定的URL是否已经被缓存。
     * @param url 视频的原始URL。
     * @return 如果视频文件存在于缓存中，则返回true。
     */
    public boolean isCached(String url) {
        if (url == null) return false;
        Path[] files = cachedFiles.get(url);
        return files != null && files[0] != null && Files.exists(files[0]);
    }

    /**
     * 检查指定的URL当前是否正在被缓存。
     * 在这个实现中，我们不追踪'caching'状态以简化逻辑，
     * 重复调用 cacheVideoAsync 是幂等的。
     */
    public boolean isCaching(String url) {
        return false;
    }

    /**
     * 异步下载并缓存一个视频（包括其可能的独立音轨）。
     * @param originalUrl 视频的原始唯一URL，用作缓存的键。
     * @param videoInfo 包含真实视频流和音频流URL的VideoInfo对象。
     * @param cookie 用于下载的Cookie。
     * @return 一个在缓存完成或失败时结束的CompletableFuture。
     */
    public CompletableFuture<Void> cacheVideoAsync(String originalUrl, VideoInfo videoInfo, String cookie) {
        if (isCached(originalUrl)) {
            return CompletableFuture.completedFuture(null);
        }

        String fileHash = generateFileNameHash(originalUrl);
        Path videoFile = cacheDir.resolve(fileHash + ".video.cache");
        Path audioFile = (videoInfo.getAudioUrl() != null && !videoInfo.getAudioUrl().isEmpty())
                ? cacheDir.resolve(fileHash + ".audio.cache")
                : null;

        CompletableFuture<Path> videoFuture = downloadToFileAsync(videoInfo.getVideoUrl(), videoFile, cookie);
        CompletableFuture<Path> audioFuture = (audioFile != null)
                ? downloadToFileAsync(videoInfo.getAudioUrl(), audioFile, cookie)
                : CompletableFuture.completedFuture(null);

        return CompletableFuture.allOf(videoFuture, audioFuture).thenRun(() -> {
            cachedFiles.put(originalUrl, new Path[]{videoFile, audioFile});
            LOGGER.info("视频缓存完成 for URL '{}'", originalUrl);
        }).exceptionally(throwable -> {
            LOGGER.warn("视频缓存失败 for URL '{}'", originalUrl, throwable);
            // 清理下载失败产生的临时文件或不完整文件
            try { Files.deleteIfExists(videoFile); } catch (IOException ignored) {}
            if (audioFile != null) try { Files.deleteIfExists(audioFile); } catch (IOException ignored) {}
            return null;
        });
    }

    /**
     * 内部方法，负责将一个URL的内容下载到指定的文件路径。
     */
    private CompletableFuture<Path> downloadToFileAsync(String url, Path finalFile, String cookie) {
        if (url == null || finalFile == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            // 使用临时文件下载，成功后再重命名，避免不完整的缓存文件
            Path tempFile = Files.createTempFile(this.cacheDir, "mcedia_", ".download");
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.bilibili.com/");
            if (cookie != null && !cookie.isEmpty()) {
                reqBuilder.header("Cookie", cookie);
            }

            return client.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
                try (InputStream is = response.body()) {
                    Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
                    return finalFile;
                } catch (IOException e) {
                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                    throw new RuntimeException("缓存文件写入或重命名失败", e);
                }
            });
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 获取指定URL的缓存信息。
     * @param url 视频的原始URL。
     * @return 如果已缓存，则返回包含本地文件路径的VideoInfo对象；否则返回null。
     */
    public VideoInfo getCachedVideoInfo(String url) {
        if (!isCached(url)) return null;

        Path[] files = cachedFiles.get(url);
        String videoPath = files[0].toAbsolutePath().toString();
        String audioPath = (files[1] != null && Files.exists(files[1])) ? files[1].toAbsolutePath().toString() : null;

        // 注意：这里的标题和作者是通用的，因为我们没有缓存原始的元数据
        return new VideoInfo(videoPath, audioPath, "已缓存的视频", "Mcedia");
    }

    /**
     * 清理所有由该管理器追踪的缓存文件和记录。
     * 这个方法应该在游戏会话结束时（如退出世界）由 Mcedia 主类调用。
     */
    public void cleanup() {
        if (cachedFiles.isEmpty()) return;
        LOGGER.info("正在清理 {} 个Mcedia缓存条目...", cachedFiles.size());
        cachedFiles.values().forEach(files -> {
            try {
                if (files[0] != null) Files.deleteIfExists(files[0]);
            } catch (IOException e) { LOGGER.warn("删除视频缓存失败", e); }

            if (files.length > 1 && files[1] != null) {
                try { Files.deleteIfExists(files[1]); } catch (IOException e) { LOGGER.warn("删除音频缓存失败", e); }
            }
        });
        cachedFiles.clear();
        LOGGER.info("Mcedia 缓存已完全清理。");
    }

    /**
     * 内部方法，根据URL字符串生成一个对于文件名而言安全且唯一的哈希值。
     */
    private String generateFileNameHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // 这是一个不太可能发生的严重错误
            throw new RuntimeException("SHA-1 algorithm not found", e);
        }
    }

    public Map<String, Long> getCacheInfo() {
        if (cacheDir == null || !Files.isDirectory(cacheDir)) {
            return Collections.emptyMap();
        }
        try (Stream<Path> files = Files.list(cacheDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toMap(
                            path -> path.getFileName().toString(),
                            path -> {
                                try {
                                    return Files.size(path);
                                } catch (IOException e) {
                                    return 0L;
                                }
                            }
                    ));
        } catch (IOException e) {
            LOGGER.error("无法列出缓存目录文件", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 清空所有缓存文件
     */
    public void clearCache() {
        if (cacheDir == null || !Files.isDirectory(cacheDir)) return;

        try (Stream<Path> files = Files.list(cacheDir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    LOGGER.warn("删除缓存文件失败: {}", file, e);
                }
            });
            LOGGER.info("所有视频缓存已成功清除。");
        } catch (IOException e) {
            LOGGER.error("清空缓存时无法列出文件", e);
        }
    }
}
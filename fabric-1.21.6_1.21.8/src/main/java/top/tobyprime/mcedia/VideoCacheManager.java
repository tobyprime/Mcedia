package top.tobyprime.mcedia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class VideoCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoCacheManager.class);
    private static final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    private final Path cacheDir;

    private enum CacheState { IDLE, CACHING, COMPLETED, FAILED }
    private final AtomicReference<CacheState> state = new AtomicReference<>(CacheState.IDLE);

    private Path videoCacheFile = null;
    private Path audioCacheFile = null;

    public VideoCacheManager(Path cacheDirectory) {
        this.cacheDir = cacheDirectory;
        try {
            Files.createDirectories(this.cacheDir);
        } catch (IOException e) {
            LOGGER.error("无法创建Mcedia缓存目录: {}", this.cacheDir, e);
        }
    }

    public boolean isCached() {
        return state.get() == CacheState.COMPLETED && videoCacheFile != null && Files.exists(videoCacheFile);
    }

    public boolean isCaching() {
        return state.get() == CacheState.CACHING;
    }

    public CompletableFuture<Void> cacheVideoAsync(VideoInfo videoInfo, String cookie) {
        if (!state.compareAndSet(CacheState.IDLE, CacheState.CACHING)) {
            return CompletableFuture.completedFuture(null);
        }
        cleanup();

        CompletableFuture<Path> videoFuture = downloadToFileAsync(videoInfo.getVideoUrl(), "video", cookie);
        CompletableFuture<Path> audioFuture = (videoInfo.getAudioUrl() != null && !videoInfo.getAudioUrl().isEmpty())
                ? downloadToFileAsync(videoInfo.getAudioUrl(), "audio", cookie)
                : CompletableFuture.completedFuture(null);

        return CompletableFuture.allOf(videoFuture, audioFuture).handle((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("视频缓存失败", throwable);
                state.set(CacheState.FAILED);
                cleanup();
            } else {
                this.videoCacheFile = videoFuture.join();
                this.audioCacheFile = audioFuture.join();
                LOGGER.info("视频缓存完成. 视频: {}, 音频: {}", videoCacheFile, audioCacheFile);
                state.set(CacheState.COMPLETED);
            }
            return null;
        });
    }

    private CompletableFuture<Path> downloadToFileAsync(String url, String prefix, String cookie) {
        try {
            Path tempFile = Files.createTempFile(this.cacheDir, prefix + "_", ".download");
            Path finalFile = tempFile.resolveSibling(tempFile.getFileName().toString().replace(".download", ".mcedia_cache"));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.bilibili.com/");
            if (cookie != null && !cookie.isEmpty()) {
                requestBuilder.header("Cookie", cookie);
            }

            return client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(response -> {
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

    public VideoInfo getCachedVideoInfo() {
        if (!isCached()) {
            return null;
        }

        // 直接使用文件的绝对路径字符串，而不是 URI
        String videoPath = videoCacheFile.toAbsolutePath().toString();
        String audioPath = (audioCacheFile != null && Files.exists(audioCacheFile))
                ? audioCacheFile.toAbsolutePath().toString()
                : null;

        return new VideoInfo(videoPath, audioPath, "已缓存的视频", "Mcedia");
    }

    public void cleanup() {
        state.set(CacheState.IDLE);
        if (videoCacheFile != null) {
            try { Files.deleteIfExists(videoCacheFile); } catch (IOException e) { LOGGER.warn("删除视频缓存失败", e); }
            videoCacheFile = null;
        }
        if (audioCacheFile != null) {
            try { Files.deleteIfExists(audioCacheFile); } catch (IOException e) { LOGGER.warn("删除音频缓存失败", e); }
            audioCacheFile = null;
        }
    }
}
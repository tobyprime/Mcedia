package top.tobyprime.mcedia.video_fetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class UrlExpander {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlExpander.class);

    // 创建一个特殊配置的HttpClient，它不会自动跟随重定向
    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 异步展开URL。如果不是b23.tv短链接，则直接返回原URL。
     * @param url 原始URL
     * @return 一个包含最终URL的 CompletableFuture
     */
    public static CompletableFuture<String> expand(String url) {
        if (url == null || !url.contains("b23.tv")) {
            // 如果不是B站短链接，直接完成
            return CompletableFuture.completedFuture(url);
        }

        LOGGER.info("检测到B站短链接，正在展开: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()) // 使用HEAD请求，更高效，我们只需要响应头
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenApply(response -> {
                        // B站短链接会返回 302 状态码，并在 "Location" 头中包含完整URL
                        if (response.statusCode() == 302) {
                            String expandedUrl = response.headers().firstValue("Location").orElse(url);
                            LOGGER.info("短链接展开成功: {}", expandedUrl);
                            return expandedUrl;
                        }
                        // 如果不是302，可能链接已失效，返回原URL让后续流程处理
                        LOGGER.warn("展开短链接失败，服务器返回状态码: {}", response.statusCode());
                        return url;
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("展开短链接时发生网络错误", ex);
                        return url; // 出现异常时也返回原URL
                    });

        } catch (Exception e) {
            LOGGER.error("创建短链接展开请求失败", e);
            return CompletableFuture.completedFuture(url);
        }
    }
}
package top.tobyprime.mcedia.video_fetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class UrlExpander {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlExpander.class);
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1";

    // 匹配 b23.tv 和 v.douyin.com 的短链接
    private static final Pattern SHORT_URL_PATTERN = Pattern.compile("https://(b23\\.tv|v\\.douyin\\.com)/\\S+");

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS) // 关键：让HttpClient自动处理重定向
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 异步展开短链接
     * @param url 输入的URL
     * @return 返回一个包含最终URL的CompletableFuture。如果不是短链接，则直接返回原始URL。
     */
    public static CompletableFuture<String> expand(String url) {
        if (url == null || !SHORT_URL_PATTERN.matcher(url).find()) {
            return CompletableFuture.completedFuture(url); // 如果不是已知的短链接，直接返回
        }

        LOGGER.info("正在展开短链接: {}", url);

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", MOBILE_USER_AGENT)
                        .GET()
                        .build();

                // 发送请求，BodyHandlers.discarding()表示我们不关心响应体，只关心最终的URI
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String finalUrl = response.uri().toString();
                    LOGGER.info("短链接 {} 展开为 -> {}", url, finalUrl);
                    return finalUrl;
                } else {
                    LOGGER.warn("展开短链接失败，状态码: {}", response.statusCode());
                    return url; // 失败时返回原始URL
                }
            } catch (Exception e) {
                LOGGER.error("展开短链接时发生异常", e);
                return url; // 异常时返回原始URL
            }
        });
    }
}
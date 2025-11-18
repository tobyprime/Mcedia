package top.tobyprime.mcedia.media_play_resolvers;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.core.BaseMediaPlay;
import top.tobyprime.mcedia.core.EmptyMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlay;
import top.tobyprime.mcedia.interfaces.IMediaPlayResolver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class BilibiliShortLinkMediaPlayResolver implements IMediaPlayResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliShortLinkMediaPlayResolver.class);
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) EdgiOS/121.0.2277.107 Version/17.0 Mobile/15E148 Safari/604.1";

    // 匹配 b23.tv 和 v.douyin.com 的短链接
    private static final Pattern SHORT_URL_PATTERN = Pattern.compile("https://(b23\\.tv)/\\S+");

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
        var matcher = SHORT_URL_PATTERN.matcher(url);
        if (!matcher.find()) {
            throw new RuntimeException("不是短链");
        }
        url = matcher.group();

        LOGGER.info("正在展开短链接: {}", url);

        String finalUrl1 = url;
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(finalUrl1))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", MOBILE_USER_AGENT)
                        .GET()
                        .build();

                // 发送请求，BodyHandlers.discarding()表示我们不关心响应体，只关心最终的URI
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String finalUrl = response.uri().toString();
                    LOGGER.info("短链接 {} 展开为 -> {}", finalUrl1, finalUrl);
                    return finalUrl;
                } else {
                    LOGGER.warn("展开短链接失败，状态码: {}", response.statusCode());
                    throw new RuntimeException("展开短链接失败，状态码: "+response.statusCode());
                }
            } catch (Exception e) {
                LOGGER.error("展开短链接时发生异常", e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean isSupported(@NotNull String url) {
        return SHORT_URL_PATTERN.matcher(url).find();
    }

    @Override
    public @NotNull IMediaPlay resolve(@NotNull String playUrl) {
        return new ShortLinkMediaPlay(playUrl);
    }

    public static class ShortLinkMediaPlay extends BaseMediaPlay {
        IMediaPlay mediaPlay;
        public ShortLinkMediaPlay(String url){
            expand(url).thenAccept((trueUrl)->{
                mediaPlay = MediaPlayFactory.createMediaPlay(trueUrl);
                mediaPlay.registerOnMediaInfoUpdatedEventAndCallOnce(this::setMediaInfo);
                mediaPlay.registerOnStatusUpdatedEventAndCallOnce(this::setStatus);
            }).exceptionally(e-> {
                setStatus("无法解析短链");
                return null;
            });
        }

        @Override
        public void close() {
            super.close();
            this.mediaPlay.close();
        }
    }
}

package top.tobyprime.mcedia_platforms.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class NeteaseAuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeteaseAuthManager.class);
    private static final NeteaseAuthManager INSTANCE = new NeteaseAuthManager();

    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Gson gson = new Gson();
    private volatile NeteaseAccountStatus accountStatus = new NeteaseAccountStatus(false, false, "游客");
    private final List<NeteaseAccountStatusUpdateEventHandler> handlers = new ArrayList<>();

    private NeteaseAuthManager() {
    }

    public static NeteaseAuthManager getInstance() {
        return INSTANCE;
    }

    public NeteaseAccountStatus getAccountStatus() {
        return accountStatus;
    }

    private void setAccountStatus(NeteaseAccountStatus status) {
        this.accountStatus = status;
        this.handlers.forEach(handler -> handler.onAccountStatusUpdated(status));
    }

    public void addStatusUpdateHandler(NeteaseAccountStatusUpdateEventHandler handler) {
        handlers.add(handler);
    }

    public void logout() {
        setAccountStatus(new NeteaseAccountStatus(false, false, "游客"));
        NeteaseCookie.saveCookies("");
    }

    public CompletableFuture<String> checkAndUpdateLoginStatusAsync() {
        var cookie = NeteaseCookie.getCookie();
        if (cookie == null || cookie.isEmpty()) {
            setAccountStatus(new NeteaseAccountStatus(false, false, ""));
            return CompletableFuture.completedFuture("无本地登录数据");
        }

        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/api/v1/user/info"))
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", cookie)
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .handle((body, err) -> {
                    if (err != null) {
                        setAccountStatus(new NeteaseAccountStatus(false, false, ""));
                        return "检查网易云Cookie时发生网络错误";
                    }

                    try {
                        var json = gson.fromJson(body, JsonObject.class);
                        if (json.get("code").getAsInt() == 200) {
                            var profile = json.getAsJsonObject("profile");
                            var nickname = profile.get("nickname").getAsString();
                            var vipType = profile.get("vipType").getAsInt();
                            setAccountStatus(new NeteaseAccountStatus(true, vipType >= 1, vipType >= 1 ? nickname + " [黑胶VIP]" : nickname));
                            return "登录成功";
                        }
                        throw new IllegalStateException("API返回未登录状态或错误码: " + json.get("code").getAsInt());
                    } catch (Exception e) {
                        setAccountStatus(new NeteaseAccountStatus(false, false, ""));
                        NeteaseCookie.saveCookies("");
                        return "登录已失效";
                    }
                });
    }

    public CompletableFuture<String> loginAsync(NeteaseLoginQrCodeHandler qrCodeHandler) {
        LOGGER.info("loginAsync: 开始网易云登录流程");
        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/api/login/qrcode/unikey"))
                .header("User-Agent", "Mozilla/5.0")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    LOGGER.info("loginAsync: 二维码key生成完成, statusCode={}", response.statusCode());
                    return response.body();
                })
                .thenCompose(body -> {
                    var json = gson.fromJson(body, JsonObject.class);
                    var code = json.get("code").getAsInt();
                    if (code != 200) {
                        throw new IllegalStateException("获取二维码key失败, code=" + code);
                    }
                    var unikey = json.getAsJsonObject("data").get("unikey").getAsString();
                    var qrContentUrl = "https://music.163.com/login?codekey=" + unikey;
                    var encodedUrl = URLEncoder.encode(qrContentUrl, StandardCharsets.UTF_8);
                    var qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=" + encodedUrl;

                    LOGGER.info("loginAsync: 调用onDisplayQrCode回调");
                    qrCodeHandler.onDisplayQrCode(qrImageUrl);
                    LOGGER.info("loginAsync: 开始轮询扫码状态, unikey={}", unikey);
                    return waitForScanQrCodeAsync(unikey);
                })
                .thenCompose(v -> {
                    LOGGER.info("loginAsync: 扫码成功，检查登录状态");
                    return checkAndUpdateLoginStatusAsync();
                })
                .exceptionally(err -> {
                    LOGGER.error("loginAsync: 登录流程异常", err);
                    return "登录失败: " + err.getMessage();
                });
    }

    private CompletableFuture<Void> waitForScanQrCodeAsync(String unikey) {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var future = new CompletableFuture<Void>();
        var startTime = System.currentTimeMillis();
        var holder = new AtomicReference<ScheduledFuture<?>>();

        Runnable task = () -> {
            if (future.isDone()) {
                var scheduledFuture = holder.get();
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(true);
                }
                scheduler.shutdownNow();
                return;
            }

            try {
                if (System.currentTimeMillis() - startTime > 180_000) {
                    throw new IllegalStateException("登录超时");
                }

                var request = HttpRequest.newBuilder()
                        .uri(URI.create("https://music.163.com/api/login/qrcode/client/code?key=" + unikey))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();

                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                var json = gson.fromJson(response.body(), JsonObject.class);
                var code = json.get("code").getAsInt();

                // 800 = 已扫码未确认, 801 = 等待扫码, 802 = 已扫码待确认, 803 = 授权成功
                if (code == 803 || code == 800) {
                    var cookieHeaders = response.headers().allValues("Set-Cookie");
                    if (!cookieHeaders.isEmpty()) {
                        var fullCookie = cookieHeaders.stream()
                                .map(h -> h.split(";", 2)[0])
                                .collect(Collectors.joining("; "));
                        NeteaseCookie.saveCookies(fullCookie);
                    }
                    future.complete(null);
                    return;
                }

                if (code == 802) {
                    LOGGER.info("loginAsync: 已扫码，等待确认");
                    return;
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        };

        holder.set(scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS));
        future.whenComplete((v, ex) -> {
            var scheduledFuture = holder.get();
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
            }
            scheduler.shutdownNow();
        });
        return future;
    }
}

package top.tobyprime.mcedia_platforms.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.resolver.MediaResolvers;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 网易云账号登录态管理。
 * <p>
 * 提供两种本地登录方式：
 * <ul>
 *   <li><b>扫码登录</b>：通过 web 端 {@code /api/login/qrcode/unikey} 生成二维码，
 *       手机网易云 App 扫码后由 {@code /api/login/qrcode/client/login} 轮询确认；</li>
 *   <li><b>手动 Cookie 导入</b>：{@link #saveCookies(String)} 粘贴浏览器登录态（MUSIC_U 等）。</li>
 * </ul>
 * 登录后解析器可利用账号权益请求更高音质（VIP 无损 / 非 VIP 高码率）。
 */
public final class NeteaseAuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeteaseAuthManager.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5L);
    private static final long LOGIN_TIMEOUT_MILLIS = 180_000L;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
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

    private void setAccountStatus(NeteaseAccountStatus accountStatus) {
        var previous = this.accountStatus;
        this.accountStatus = accountStatus;
        this.handlers.forEach(handler -> handler.onAccountStatusUpdated(accountStatus));
        // 登录态/VIP/昵称变化时通知解析器：后续解析可重新请求更高音质
        if (previous == null
                || previous.isLoggedIn != accountStatus.isLoggedIn
                || previous.isVip != accountStatus.isVip
                || !Objects.equals(previous.nickname, accountStatus.nickname)) {
            MediaResolvers.notifyStateChanged();
        }
    }

    public void addStatusUpdateHandler(NeteaseAccountStatusUpdateEventHandler handler) {
        handlers.add(handler);
    }

    public void logout() {
        setAccountStatus(new NeteaseAccountStatus(false, false, "游客"));
        NeteaseCookie.saveCookies("");
    }

    /** 保存并规范化 Cookie，随后异步校验登录态并更新账号状态。 */
    public void saveCookies(String rawCookies) {
        NeteaseCookie.saveCookies(rawCookies);
        checkAndUpdateLoginStatusAsync();
    }

    public CompletableFuture<String> checkAndUpdateLoginStatusAsync() {
        var cookie = NeteaseCookie.getCookie();
        if (!NeteaseCookie.isLoggedIn()) {
            setAccountStatus(new NeteaseAccountStatus(false, false, ""));
            return CompletableFuture.completedFuture("无本地登录数据");
        }

        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/api/v1/user/info"))
                .header("User-Agent", UA)
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
        // 登录使用独立的 CookieManager 客户端，确保账号 Cookie 被完整收集
        CookieManager loginCookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient loginHttpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(loginCookieManager)
                .build();

        LOGGER.info("loginAsync: 开始网易云扫码登录流程");
        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/api/login/qrcode/unikey"))
                .header("User-Agent", UA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("type=3"))
                .build();

        return loginHttpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenCompose(body -> {
                    var json = gson.fromJson(body, JsonObject.class);
                    var code = json.get("code").getAsInt();
                    if (code != 200) {
                        throw new IllegalStateException("获取二维码key失败, code=" + code);
                    }
                    var unikey = json.get("unikey").getAsString();
                    var qrContentUrl = "https://music.163.com/login?codekey=" + unikey;
                    var encodedUrl = URLEncoder.encode(qrContentUrl, StandardCharsets.UTF_8);
                    var qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=" + encodedUrl;

                    LOGGER.info("loginAsync: 调用onDisplayQrCode回调");
                    qrCodeHandler.onDisplayQrCode(qrImageUrl);
                    LOGGER.info("loginAsync: 开始轮询扫码状态, unikey={}", unikey);
                    return waitForScanQrCodeAsync(unikey, loginHttpClient, loginCookieManager.getCookieStore());
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

    private CompletableFuture<Void> waitForScanQrCodeAsync(String unikey, HttpClient loginHttpClient, CookieStore loginCookieStore) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<Void> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();
        AtomicReference<ScheduledFuture<?>> holder = new AtomicReference<>();

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
                if (System.currentTimeMillis() - startTime > LOGIN_TIMEOUT_MILLIS) {
                    throw new IllegalStateException("登录超时");
                }

                var request = HttpRequest.newBuilder()
                        .uri(URI.create("https://music.163.com/api/login/qrcode/client/login"))
                        .header("User-Agent", UA)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("key=" + unikey + "&type=3"))
                        .build();

                var response = loginHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
                var json = gson.fromJson(response.body(), JsonObject.class);
                var code = json.get("code").getAsInt();

                // 801 等待扫码，802 已扫码待确认，803 授权成功，800 二维码已过期
                if (code == 803) {
                    var fullCookie = collectLoginCookies(loginCookieStore, response.headers().allValues("Set-Cookie"));
                    if (!NeteaseCookie.isLoggedIn() || fullCookie.isBlank()) {
                        throw new IllegalStateException("登录成功，但未能获取完整的账号 Cookie");
                    }
                    NeteaseCookie.saveCookies(fullCookie);
                    future.complete(null);
                    return;
                }
                if (code == 802) {
                    LOGGER.info("loginAsync: 已扫码，等待确认");
                    return;
                }
                if (code == 800) {
                    throw new IllegalStateException("二维码已过期，请重新登录");
                }
                // 801 或其他状态继续轮询
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

    /** 从 CookieStore 与 Set-Cookie 响应头两个来源合并账号 Cookie，避免遗漏。 */
    private static String collectLoginCookies(CookieStore cookieStore, List<String> setCookieHeaders) {
        LinkedHashMap<String, String> cookiesByName = new LinkedHashMap<>();
        if (cookieStore != null) {
            for (HttpCookie cookie : cookieStore.getCookies()) {
                addLoginCookie(cookiesByName, cookie);
            }
        }
        if (setCookieHeaders != null) {
            for (String header : setCookieHeaders) {
                try {
                    for (HttpCookie cookie : HttpCookie.parse(header)) {
                        addLoginCookie(cookiesByName, cookie);
                    }
                } catch (IllegalArgumentException exception) {
                    LOGGER.debug("Ignore malformed Netease Set-Cookie header during QR login");
                }
            }
        }
        return NeteaseCookie.normalizeForStorage(cookiesByName.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; ")));
    }

    private static void addLoginCookie(Map<String, String> cookiesByName, HttpCookie cookie) {
        if (cookie == null || cookie.hasExpired()
                || cookie.getName() == null || cookie.getName().isBlank()
                || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return;
        }
        cookiesByName.put(cookie.getName(), cookie.getValue());
    }
}

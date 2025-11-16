package top.tobyprime.mcedia.bilibili;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 负责 bilibili 账户的状态管理、登录
 */

public class BilibiliAuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliAuthManager.class);
    private static final BilibiliAuthManager INSTANCE = new BilibiliAuthManager();
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Gson gson = new Gson();

    private volatile BilibiliAccountStatus accountStatus = new BilibiliAccountStatus(false, false, "游客");
    private List<BilibiliAccountStatusUpdateEventHandler> handlers = new ArrayList<>();

    private BilibiliAuthManager() {
    }

    public static BilibiliAuthManager getInstance() {
        return INSTANCE;
    }

    public BilibiliAccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void AddStatusUpdateHandler(BilibiliAccountStatusUpdateEventHandler handler) {
        handlers.add(handler);
    }

    public void logout() {
        LOGGER.info("正在执行登出操作...");
        this.setAccountStatus(new BilibiliAccountStatus(false, false, "游客"));
        BilibiliConfigs.saveCookies("");
    }

    private void setAccountStatus(BilibiliAccountStatus accountStatus) {
        this.handlers.forEach(y -> y.OnAccountStatusUpdated(accountStatus));
        this.accountStatus = accountStatus;

    }

    public CompletableFuture<String> checkAndUpdateLoginStatusAsync() {
        String cookie = BilibiliConfigs.getCookie();

        // 本地没有 cookie → 直接返回一个已完成的 Future
        if (cookie == null || cookie.isEmpty()) {
            setAccountStatus(new BilibiliAccountStatus(false, false, ""));

            return CompletableFuture.completedFuture("无本地登录数据");
        }

        LOGGER.info("正在检查Bilibili Cookie有效性...");

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.bilibili.com/x/web-interface/nav")).header("User-Agent", "Mozilla/5.0").header("Cookie", cookie).build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body).handle((body, err) -> {
            // 网络错误
            if (err != null) {
                setAccountStatus(new BilibiliAccountStatus(false, false, ""));

                LOGGER.error("检查Bilibili Cookie时发生网络错误", err);

                return "检查Bilibili Cookie时发生网络错误";
            }

            try {
                JsonObject json = gson.fromJson(body, JsonObject.class);

                if (json.get("code").getAsInt() == 0 && json.getAsJsonObject("data").get("isLogin").getAsBoolean()) {

                    JsonObject data = json.getAsJsonObject("data");

                    String uname = data.get("uname").getAsString();
                    int vipStatus = data.get("vipStatus").getAsInt();

                    setAccountStatus(new BilibiliAccountStatus(true, vipStatus == 1, vipStatus == 1 ? uname + " [大会员]" : uname));

                    LOGGER.info("Bilibili Cookie有效，当前登录用户: {}", this.accountStatus.username);
                    return "登录成功";
                }

                throw new Exception("API返回未登录状态或错误码: " + json.get("code").getAsInt());

            } catch (Exception e) {
                setAccountStatus(new BilibiliAccountStatus(false, false, ""));

                LOGGER.warn("检查Bilibili Cookie时失败: {}", e.getMessage());
                BilibiliConfigs.saveCookies("");

                return "登录已失效";
            }
        }).thenApply(x -> {
            handlers.forEach(y -> y.OnAccountStatusUpdated(this.accountStatus));
            return x;
        });
    }


    public CompletableFuture<String> loginAsync(BilibiliLoginQrCodeHandler qrCode) {
        // 请求二维码
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header")).header("User-Agent", "Mozilla/5.0").GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body).thenCompose(body -> {
                    try {
                        JsonObject json = gson.fromJson(body, JsonObject.class);

                        if (json.get("code").getAsInt() != 0) {
                            throw new BilibiliException("生成二维码失败");
                        }

                        JsonObject data = json.getAsJsonObject("data");
                        String qrContentUrl = data.get("url").getAsString();
                        String qrcodeKey = data.get("qrcode_key").getAsString();

                        // 生成二维码图片链接
                        String encodedUrl = URLEncoder.encode(qrContentUrl, StandardCharsets.UTF_8);
                        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=" + encodedUrl;

                        // 显示二维码
                        qrCode.onDisplayQrCode(qrImageUrl);

                        return waitForScanQrCodeAsync(qrcodeKey);

                    } catch (Exception e) {
                        LOGGER.error("Failed to parse QR code response", e);
                        throw new BilibiliException("生成二维码失败", e);
                    }
                })
                // 等待扫码结束，检查 Cookie 是否正常并更新用户信息
                .thenCompose(v -> checkAndUpdateLoginStatusAsync()).exceptionally(err -> {
                    LOGGER.error("Bilibili login failed", err);
                    return "登录失败: " + err.getMessage();
                });
    }

    /**
     * 轮询等待用户扫码
     */
    private CompletableFuture<Void> waitForScanQrCodeAsync(String qrcodeKey) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        CompletableFuture<Void> future = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        // 以延迟赋值
        AtomicReference<ScheduledFuture<?>> holder = new AtomicReference<>();

        Runnable task = () -> {
            // 已完成 → 停止轮询
            if (future.isDone()) {
                holder.get().cancel(true);
                scheduler.close();
                return;
            }

            try {
                if (System.currentTimeMillis() - startTime > 180_000) {
                    throw new BilibiliException("登录超时");
                }

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + qrcodeKey + "&source=main-fe-header")).header("User-Agent", "Mozilla/5.0").GET().build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                JsonObject data = json.getAsJsonObject("data");
                int code = data.get("code").getAsInt();

                if (code == 0) { // 登录成功
                    List<String> cookieHeaders = response.headers().allValues("Set-Cookie");
                    if (cookieHeaders.isEmpty()) {
                        throw new BilibiliException("登录成功，但未能获取到Cookie");
                    }

                    String fullCookie = cookieHeaders.stream().map(h -> h.split(";", 2)[0]).collect(Collectors.joining("; "));

                    BilibiliConfigs.saveCookies(fullCookie);
                    future.complete(null); // 登录完成
                    return;
                }

                if (code == 86038) { // 二维码失效
                    throw new BilibiliException("二维码已过期，请重新登录");
                }

            } catch (Exception e) {
                LOGGER.error("轮询等待扫码出错", e);
                future.completeExceptionally(e);
            }
        };

        // 启动轮询并保存 ScheduledFuture 用来取消
        holder.set(scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS));

        // 确保 future 完成时，自动停止轮询
        future.whenComplete((v, ex) -> {
            holder.get().cancel(true);
            scheduler.close();
        });

        return future;
    }

}
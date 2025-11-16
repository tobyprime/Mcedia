package top.tobyprime.mcedia.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.McediaConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class BilibiliAuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BilibiliAuthManager.class);
    private static final BilibiliAuthManager INSTANCE = new BilibiliAuthManager();
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Gson gson = new Gson();

    private volatile boolean isLoggedIn = false;
    private volatile String username = "";

    private BilibiliAuthManager() {}

    public static BilibiliAuthManager getInstance() {
        return INSTANCE;
    }

    public boolean isLoggedIn() {
        return this.isLoggedIn;
    }

    public String getUsername() {
        return this.username;
    }

    public void logout() {
        LOGGER.info("正在执行登出操作...");
        this.isLoggedIn = false;
        this.username = "";
        McediaConfig.saveCookie("");
        Mcedia.msgToPlayer("§a[Mcedia] §f已成功登出Bilibili账号。");
    }

    public void checkCookieValidityAndNotifyPlayer() {
        String cookie = McediaConfig.BILIBILI_COOKIE;
        if (cookie == null || cookie.isEmpty()) {
            this.isLoggedIn = false;
            this.username = "";
            return;
        }
        LOGGER.info("正在检查Bilibili Cookie有效性...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.bilibili.com/x/web-interface/nav"))
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", cookie)
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        if (json.get("code").getAsInt() == 0 && json.getAsJsonObject("data").get("isLogin").getAsBoolean()) {
                            this.isLoggedIn = true;
                            JsonObject data = json.getAsJsonObject("data");
                            String uname = data.get("uname").getAsString();
                            int vipStatus = data.get("vipStatus").getAsInt();
                            String displayName = uname;
                            if (vipStatus == 1) {
                                displayName += " §c(大会员)";
                            }
                            this.username = displayName;
                            LOGGER.info("Bilibili Cookie有效，当前登录用户: {}", this.username);
                            Component successMessage = Component.literal("§a[Mcedia] §fBilibili Cookie 有效！当前登录用户: §b" + this.username);
                            Minecraft.getInstance().execute(() -> Mcedia.msgToPlayer(successMessage));
                        } else {
                            throw new Exception("API返回未登录状态或错误码: " + json.get("code").getAsInt());
                        }
                    } catch (Exception e) {
                        this.isLoggedIn = false;
                        this.username = "";
                        LOGGER.warn("检查Bilibili Cookie时失败: {}", e.getMessage());
                        Component errorMessage = Component.literal("§e[Mcedia] §f你的Bilibili登录已过期，请使用 §a/mcedia login §f重新登录。");
                        Minecraft.getInstance().execute(() -> Mcedia.msgToPlayer(errorMessage));
                        McediaConfig.saveCookie("");
                    }
                }).exceptionally(e -> {
                    this.isLoggedIn = false;
                    this.username = "";
                    LOGGER.error("检查Bilibili Cookie时发生网络错误", e);
                    Component networkErrorMessage = Component.literal("§c[Mcedia] §f检查Bilibili登录状态时发生网络错误。");
                    Minecraft.getInstance().execute(() -> Mcedia.msgToPlayer(networkErrorMessage));
                    return null;
                });
    }

    public void startLoginFlow() {
        Mcedia.msgToPlayer("§a[Mcedia] §f正在生成登录二维码...");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header"))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(body -> {
                        try {
                            JsonObject json = gson.fromJson(body, JsonObject.class);
                            if (json.get("code").getAsInt() == 0) {
                                JsonObject data = json.getAsJsonObject("data");
                                String qrContentUrl = data.get("url").getAsString();
                                String qrcodeKey = data.get("qrcode_key").getAsString();
                                String encodedUrl = URLEncoder.encode(qrContentUrl, StandardCharsets.UTF_8);
                                String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=" + encodedUrl;
                                Style style = Style.EMPTY
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, qrImageUrl))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("在浏览器中打开二维码，并使用B站手机App扫描")));

                                Mcedia.msgToPlayer("§a[Mcedia] §f请点击下方链接，在打开的网页中用B站App扫描二维码登录：");
                                Mcedia.msgToPlayer(Component.literal("§b§n[点我打开二维码]").setStyle(style));
                                Mcedia.getInstance().getBackgroundExecutor().submit(() -> pollLoginStatus(qrcodeKey));
                            } else {
                                Mcedia.msgToPlayer("§c[Mcedia] §f生成二维码失败: " + json.get("message").getAsString());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to parse QR code response", e);
                            Mcedia.msgToPlayer("§c[Mcedia] §f解析服务器响应失败。");
                        }
                    })
                    .exceptionally(e -> {
                        LOGGER.error("Failed to generate QR code", e);
                        Mcedia.msgToPlayer("§c[Mcedia] §f生成二维码时发生网络错误: " + e.getCause().getMessage());
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Login flow failed to start", e);
            Mcedia.msgToPlayer("§c[Mcedia] §f登录流程启动失败: " + e.getMessage());
        }
    }

    private void pollLoginStatus(String qrcodeKey) {
        long startTime = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - startTime < 180_000) { // 轮询3分钟
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + qrcodeKey + "&source=main-fe-header"))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                JsonObject data = json.getAsJsonObject("data");
                int code = data.get("code").getAsInt();

                if (code == 0) { // 登录成功
                    List<String> cookieHeaders = response.headers().allValues("Set-Cookie");
                    if (cookieHeaders.isEmpty()) {
                        Mcedia.msgToPlayer("§c[Mcedia] §f登录成功，但未能获取到Cookie，请重试。");
                        return;
                    }

                    String fullCookie = cookieHeaders.stream()
                            .map(header -> header.split(";", 2)[0])
                            .collect(Collectors.joining("; "));

                    McediaConfig.saveCookie(fullCookie);
                    Mcedia.msgToPlayer("§a[Mcedia] §f登录成功！Cookie已自动更新。");
                    checkCookieValidityAndNotifyPlayer();
                    return;
                } else if (code == 86038) { // 二维码已失效
                    Mcedia.msgToPlayer("§c[Mcedia] §f二维码已过期，请重新执行 /mcedia login。");
                    return;
                } else if (code == 86090) { // 二维码已扫，待确认
                    // Do nothing, just wait for confirmation
                }

                Thread.sleep(3000);
            }
            Mcedia.msgToPlayer("§c[Mcedia] §f登录超时，请重新执行 /mcedia login。");
        } catch (Exception e) {
            LOGGER.error("Polling Bilibili login status failed", e);
            Mcedia.msgToPlayer("§c[Mcedia] §f轮询登录状态时出错。");
        }
    }
}
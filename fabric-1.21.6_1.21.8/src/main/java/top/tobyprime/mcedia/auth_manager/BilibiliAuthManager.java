package top.tobyprime.mcedia.auth_manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.McediaConfig;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mojang.datafixers.kinds.IdF.create;

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

    // 检查 cookie 状态
    public void checkCookieValidityAndNotifyPlayer() {
        if (McediaConfig.BILIBILI_COOKIE == null || McediaConfig.BILIBILI_COOKIE.isEmpty()) {
            this.isLoggedIn = false;
            this.username = "";
            return; // 没有Cookie，无需检查
        }

        LOGGER.info("正在检查Bilibili Cookie有效性...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.bilibili.com/x/web-interface/nav"))
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", McediaConfig.BILIBILI_COOKIE)
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    try {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        // code为0表示cookie有效，非0表示无效
                        if (json.get("code").getAsInt() != 0) {
                            this.isLoggedIn = false;
                            this.username = "";
                            Mcedia.msgToPlayer("§e[Mcedia] §f你的Bilibili登录已过期，请使用 §a/mcedia login §f重新登录。");
                            McediaConfig.saveCookie(""); // 清空无效的cookie
                        } else {
                            this.isLoggedIn = true;
                            this.username = json.getAsJsonObject("data").get("uname").getAsString();
                            LOGGER.info("Bilibili Cookie有效，当前登录用户: {}", username);
                            Mcedia.msgToPlayer("§a[Mcedia] §fBilibili账号已登录: " + username);
                        }
                    } catch (Exception e) {
                        this.isLoggedIn = false;
                        this.username = "";
                        LOGGER.error("检查Bilibili Cookie时解析响应失败", e);
                    }
                }).exceptionally(e -> {
                    this.isLoggedIn = false;
                    this.username = "";
                    LOGGER.error("检查Bilibili Cookie时发生网络错误", e);
                    return null;
                });
    }

    public void startLoginFlow() {
        Mcedia.msgToPlayer("§a[Mcedia] §f正在生成登录链接...");
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
                                String qrUrl = data.get("url").getAsString();
                                String qrcodeKey = data.get("qrcode_key").getAsString();

                                Style style = Style.EMPTY
                                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(qrUrl)))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("在浏览器中打开Bilibili登录页面")));
                                Mcedia.msgToPlayer("§a[Mcedia] §f请复制该链接到手机浏览器中登录：");
                                Mcedia.msgToPlayer(Component.literal("§b§n[点我复制链接]").setStyle(style));

                                new Thread(() -> pollLoginStatus(qrcodeKey), "Mcedia-Bili-Login-Poll").start();
                            } else {
                                Mcedia.msgToPlayer("§c[Mcedia] §f生成链接失败: " + json.get("message").getAsString());
                            }
                        } catch (Exception e) {
                            Mcedia.msgToPlayer("§c[Mcedia] §f解析服务器响应失败。");
                        }
                    })
                    .exceptionally(e -> {
                        Mcedia.msgToPlayer("§c[Mcedia] §f生成链接时发生网络错误: " + e.getCause().getMessage());
                        return null;
                    });
        } catch (Exception e) {
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
                    // 从HTTP响应头中获取完整Cookie
                    List<String> cookieHeaders = response.headers().allValues("Set-Cookie");
                    if (cookieHeaders.isEmpty()) {
                        Mcedia.msgToPlayer("§c[Mcedia] §f登录成功，但未能获取到Cookie，请重试。");
                        return;
                    }

                    // 将所有Set-Cookie头中的cookie值（分号前的部分）拼接起来
                    String fullCookie = cookieHeaders.stream()
                            .map(header -> header.split(";", 2)[0])
                            .collect(Collectors.joining("; "));

                    this.isLoggedIn = true;
                    McediaConfig.saveCookie(fullCookie);
                    Mcedia.msgToPlayer("§a[Mcedia] §f登录成功！Cookie已自动更新。");
                    checkCookieValidityAndNotifyPlayer(); // 立即检查并显示用户名
                    return;
                } else if (code == 86038) { // 链接过期
                    Mcedia.msgToPlayer("§c[Mcedia] §f链接已过期，请重新执行 /mcedia login。");
                    return;
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
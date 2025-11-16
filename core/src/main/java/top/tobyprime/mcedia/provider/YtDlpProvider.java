package top.tobyprime.mcedia.provider;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.interfaces.IMediaProvider;
import top.tobyprime.mcedia.interfaces.IYtDlpManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class YtDlpProvider implements IMediaProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpProvider.class);
    private static IYtDlpManager managerInstance;
    private static final Object YTDLP_LOCK = new Object();

    public static void setManager(IYtDlpManager manager) {
        managerInstance = manager;
    }

    @Override
    public boolean isSupported(String url) {
        return url.startsWith("http");
    }

    @Override
    public VideoInfo resolve(String url, @Nullable String cookie, String desiredQuality) throws Exception {
        String formatSelector = "bv[vcodec^=avc1]+ba[acodec^=mp4a]/b[vcodec^=avc1]/bv*+ba/b";
        List<String> args = List.of("-f", formatSelector, "--dump-json");
        JSONObject finalJson = executeYtDlp(url, cookie, args.toArray(new String[0]));
        return parseJsonToVideoInfo(finalJson);
    }

    private VideoInfo parseJsonToVideoInfo(JSONObject json) {
        String title = json.optString("title", "未知标题");
        String author = json.optString("uploader", null);
        if (author == null) author = json.optString("creator", null);
        if (author == null) author = json.optString("channel", "未知作者");
        long cid = json.optLong("id", 0);
        String videoUrl = null;
        String audioUrl = null;
        String currentQuality = "自动";
        JSONArray reqFormats = json.optJSONArray("requested_formats");
        if (reqFormats != null && !reqFormats.isEmpty()) {
            JSONObject videoFormatJson = reqFormats.getJSONObject(0);
            videoUrl = videoFormatJson.getString("url");
            currentQuality = videoFormatJson.optInt("height", 0) + "p";
            if (reqFormats.length() >= 2) {
                audioUrl = reqFormats.getJSONObject(1).getString("url");
            }
        } else {
            videoUrl = json.getString("url");
            currentQuality = json.optInt("height", 0) + "p";
        }
        if (videoUrl == null) {
            throw new RuntimeException("在 yt-dlp JSON 输出中找不到视频 URL。");
        }
        List<QualityInfo> availableQualities = new ArrayList<>();
        Set<Integer> addedHeights = new HashSet<>();
        JSONArray allFormats = json.optJSONArray("formats");
        if (allFormats != null) {
            for (int i = 0; i < allFormats.length(); i++) {
                JSONObject format = allFormats.getJSONObject(i);
                if (!"none".equals(format.optString("vcodec")) && format.has("height")) {
                    int height = format.getInt("height");
                    if (addedHeights.add(height)) {
                        String qualityDesc = height + "p";
                        availableQualities.add(new QualityInfo(qualityDesc));
                    }
                }
            }
            availableQualities.sort(Comparator.comparingInt(q -> -Integer.parseInt(q.description.replace("p", ""))));
        }
        if (availableQualities.isEmpty() && !currentQuality.equals("0p")) {
            availableQualities.add(new QualityInfo(currentQuality));
        }
        LOGGER.info("通过 yt-dlp 成功解析 '{}', 作者: '{}', 当前画质: {}", title, author, currentQuality);
        return new VideoInfo(videoUrl, audioUrl, title, author, null, null, 1, availableQualities, currentQuality, false, cid);
    }

    private static JSONObject executeYtDlp(String url, @Nullable String cookie, String... args) throws Exception {
        synchronized (YTDLP_LOCK) {
            if (managerInstance == null) throw new IllegalStateException("YtDlpManager is not initialized.");
            Path ytDlpPath = managerInstance.getExecutablePath();
            if (ytDlpPath == null || !Files.exists(ytDlpPath)) throw new RuntimeException("yt-dlp executable not found.");

            Path workingDir = ytDlpPath.getParent();
            Path cookieFile = null;

            try {
                List<String> command = new ArrayList<>(List.of(ytDlpPath.toString()));

                String browser = McediaConfig.YTDLP_BROWSER_COOKIE;
                if (browser != null && !browser.equalsIgnoreCase("none") && !browser.isBlank()) {
                    command.add("--cookies-from-browser");
                    command.add(browser.toLowerCase());
                    LOGGER.info("正在尝试从浏览器 '{}' 加载 Cookie...", browser);
                }
                else if (cookie != null && !cookie.isBlank()) {
                    cookieFile = workingDir.resolve("cookies.txt");
                    Files.writeString(cookieFile, cookie);
                    command.add("--cookies");
                    command.add(cookieFile.toString());
                    LOGGER.debug("正在为 yt-dlp 使用临时 Cookie 文件: {}", cookieFile);
                }

                command.addAll(List.of(args));
                command.add(url);

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                processBuilder.directory(workingDir.toFile());

                Process process = processBuilder.start();

                String jsonLine = null;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    String lastLine = null;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            lastLine = line;
                        }
                    }
                    if (lastLine != null && lastLine.trim().startsWith("{")) {
                        jsonLine = lastLine;
                    }
                }

                boolean finished = process.waitFor(60, TimeUnit.SECONDS);
                if (!finished || process.exitValue() != 0 || jsonLine == null) {
                    throw new RuntimeException("yt-dlp 进程失败或没有返回 JSON。");
                }
                return new JSONObject(jsonLine);

            } finally {
                if (cookieFile != null) {
                    try {
                        Files.deleteIfExists(cookieFile);
                        LOGGER.debug("已删除临时 Cookie 文件。");
                    } catch (IOException e) {
                        LOGGER.warn("删除临时 Cookie 文件失败: {}", cookieFile, e);
                    }
                }
            }
        }
    }
}
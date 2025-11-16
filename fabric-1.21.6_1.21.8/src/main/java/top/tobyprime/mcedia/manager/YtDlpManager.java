package top.tobyprime.mcedia.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.Mcedia;
import top.tobyprime.mcedia.McediaConfig;
import top.tobyprime.mcedia.interfaces.IYtDlpManager; // 引入接口

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class YtDlpManager implements IYtDlpManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpManager.class);
    private static final YtDlpManager INSTANCE = new YtDlpManager();

    private Path executablePath = null;
    private boolean isInitialized = false;

    private YtDlpManager() {}

    public static YtDlpManager getInstance() {
        return INSTANCE;
    }

    @Override
    public synchronized Path getExecutablePath() {
        if (!isInitialized) {
            initialize();
        }
        return executablePath;
    }

    public synchronized void initialize() {
        if (isInitialized) return;

        try {
            String os = System.getProperty("os.name").toLowerCase();
            String executableName = os.contains("win") ? "yt-dlp.exe" : "yt-dlp";
            String resourcePath = "/assets/mcedia/bin/" + executableName;

            Path cacheDir = Mcedia.getCacheDirectory();
            if (cacheDir == null) {
                throw new IOException("Cache directory is not available.");
            }
            Path destinationPath = cacheDir.resolve(executableName);

            try (InputStream stream = YtDlpManager.class.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    throw new IOException("yt-dlp executable not found in mod resources: " + resourcePath);
                }
                Files.copy(stream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Extracted yt-dlp executable to: {}", destinationPath);
            }

            if (!os.contains("win")) {
                try {
                    if(!Files.isExecutable(destinationPath)) {
                        destinationPath.toFile().setExecutable(true);
                        LOGGER.info("Set executable permission for yt-dlp on non-Windows OS.");
                    }
                } catch (SecurityException e) {
                    LOGGER.error("Failed to set executable permission for yt-dlp.", e);
                }
            }

            executablePath = destinationPath;

        } catch (Exception e) {
            LOGGER.error("Failed to initialize and extract yt-dlp executable.", e);
            executablePath = null;
        } finally {
            isInitialized = true;
        }
    }
}
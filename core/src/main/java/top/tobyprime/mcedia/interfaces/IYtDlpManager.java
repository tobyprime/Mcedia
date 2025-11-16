package top.tobyprime.mcedia.interfaces;

import java.nio.file.Path;

/**
 * An interface to abstract the functionality of managing the yt-dlp executable.
 * This allows the core module to use yt-dlp without depending on platform-specific code.
 */
public interface IYtDlpManager {
    /**
     * Gets the path to the yt-dlp executable, handling extraction and permissions.
     * @return The path to the executable, or null if it's not available.
     */
    Path getExecutablePath();
}
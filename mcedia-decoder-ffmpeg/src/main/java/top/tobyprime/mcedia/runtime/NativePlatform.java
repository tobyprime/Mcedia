package top.tobyprime.mcedia.runtime;

import java.util.Locale;

public final class NativePlatform {
    private NativePlatform() {
    }

    public static String detect() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String archName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String os;
        if (osName.contains("win")) os = "windows";
        else if (osName.contains("mac") || osName.contains("darwin")) os = "macosx";
        else if (osName.contains("linux")) os = "linux";
        else return "unsupported";

        String arch;
        if (archName.equals("amd64") || archName.equals("x86_64")) arch = "x86_64";
        else if (archName.equals("aarch64") || archName.equals("arm64")) arch = "arm64";
        else return "unsupported";
        return os + "-" + arch;
    }

    public static boolean isWindowsBuiltin() {
        return "windows-x86_64".equals(detect());
    }
}

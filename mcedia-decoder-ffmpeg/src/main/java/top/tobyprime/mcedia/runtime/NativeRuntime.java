package top.tobyprime.mcedia.runtime;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avdevice;
import org.bytedeco.ffmpeg.global.avfilter;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.postproc;
import org.bytedeco.ffmpeg.global.swresample;
import org.bytedeco.ffmpeg.global.swscale;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads and loads platform native libraries without changing the Mod distribution. */
public final class NativeRuntime {
    public enum State { BUILTIN, MISSING, DOWNLOADING, READY, FAILED }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private static final AtomicReference<State> STATE = new AtomicReference<>();
    private static volatile Path root;
    private static volatile URI archiveUri;
    private static volatile String expectedSha256;
    private static volatile String platform;
    private static volatile boolean loaded;

    private NativeRuntime() {
    }

    public static void configure(Path gameDirectory, String version, String downloadBaseUrl, String sha256) {
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        platform = NativePlatform.detect();
        if (NativePlatform.isWindowsBuiltin()) {
            root = gameDirectory.resolve("config").resolve("mcedia").resolve("native").resolve(version).resolve(platform);
            STATE.set(State.BUILTIN);
            return;
        }
        root = gameDirectory.resolve("config").resolve("mcedia").resolve("native").resolve(version).resolve(platform);
        archiveUri = URI.create(downloadBaseUrl.replaceAll("/+$", "") + "/mcedia-native-" + platform + "-" + version + ".zip");
        expectedSha256 = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
        STATE.set(isUsable(root, expectedSha256) ? State.READY : State.MISSING);
    }

    public static String platform() {
        return platform == null ? NativePlatform.detect() : platform;
    }

    public static State state() {
        State current = STATE.get();
        if (current == null) {
            return NativePlatform.isWindowsBuiltin() ? State.BUILTIN : State.MISSING;
        }
        return current;
    }

    public static boolean isAvailable() {
        return state() == State.BUILTIN || (state() == State.READY && loaded);
    }

    public static boolean ensureLoaded() {
        State current = state();
        if (current == State.BUILTIN) return true;
        if (current == State.READY && loaded) return true;
        if (current != State.READY && current != State.MISSING) return false;
        if (!isUsable(root, expectedSha256)) return false;
        try {
            loadExtracted(root);
            loaded = true;
            STATE.set(State.READY);
            return true;
        } catch (Throwable error) {
            loaded = false;
            STATE.set(State.FAILED);
            return false;
        }
    }

    public static CompletableFuture<State> downloadAsync() {
        State current = state();
        if (current == State.BUILTIN) {
            return CompletableFuture.completedFuture(State.BUILTIN);
        }
        if (current == State.READY && ensureLoaded()) {
            return CompletableFuture.completedFuture(State.READY);
        }
        if (archiveUri == null || root == null) {
            STATE.set(State.FAILED);
            return CompletableFuture.completedFuture(State.FAILED);
        }
        STATE.set(State.DOWNLOADING);
        return CompletableFuture.supplyAsync(() -> {
            Path temp = root.resolveSibling(root.getFileName() + ".download");
            try {
                Files.createDirectories(root.getParent());
                Files.deleteIfExists(temp);
                HttpRequest request = HttpRequest.newBuilder(archiveUri)
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", "Mcedia-NativeRuntime")
                        .GET().build();
                HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(temp));
                if (response.statusCode() / 100 != 2) throw new IOException("native download HTTP " + response.statusCode());
                String checksum = expectedSha256;
                if (checksum.isEmpty()) checksum = downloadChecksum();
                if (!checksum.isEmpty() && !checksum.equals(sha256(temp))) {
                    throw new IOException("native archive checksum mismatch");
                }
                Path staging = root.resolveSibling(root.getFileName() + ".staging");
                deleteTree(staging);
                Files.createDirectories(staging);
                extractZip(temp, staging);
                if (!isUsable(staging, "")) throw new IOException("native archive contains no libraries");
                deleteTree(root);
                try {
                    Files.move(staging, root, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(staging, root, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.deleteIfExists(temp);
                STATE.set(State.READY);
                if (!ensureLoaded()) throw new IOException("native libraries failed to load");
                return STATE.get();
            } catch (Throwable error) {
                STATE.set(State.FAILED);
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
                return State.FAILED;
            }
        });
    }

    private static boolean isUsable(Path directory, String checksum) {
        if (directory == null || !Files.isDirectory(directory)) return false;
        try {
            boolean nativeFile = Files.walk(directory).anyMatch(NativeRuntime::isNativeFile);
            return nativeFile && (checksum.isEmpty() || Files.exists(directory.resolve(".verified")));
        } catch (IOException error) {
            return false;
        }
    }

    private static String downloadChecksum() throws IOException, InterruptedException {
        URI checksumUri = URI.create(archiveUri.toString().substring(0, archiveUri.toString().lastIndexOf('/'))
                + "/SHA256SUMS-" + platform);
        HttpRequest request = HttpRequest.newBuilder(checksumUri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mcedia-NativeRuntime")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) return "";
        String archiveName = archiveUri.toString().substring(archiveUri.toString().lastIndexOf('/') + 1);
        for (String line : response.body().split("\\R")) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 2 && parts[1].trim().equals(archiveName)) return parts[0].toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static void extractZip(Path archive, Path destination) throws IOException {
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) throw new IOException("invalid native archive path");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    target.toFile().setExecutable(true, false);
                }
            }
        }
        Files.writeString(destination.resolve(".verified"), "verified\n");
    }

    private static void loadExtracted(Path directory) throws IOException {
        Files.walk(directory).filter(NativeRuntime::isNativeFile)
                .sorted(Comparator.comparingInt(NativeRuntime::loadOrder))
                .forEach(path -> System.load(path.toAbsolutePath().toString()));
        Loader.load(avutil.class);
        Loader.load(avcodec.class);
        Loader.load(avformat.class);
        Loader.load(avfilter.class);
        Loader.load(avdevice.class);
        Loader.load(swresample.class);
        Loader.load(swscale.class);
        Loader.load(postproc.class);
    }

    private static int loadOrder(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains("javacpp")) return 0;
        if (name.contains("avutil")) return 1;
        if (name.contains("swresample") || name.contains("swscale")) return 2;
        if (name.contains("avcodec")) return 3;
        if (name.contains("avformat") || name.contains("avdevice")) return 4;
        if (name.contains("avfilter") || name.contains("postproc")) return 5;
        return name.startsWith("jni") ? 6 : 7;
    }

    private static boolean isNativeFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib");
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try { Files.deleteIfExists(item); } catch (IOException error) { throw new RuntimeException(error); }
            });
        }
    }
}

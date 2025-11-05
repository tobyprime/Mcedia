package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.ITexture;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class MediaPlayer {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MediaPlayer-Async");
        t.setDaemon(true);
        return t;
    });

    // --- 核心修复：引入一个锁 ---
    private final ReentrantLock lock = new ReentrantLock();

    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    private @Nullable ITexture texture;
    @Nullable
    private Media media;
    private volatile DecoderConfiguration decoderConfiguration = new DecoderConfiguration(new DecoderConfiguration.Builder());

    public synchronized void bindTexture(ITexture texture) {
        this.texture = texture;
        if (media != null) media.bindTexture(texture);
    }

    public synchronized void unbindTexture() {
        if (media != null) media.unbindTexture();
    }

    public synchronized void bindAudioSource(IAudioSource audioBuffer) {
        audioSources.add(audioBuffer);
        if (media != null) media.bindAudioSource(audioBuffer);
    }

    public synchronized void unbindAudioSource(IAudioSource audioBuffer) {
        audioSources.remove(audioBuffer);
        if (media != null) media.unbindAudioSource(audioBuffer);
    }

    public static void shutdownExecutor() { executor.shutdownNow(); }
    public DecoderConfiguration getDecoderConfiguration() { return decoderConfiguration; }
    public void setDecoderConfiguration(DecoderConfiguration decoderConfiguration) { this.decoderConfiguration = decoderConfiguration; }

    private void closeInternal() {
        Media preMedia;
        // 使用 synchronized(this) 保证对 media 字段的原子性访问
        synchronized (this) {
            if (media == null) return;
            preMedia = media;
            media = null;
        }
        // 在锁外关闭，避免长时间持有锁
        preMedia.close();
    }

    public boolean looping = false;
    @Nullable
    public synchronized Media getMedia() { return media; }

    public CompletableFuture<?> closeAsync() {
        return CompletableFuture.runAsync(() -> {
            lock.lock();
            try {
                closeInternal();
            } finally {
                lock.unlock();
            }
        }, executor);
    }

    public CompletableFuture<?> openAsync(String inputMedia) {
        return CompletableFuture.runAsync(() -> openInternal(inputMedia), executor);
    }

    public CompletableFuture<?> openAsync(Supplier<String> inputMediaSupplier) {
        return CompletableFuture.runAsync(() -> openInternal(inputMediaSupplier.get()), executor);
    }

    public CompletableFuture<?> openAsyncWithVideoInfo(Supplier<VideoInfo> videoInfoSupplier, @Nullable Supplier<String> cookieSupplier) {
        return CompletableFuture.runAsync(() -> {
            openInternal(videoInfoSupplier.get(), cookieSupplier != null ? cookieSupplier.get() : null);
        }, executor);
    }

    public synchronized void play() { if (media != null) media.play(); }
    public synchronized void pause() { if (media != null) media.pause(); }
    public void seek(long ms) {
        Media currentMedia;
        synchronized(this) {
            currentMedia = this.media;
        }
        if (currentMedia != null) currentMedia.seek(ms);
    }
    float speed = 1;

    private void openInternal(String inputMedia) {
        // --- 核心修复：使用锁来保护整个 open 流程 ---
        lock.lock();
        try {
            closeInternal();
            if (inputMedia == null) return;
            var newMedia = new Media(inputMedia, decoderConfiguration);
            bindResourcesToMedia(newMedia);
        } finally {
            lock.unlock();
        }
    }

    // 重载 openInternal 方法
    private void openInternal(VideoInfo info, @Nullable String cookie) {
        // --- 核心修复：使用锁来保护整个 open 流程 ---
        lock.lock();
        try {
            closeInternal();
            if (info == null) return;
            var newMedia = new Media(info, cookie, decoderConfiguration);
            bindResourcesToMedia(newMedia);
        } finally {
            lock.unlock();
        }
    }

    // 提取公共逻辑
    private void bindResourcesToMedia(Media newMedia) {
        newMedia.bindTexture(texture);
        for (var audioSource : audioSources) {
            newMedia.bindAudioSource(audioSource);
        }
        synchronized (this) {
            media = newMedia;
        }
        media.setSpeed(speed);
        media.setLooping(looping);
    }

    public synchronized void setSpeed(float speed) { this.speed = speed; if (media != null) media.setSpeed(speed); }
    public synchronized void setLooping(boolean looping) { this.looping = looping; if (media != null) media.setLooping(looping); }
    public synchronized float getProgress() { if (media != null && media.getLengthUs() > 0) { return (float) media.getDurationUs() / media.getLengthUs(); } return 0; }
}
package top.tobyprime.mcedia.core;

import org.bytedeco.javacv.FFmpegLogCallback;
import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.IMediaInfo;
import top.tobyprime.mcedia.interfaces.ITexture;
import top.tobyprime.mcedia.provider.VideoInfo;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;

public class MediaPlayer {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MediaPlayer-Async");
        t.setDaemon(true);
        return t;
    });

    static {
        FFmpegLogCallback.set();
        av_log_set_level(AV_LOG_ERROR);
    }

    private final ReentrantLock lock = new ReentrantLock();

    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    private @Nullable ITexture texture;
    @Nullable
    private Media media;
    @Nullable private VideoInfo currentVideoInfo;
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

    public static void shutdownExecutor() {
        executor.shutdownNow();
    }

    public DecoderConfiguration getDecoderConfiguration() {
        return decoderConfiguration;
    }

    public void setDecoderConfiguration(DecoderConfiguration decoderConfiguration) {
        this.decoderConfiguration = decoderConfiguration;
    }

    private void closeInternal() {
        Media preMedia;
        synchronized (this) {
            if (media == null) return;
            preMedia = media;
            media = null;
        }
        preMedia.close();
    }

    public boolean looping = false;

    @Nullable
    public synchronized Media getMedia() {
        return media;
    }

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

    public void closeSync() {
        Media preMedia;
        // 使用锁来确保同一时间只有一个线程在进行开启或关闭操作
        lock.lock();
        try {
            synchronized (this) {
                if (media == null) return;
                preMedia = media;
                media = null;
            }
        } finally {
            lock.unlock();
        }

        if (preMedia != null) {
            preMedia.close();
        }
    }

    public CompletableFuture<?> openAsync(String inputMedia) {
        return CompletableFuture.runAsync(() -> openInternal(inputMedia), executor);
    }

    public CompletableFuture<?> openAsync(Supplier<String> inputMediaSupplier) {
        return CompletableFuture.runAsync(() -> openInternal(inputMediaSupplier.get()), executor);
    }

    public void openSync(VideoInfo info, @Nullable String cookie) {
        openSync(info, cookie, 0);
    }

    public void openSync(VideoInfo info, @Nullable String cookie, long initialSeekUs) {
        lock.lock();
        try {
            closeInternal();
            if (info == null) return;
            var newMedia = new Media(info, cookie, decoderConfiguration, initialSeekUs);
            bindResourcesToMedia(newMedia);
        } finally {
            lock.unlock();
        }
    }

    public CompletableFuture<VideoInfo> openAsyncWithVideoInfo(Supplier<VideoInfo> videoInfoSupplier, @Nullable Supplier<String> cookieSupplier, long initialSeekUs) {
        return CompletableFuture.supplyAsync(() -> {
            VideoInfo info = videoInfoSupplier.get();
            String cookie = (cookieSupplier != null) ? cookieSupplier.get() : null;
            openInternal(info, cookie, initialSeekUs);
            return info;
        }, executor);
    }

    public synchronized void play() {
        if (media != null) media.play();
    }

    public synchronized void pause() {
        if (media != null) media.pause();
    }

    public void seek(long us) {
        final Media currentMedia;
        synchronized (this) {
            currentMedia = this.media;
        }
        if (currentMedia != null) {
            executor.execute(() -> {
                currentMedia.seek(us);
            });
        }
    }

    float speed = 1;

    private void openInternal(String inputMedia) {
        lock.lock();
        try {
            closeInternal();
            if (inputMedia == null) return;
            var newMedia = new Media(new VideoInfo(inputMedia, null), null, decoderConfiguration, 0);
            bindResourcesToMedia(newMedia);
        } finally {
            lock.unlock();
        }
    }

    private void openInternal(VideoInfo info, @Nullable String cookie, long initialSeekUs) {
        closeSync();
        lock.lock();
        try {
            if (info == null) return;
            this.currentVideoInfo = info;
            var newMedia = new Media(info, cookie, decoderConfiguration, initialSeekUs);
            bindResourcesToMedia(newMedia);
        } finally {
            lock.unlock();
        }
    }

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

    public void bindResourcesToCurrentMedia(Media newMedia) {
        this.bindResourcesToMedia(newMedia);
    }

    public synchronized void setSpeed(float speed) {
        this.speed = speed;
        if (media != null) media.setSpeed(speed);
    }

    public synchronized void setLooping(boolean looping) {
        this.looping = looping;
        if (media != null) media.setLooping(looping);
    }

    public synchronized float getProgress() {
        if (media != null && media.getLengthUs() > 0) {
            return (float) (getMedia().getDurationUs() / (double) getMedia().getLengthUs());
        }
        return 0;
    }

    public synchronized boolean isBuffering() {
        if (media != null) {
            return media.isBuffering();
        }
        return false;
    }

    @Nullable
    public synchronized IMediaInfo getMediaInfo() {
        return this.currentVideoInfo;
    }
}
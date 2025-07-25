package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.ITexture;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class MediaPlayer {
    // 全局共享线程池（单线程或多线程）
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MediaPlayer-Async");
        t.setDaemon(true);
        return t;
    });
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    private @Nullable ITexture texture;

    @Nullable
    private Media media;
    private volatile DecoderConfiguration decoderConfiguration = new DecoderConfiguration(new DecoderConfiguration.Builder());

    public synchronized void bindTexture(ITexture texture) {
        this.texture = texture;
        if (media != null) {
            media.bindTexture(texture);
        }
    }

    public synchronized void unbindTexture() {
        if (media != null) {
            media.unbindTexture();
        }
    }

    public synchronized void bindAudioSource(IAudioSource audioBuffer) {
        this.audioSources.add(audioBuffer);
        if (media != null) {
            media.bindAudioSource(audioBuffer);
        }
    }

    public synchronized void unbindAudioSource(IAudioSource audioBuffer) {
        this.audioSources.remove(audioBuffer);
        if (media != null) {
            media.unbindAudioSource(audioBuffer);
        }
    }
    /**
     * 关闭线程池（全局）
     */
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
        Media preMedia = null;
        synchronized (this) {
            if (media != null) {
                preMedia = media;
                media = null;
            }
        }
        if (preMedia != null) {
            preMedia.close();
        }
    }

    private void openInternal(String inputMedia) {
        closeInternal();
        if (inputMedia == null) {
            return;
        }
        var newMedia = new Media(inputMedia, decoderConfiguration);

        newMedia.bindTexture(texture);
        for (var audioSource : audioSources) {
            newMedia.bindAudioSource(audioSource);
        }
        synchronized (this) {
            media = newMedia;
        }
    }

    public synchronized @Nullable Media getMedia() {
        return media;
    }


    /**
     * 异步关闭
     */
    public  CompletableFuture<?> closeAsync() {
        return CompletableFuture.runAsync(this::closeInternal, executor);
    }

    /**
     * 异步打开（会先关闭当前媒体）
     */
    public CompletableFuture<?> openAsync(String inputMedia) {
        return CompletableFuture.runAsync(() -> {
            openInternal(inputMedia);
        }, executor);
    }

    public CompletableFuture<?> openAsync(Supplier<String> inputMediaSupplier) {
        return CompletableFuture.runAsync(() -> {
            openInternal(inputMediaSupplier.get());
        }, executor);
    }

    public synchronized void play(){
        if (media != null) {
            media.play();
        }
    }
    public synchronized void pause(){
        if (media != null) {
            media.pause();
        }
    }
    public synchronized void seek(int ms){
        if (media != null) {
            media.seek(ms);
        }
    }
}


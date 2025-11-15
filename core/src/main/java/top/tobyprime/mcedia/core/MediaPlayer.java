package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.decoders.DecoderConfiguration;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.ITexture;
import top.tobyprime.mcedia.media_play_resolvers.MediaPlayFactory;
import top.tobyprime.mcedia.video_fetcher.MediaInfo;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 播放器核心，同时只播放单一媒体，管理媒体切换等
 */
public class MediaPlayer implements Closeable {
    private static Logger LOGGER = LoggerFactory.getLogger(MediaPlayer.class);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MediaPlayer-Async");
        t.setDaemon(true);
        return t;
    });
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    public boolean looping = false;
    float speed = 1;
    private @Nullable ITexture texture;
    private volatile boolean loading;

    @Nullable
    private Media media;
    private volatile IMediaPlay mediaPlay;

    private volatile DecoderConfiguration decoderConfiguration = new DecoderConfiguration(new DecoderConfiguration.Builder());

    /**
     * 关闭线程池（全局）
     */
    public static void shutdownExecutor() {
        executor.shutdownNow();
    }

    public IMediaPlay getMediaPlay() {
        return mediaPlay;
    }

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

    public float getAspectRatio() {
        return media == null ? 0 : media.getAspectRatio();
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

    public DecoderConfiguration getDecoderConfiguration() {
        return decoderConfiguration;
    }

    public void setDecoderConfiguration(DecoderConfiguration decoderConfiguration) {
        this.decoderConfiguration = decoderConfiguration;
    }

    /**
     * 关闭当前 Media
     */
    private void stopMediaInternal() {
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

    public synchronized @Nullable Media getMedia() {
        return media;
    }

    /**
     * 异步关闭 Media 以及 Media Play
     */
    public void stop() {
        CompletableFuture.runAsync(this::stopMediaInternal, executor).thenAccept(x -> mediaPlay.close());
    }

    /**
     * 异步打开（会先关闭当前媒体）
     */
    private void openMedia(MediaInfo mediaInfo, Consumer<MediaInfo> afterOpened) {
        CompletableFuture.runAsync(() -> {
            LOGGER.info("读取: {}", mediaInfo.streamUrl);
            openMediaInternal(mediaInfo);
            if (afterOpened != null)
                afterOpened.accept(mediaInfo);
        }, executor);
    }

    public void open(IMediaPlay mediaPlay, Consumer<MediaInfo> afterOpened) {
        this.mediaPlay = mediaPlay;

        mediaPlay.registerOnMediaInfoUpdatedEventAndCallOnce(mediaInfo -> {
            if (this.mediaPlay != mediaPlay) {
                return;
            }

            if (mediaInfo != null) {
                openMedia(mediaInfo, afterOpened);
            }
        });
    }

    public IMediaPlay getMediaPlayAndOpen(String url, Consumer<MediaInfo> afterOpened) {
        var mediaPlay = MediaPlayFactory.createMediaPlay(url);
        open(mediaPlay, afterOpened);
        return mediaPlay;
    }

    public synchronized void uploadVideo() {
        if (media != null) {
            media.uploadVideo();
        }
    }

    public synchronized void play() {
        if (media != null) {
            media.play();
        }
    }

    public synchronized void pause() {
        if (media != null) {
            media.pause();
        }
    }

    public void seek(long ms) {
        Media preMedia = null;
        synchronized (this) {
            if (media != null) {
                preMedia = media;
            }
        }
        if (media != null) {
            preMedia.seek(ms);
        }
    }

    private void openMediaInternal(@Nullable MediaInfo inputMedia) {
        loading = true;
        try {
            stopMediaInternal();
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
            media.setSpeed(speed);
            media.setLooping(looping);
        } finally {
            loading = false;
        }
    }

    public synchronized void setSpeed(float speed) {
        this.speed = speed;
        if (media != null) {
            media.setSpeed(speed);
        }
    }

    public synchronized void setLooping(boolean looping) {
        this.looping = looping;
        if (media != null) media.setLooping(looping);
    }

    public synchronized float getProgress() {
        if (media != null) {
            if (media.getLengthUs() <= 0) {
                return 0;
            }
            return (float) media.getDurationUs() / media.getLengthUs();
        }
        return 0;
    }

    @Override
    public void close() {
        stopMediaInternal();
    }
}


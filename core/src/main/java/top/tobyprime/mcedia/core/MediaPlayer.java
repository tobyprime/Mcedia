package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.danmaku.Danmaku;
import top.tobyprime.mcedia.danmaku.DanmakuEntity;
import top.tobyprime.mcedia.decoders.DecoderConfiguration;
import top.tobyprime.mcedia.interfaces.IAudioSource;
import top.tobyprime.mcedia.interfaces.IMediaPlay;
import top.tobyprime.mcedia.interfaces.ITexture;
import top.tobyprime.mcedia.media_play_resolvers.MediaPlayFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 播放器核心，同时只播放单一媒体，管理媒体切换等
 */
public class MediaPlayer implements Closeable {
    public volatile PlayerStatus status;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MediaPlayer-Async");
        t.setDaemon(true);
        return t;
    });
    @Nullable
    public static Function<Danmaku, Float> danmakuWidthPredictor;
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaPlayer.class);
    private final ArrayList<IAudioSource> audioSources = new ArrayList<>();
    public boolean looping = false;
    float speed = 1;
    private @Nullable ITexture texture;
    @Nullable
    private Media media;
    private volatile IMediaPlay mediaPlay;
    private boolean lowOverhead =false;

    private volatile DecoderConfiguration decoderConfiguration = new DecoderConfiguration(new DecoderConfiguration.Builder());

    public void setLowOverhead(boolean lowOverhead) {
        var media = this.media;
        if (media != null){
            media.setLowOverhead(lowOverhead);
        }
        this.lowOverhead = lowOverhead;
    }

    public IMediaPlay getMediaPlay() {
        return mediaPlay;
    }

    public PlayerStatus getStatus() {
        return status;
    }

    public synchronized void bindTexture(ITexture texture) {
        this.texture = texture;
        if (media != null) {
            media.bindTexture(texture);
        }
    }


    public @Nullable Collection<DanmakuEntity> updateAndGetDanmakus() {
        var media = getMedia();
        if (media != null) {
            return getMedia().updateAndGetDanmakus();
        }
        return null;
    }

    public synchronized void unbindTexture() {
        if (media != null) {
            media.unbindTexture();
        }
    }

    public void setDanmakuWidthPredictor(@Nullable Function<Danmaku, Float> danmakuWidthPredictor) {
        MediaPlayer.danmakuWidthPredictor = danmakuWidthPredictor;
        var media = getMedia();
        if (media != null) {
            media.setDanmakuWidthPredictor(danmakuWidthPredictor);
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
    private void openMedia(@NotNull MediaInfo mediaInfo, Consumer<Media> afterOpened) {
        CompletableFuture.runAsync(() -> {
            LOGGER.info("读取: {}", mediaInfo.streamUrl);
            var media = openMediaInternal(mediaInfo);
            if (afterOpened != null)
                afterOpened.accept(media);
            this.status = PlayerStatus.PLAYING;
        }, executor).exceptionally(
                e -> {
                    this.status = PlayerStatus.ERROR;
                    LOGGER.error("打开 {} 失败", mediaInfo.streamUrl, e);
                    return null;
                }
        );
    }

    public void open(IMediaPlay mediaPlay, Consumer<Media> afterOpened) {
        this.mediaPlay = mediaPlay;
        this.status = PlayerStatus.LOADING_MEDIA_INFO;

        mediaPlay.registerOnMediaInfoUpdatedEventAndCallOnce(mediaInfo -> {
            if (this.mediaPlay != mediaPlay) {
                return;
            }
            this.status = PlayerStatus.LOADING_MEDIA;

            if (mediaInfo != null) {
                openMedia(mediaInfo, afterOpened);
            }
        });
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

    private Media openMediaInternal(@NotNull MediaInfo inputMedia) {
        stopMediaInternal();
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
        media.setDanmakuWidthPredictor(danmakuWidthPredictor);
        media.setLowOverhead(lowOverhead);
        return newMedia;
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

    public long getLength() {
        if (media != null) {
            if (media.getLength() <= 0) {
                return 0;
            }
            return media.getLength();
        }
        return 0;
    }

    public long getDuration() {
        if (media != null) {
            if (media.getDuration() <= 0) {
                return 0;
            }
            return media.getDuration();
        }
        return 0;
    }

    public IMediaPlay getMediaPlayAndOpen(String url, Consumer<Media> afterOpened) {
        var mediaPlay = MediaPlayFactory.createMediaPlay(url);
        open(mediaPlay, afterOpened);
        return mediaPlay;
    }

    public synchronized float getProgress() {
        if (media != null) {
            if (media.getLength() <= 0) {
                return 0;
            }
            return (float) media.getDuration() / media.getLength();
        }
        return 0;
    }

    public void close() {
        stopMediaInternal();
    }
}


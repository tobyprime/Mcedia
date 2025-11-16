package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.McediaConfig;

public class DecoderConfiguration {
    public final @Nullable String userAgent;
    public final boolean enableVideo;
    public final boolean enableAudio;
    public final int cacheDuration;
    public final boolean useHardwareDecoding;
    public final boolean videoAlpha;
    public final int audioSampleRate;

    public final int timeout;
    public final int bufferSize;
    public final int probesize;

    public DecoderConfiguration(Builder builder) {
        this.userAgent = builder.userAgent;
        this.enableVideo = builder.enableVideo;
        this.enableAudio = builder.enableAudio;
        this.cacheDuration = builder.cacheDuration;
        this.useHardwareDecoding = builder.useHardwareDecoding;
        this.videoAlpha = builder.videoAlpha;
        this.audioSampleRate = builder.audioSampleRate;

        this.timeout = builder.timeout;
        this.bufferSize = builder.bufferSize;
        this.probesize = builder.probesize;
    }

    public static class Builder {
        private @Nullable String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        private boolean enableVideo = true;
        private boolean enableAudio = true;
        private int cacheDuration = 2000000; // 2s 缓冲区
        private boolean useHardwareDecoding = true; // 默认开启硬件解码，对4K至关重要
        private boolean videoAlpha = true;
        private int audioSampleRate = 44100;

        private int timeout = McediaConfig.getFfmpegTimeout();
        private int bufferSize = McediaConfig.getFfmpegBufferSize();
        private int probesize = McediaConfig.getFfmpegProbeSize();

        public Builder setEnableVideo(boolean enableVideo) {
            this.enableVideo = enableVideo;
            return this;
        }

        public Builder setEnableAudio(boolean enableAudio) {
            this.enableAudio = enableAudio;
            return this;
        }

        public Builder setUseHardwareDecoding(boolean useHardwareDecoding) {
            this.useHardwareDecoding = McediaConfig.isHardwareDecodingEnabled();
            return this;
        }

        public Builder setVideoAlpha(boolean videoAlpha) {
            this.videoAlpha = videoAlpha;
            return this;
        }

        public Builder setUserAgent(@Nullable String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder setCacheDuration(int cacheDuration) {
            this.cacheDuration = cacheDuration;
            return this;
        }

        public Builder setAudioSampleRate(int audioSampleRate) {
            this.audioSampleRate = audioSampleRate;
            return this;
        }

        public Builder setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder setTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder setProbesize(int probesize) {
            this.probesize = probesize;
            return this;
        }

        public DecoderConfiguration build() {
            return new DecoderConfiguration(this);
        }
    }
}
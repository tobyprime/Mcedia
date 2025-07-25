package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;

public class DecoderConfiguration {
    public final @Nullable String userAgent;
    public final boolean enableVideo;
    public final boolean enableAudio;
    public final int cacheDuration ;
    public final boolean useHardwareDecoding;
    public final boolean videoAlpha ;
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
        private @Nullable String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36";
        private boolean enableVideo = true;
        private boolean enableAudio = true;

        private int cacheDuration = 2000000; // 1s 缓冲区

        private boolean useHardwareDecoding = true;
        private boolean videoAlpha = true;

        private int audioSampleRate = 44100;


        private int timeout = 5000000; // 5s time out
        private int bufferSize = 262144; // 256kb 缓冲区
        private int probesize = 5000000;
        public void disableVideo() {
            this.enableVideo = false;
        }

        public Builder disableAudio() {
            this.enableAudio = false;
            return this;
        }
        public Builder disableHardwareDecoding() {
            this.useHardwareDecoding = false;
            return this;
        }
        public Builder disableVideoAlpha() {
            this.videoAlpha = false;
            return this;
        }
        public Builder userAgent(@Nullable String userAgent) {
            this.userAgent = userAgent;
            return this;
        }
        public Builder cacheDuration(int cacheDuration) {
            this.cacheDuration = cacheDuration;
            return this;
        }

        public Builder audioSampleRate(int audioSampleRate) {
            this.audioSampleRate = audioSampleRate;
            return this;
        }
        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }
        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }
        public Builder probesize(int probesize) {
            this.probesize = probesize;
            return this;
        }

    }
}

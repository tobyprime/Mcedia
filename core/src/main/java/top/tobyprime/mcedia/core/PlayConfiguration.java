package top.tobyprime.mcedia.core;

public class PlayConfiguration implements Cloneable {
    private final String inputUrl;
    private final boolean enableVideo;
    private final boolean enableAudio;

    private final int videoBufferSize;
    private final int audioBufferSize;
    private final int maxVideoQueueLength;
    private final int maxAudioQueueLength;

    private final boolean useHardwareDecoding;
    private final boolean videoAlpha;
    private final int audioSampleRate;
    private final boolean audioDualChannel;


    public PlayConfiguration(String inputUrl, boolean enableVideo, boolean enableAudio, int videoBufferSize, int audioBufferSize, int maxVideoQueueLength, int maxAudioQueueLength, boolean separateDecodeThreads, boolean useHardwareDecoding, boolean videoAlpha, int audioSampleRate, boolean audioDualChannel) {
        this.inputUrl = inputUrl;
        this.enableVideo = enableVideo;
        this.enableAudio = enableAudio;
        this.videoBufferSize = videoBufferSize;
        this.audioBufferSize = audioBufferSize;
        this.maxVideoQueueLength = maxVideoQueueLength;
        this.maxAudioQueueLength = maxAudioQueueLength;
        this.useHardwareDecoding = useHardwareDecoding;
        this.videoAlpha = videoAlpha;
        this.audioSampleRate = audioSampleRate;
        this.audioDualChannel = audioDualChannel;
    }

    public PlayConfiguration(String inputUrl) {
        this(inputUrl, true, true, 30, 30, 100, 1000, false, false, false, 44100, false);
    }

    public PlayConfiguration(String inputUrl, boolean enableVideo, boolean enableAudio) {
        this(inputUrl, enableVideo, enableAudio, 30, 30, 100, 1000, false, false, false, 44100, false);
    }

    public PlayConfiguration(String inputUrl, boolean videoAlpha, int audioSampleRate, boolean audioDualChannel) {
        this(inputUrl, true, true, 30, 30, 100, 1000, false, false, videoAlpha, audioSampleRate, audioDualChannel);
    }

    public int getAudioSampleRate() {
        return audioSampleRate;
    }

    public boolean isAudioDualChannel() {
        return audioDualChannel;
    }

    public boolean isEnableVideo() {
        return enableVideo;
    }

    public boolean isEnableAudio() {
        return enableAudio;
    }

    public int getVideoBufferSize() {
        return videoBufferSize;
    }

    public int getAudioBufferSize() {
        return audioBufferSize;
    }

    public int getMaxVideoQueueLength() {
        return maxVideoQueueLength;
    }

    public int getMaxAudioQueueLength() {
        return maxAudioQueueLength;
    }

    public boolean isUseHardwareDecoding() {
        return useHardwareDecoding;
    }

    public String getInputUrl() {
        return inputUrl;
    }

    @Override
    public PlayConfiguration clone() {
        try {
            PlayConfiguration clone = (PlayConfiguration) super.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public boolean isVideoAlpha() {
        return videoAlpha;
    }
}

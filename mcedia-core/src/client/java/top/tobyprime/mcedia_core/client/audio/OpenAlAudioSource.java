package top.tobyprime.mcedia_core.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.audio.AudioFrame;
import top.tobyprime.mcedia.api.audio.AudioSource;
import top.tobyprime.mcedia.player.config.Configs;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class OpenAlAudioSource implements AudioSource, AutoCloseable {

    private enum AlErrorHandling {
        LOG_ONLY,
        INVALIDATE_AUDIO_STATE
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAlAudioSource.class);
    private static final int BUFFER_COUNT = 4;
    private static final float DEFAULT_MAX_DISTANCE = 16.0F;
    private static final double MICROS_PER_SECOND = 1_000_000.0D;

    private static final ConcurrentHashMap<Integer, OpenAlAudioSource> ACTIVE_SOURCES = new ConcurrentHashMap<>();

    private final MinecraftSoundEngineAdapter soundEngineAdapter;
    private final Supplier<Vec3> positionSupplier;
    private final Queue<Integer> availableBuffers = new ArrayBlockingQueue<>(BUFFER_COUNT);
    private final Map<Integer, Long> bufferTimestamp = new ConcurrentHashMap<>();

    volatile int sourceId; // package-private for compat access
    private volatile int[] bufferIds;
    private volatile boolean playing = true;
    private volatile boolean closed;
    private volatile double speed = 1.0;
    private volatile float volume = 1.0F;
    private volatile float maxDistance = DEFAULT_MAX_DISTANCE;
    private volatile int sampleRate = -1;
    private volatile SpeakerAudioChannelMode channelMode = SpeakerAudioChannelMode.MIX;
    private volatile long lastFrameTimestamp = -1L;
    private volatile long cachedPlaytime = -1L;
    private boolean relative;

    public OpenAlAudioSource(MinecraftSoundEngineAdapter soundEngineAdapter, Supplier<Vec3> positionSupplier) {
        this.soundEngineAdapter = soundEngineAdapter;
        this.positionSupplier = positionSupplier;
    }

    @Override
    public void tick() {
        if (closed) {
            return;
        }

        var position = positionSupplier.get();
        float currentVolume = volume;
        float currentMaxDistance = maxDistance;
        float currentPitch = (float)speed;
        boolean shouldPlay = playing;

        soundEngineAdapter.executeBlockingOnAudioThread(() -> {
            tickInternal(position, currentVolume, currentMaxDistance, currentPitch, shouldPlay);
            return null;
        });
    }

    @Override
    public void upload(@NotNull AudioFrame frame) {
        if (closed) {
            return;
        }

        var pcm16Mono = toPcm16Mono(frame.getBuffer(channelMode.frameChannel()));
        if (!pcm16Mono.hasRemaining()) {
            return;
        }

        int frameSampleRate = frame.getSampleRate();
        long frameTime = frame.getTime();

        soundEngineAdapter.executeBlockingOnAudioThread(() -> {
            uploadInternal(frameSampleRate, frameTime, pcm16Mono);
            return null;
        });
    }

    @Override
    public boolean getRequiredFrame() {
        if (closed) {
            return false;
        }

        Boolean required = soundEngineAdapter.executeBlockingOnAudioThread(() -> {
            if (!initInternal()) {
                return false;
            }

            cleanupProcessedBuffersInternal();
            return !availableBuffers.isEmpty();
        });
        if (required != null) {
            return required;
        }
        return sourceId == 0 || bufferIds == null || !availableBuffers.isEmpty();
    }

    @Override
    public long getPlaytime() {
        Long playtime = soundEngineAdapter.executeBlockingOnAudioThread(this::queryPlaytimeInternal);
        if (playtime != null) {
            cachedPlaytime = playtime;
        }
        return cachedPlaytime;
    }

    @Override
    public void pause() {
        if (closed || !playing) {
            return;
        }
        playing = false;
        soundEngineAdapter.executeOnAudioThread(() -> {
            if (closed || sourceId == 0) {
                return;
            }
            AL10.alSourcePause(sourceId);
            checkError("pause source", AlErrorHandling.INVALIDATE_AUDIO_STATE);
        });
    }

    @Override
    public void play() {
        if (closed || playing) {
            return;
        }
        playing = true;
        soundEngineAdapter.executeOnAudioThread(() -> {
            if (closed) {
                return;
            }
            tryStartPlaybackInternal();
        });
    }

    @Override
    public void setSpeed(double speed) {
        this.speed = speed;
    }

    @Override
    public void setVolume(float gain) {
        this.volume = Mth.clamp(gain, 0.0F, 4.0F);
    }

    @Override
    public float getVolume() {
        return this.volume;
    }

    public int getSourceId() {
        return sourceId;
    }

    public Vec3 getPos() {
        return positionSupplier.get();
    }

    public static Set<OpenAlAudioSource> getActiveSources() {
        return Set.copyOf(ACTIVE_SOURCES.values());
    }

    public void setChannelMode(SpeakerAudioChannelMode channelMode) {
        this.channelMode = channelMode;
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = sanitizeMaxDistance(maxDistance);
    }

    public void setRelative(boolean relative) {
        this.relative = relative;
    }

    @Override
    public void clear() {
        soundEngineAdapter.executeBlockingOnAudioThread(() -> {
            clearInternal();
            return null;
        });
    }

    @Override
    public boolean isEnded() {
        Boolean ended = soundEngineAdapter.executeBlockingOnAudioThread(this::isEndedInternal);
        return ended != null && ended;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        if (!soundEngineAdapter.executeOnAudioThread(this::closeInternal)) {
            resetAudioStateInternal();
        }
    }

    private void tickInternal(Vec3 position, float currentVolume, float currentMaxDistance, float currentPitch, boolean shouldPlay) {
        if (!initInternal()) {
            return;
        }

        cleanupProcessedBuffersInternal();
        if (sourceId == 0) {
            return;
        }

        float masterVolume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);

        AL10.alSource3f(sourceId, AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
        AL10.alSourcef(sourceId, AL10.AL_GAIN, currentVolume * Configs.VOLUME_FACTOR * masterVolume);
        AL10.alSourcef(sourceId, AL10.AL_PITCH, currentPitch);
        AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, currentMaxDistance);
        AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 1.0F);
        AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0F);
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, relative ? AL10.AL_TRUE : AL10.AL_FALSE);
        if (checkError("update source state", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return;
        }

        if (!shouldPlay) {
            AL10.alSourcePause(sourceId);
            checkError("pause source", AlErrorHandling.INVALIDATE_AUDIO_STATE);
        } else {
            tryStartPlaybackInternal();
        }

        cachedPlaytime = queryPlaytimeInternal();
    }

    private void uploadInternal(int frameSampleRate, long frameTime, ByteBuffer pcm16Mono) {
        if (!initInternal()) {
            return;
        }

        if (sampleRate != frameSampleRate) {
            clearInternal();
            sampleRate = frameSampleRate;
        }

        cleanupProcessedBuffersInternal();
        if (sourceId == 0) {
            return;
        }

        Integer bufferId = availableBuffers.poll();
        if (bufferId == null) {
            return;
        }

        bufferTimestamp.put(bufferId, frameTime);
        AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, pcm16Mono, frameSampleRate);
        if (checkError("buffer data", AlErrorHandling.LOG_ONLY)) {
            bufferTimestamp.remove(bufferId);
            availableBuffers.offer(bufferId);
            return;
        }

        AL10.alSourceQueueBuffers(sourceId, new int[]{bufferId});
        if (checkError("queue buffer", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            bufferTimestamp.remove(bufferId);
            return;
        }

        if (playing) {
            tryStartPlaybackInternal();
        }
    }

    private void tryStartPlaybackInternal() {
        if (sourceId == 0) {
            return;
        }

        int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
        int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        if (checkError("query source state", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return;
        }

        if (queued > 0 && state != AL10.AL_PLAYING) {
            AL10.alSourcePlay(sourceId);
            checkError("play source", AlErrorHandling.INVALIDATE_AUDIO_STATE);
        }
    }

    private boolean initInternal() {
        if (closed) {
            return false;
        }

        if (!initSourceInternal()) {
            return false;
        }
        return initBuffersInternal();
    }

    private boolean initSourceInternal() {
        if (sourceId != 0 && validateSourceInternal()) {
            return true;
        }

        int[] idHolder = new int[1];
        AL10.alGenSources(idHolder);
        if (checkError("gen source", AlErrorHandling.LOG_ONLY)) {
            sourceId = 0;
            return false;
        }

        sourceId = idHolder[0];
        ACTIVE_SOURCES.put(sourceId, this);
        AL10.alSourcef(sourceId, AL10.AL_GAIN, 1.0F);
        AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, maxDistance);
        AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 1.0F);
        AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0F);
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alDistanceModel(AL10.AL_INVERSE_DISTANCE_CLAMPED);
        AL10.alSource3f(sourceId, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
        if (checkError("init source", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return false;
        }

        return true;
    }

    private boolean initBuffersInternal() {
        if (bufferIds != null) {
            return true;
        }

        int[] ids = new int[BUFFER_COUNT];
        AL10.alGenBuffers(ids);
        if (checkError("gen buffers", AlErrorHandling.LOG_ONLY)) {
            deleteBuffersInternal();
            return false;
        }

        bufferIds = ids;
        availableBuffers.clear();
        bufferTimestamp.clear();
        for (int id : ids) {
            availableBuffers.offer(id);
        }
        return true;
    }

    private boolean validateSourceInternal() {
        AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        if (checkError("validate source", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return false;
        }
        return true;
    }

    private void cleanupProcessedBuffersInternal() {
        if (sourceId == 0) {
            return;
        }

        int processed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
        if (checkError("query processed buffers", AlErrorHandling.INVALIDATE_AUDIO_STATE) || processed <= 0) {
            return;
        }

        int[] unqueuedIds = new int[processed];
        AL10.alSourceUnqueueBuffers(sourceId, unqueuedIds);
        if (checkError("unqueue processed buffers", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return;
        }

        long latestTimestamp = -1L;
        for (int bufferId : unqueuedIds) {
            availableBuffers.offer(bufferId);
            Long timestamp = bufferTimestamp.remove(bufferId);
            if (timestamp != null && timestamp > latestTimestamp) {
                latestTimestamp = timestamp;
            }
        }

        if (latestTimestamp >= 0L) {
            lastFrameTimestamp = latestTimestamp;
        }
    }

    private void clearInternal() {
        if (sourceId == 0) {
            resetRuntimeState();
            return;
        }

        AL10.alSourceStop(sourceId);
        if (checkError("stop source", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return;
        }

        int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
        if (checkError("query queued buffers", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return;
        }
        if (queued > 0) {
            int[] queuedIds = new int[queued];
            AL10.alSourceUnqueueBuffers(sourceId, queuedIds);
            if (checkError("unqueue queued buffers", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
                return;
            }
        }

        sampleRate = -1;
        lastFrameTimestamp = -1L;
        cachedPlaytime = -1L;

        availableBuffers.clear();
        bufferTimestamp.clear();
        if (bufferIds != null) {
            for (int id : bufferIds) {
                availableBuffers.offer(id);
            }
        }
    }

    private long queryPlaytimeInternal() {
        if (sourceId == 0 || sampleRate <= 0) {
            return -1L;
        }

        cleanupProcessedBuffersInternal();
        if (sourceId == 0 || lastFrameTimestamp < 0L) {
            return -1L;
        }

        float offsetSec = AL10.alGetSourcef(sourceId, AL11.AL_SEC_OFFSET);
        if (checkError("query sec offset", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return cachedPlaytime;
        }

        return lastFrameTimestamp + secondsToMicros(offsetSec);
    }

    static long secondsToMicros(double seconds) {
        return (long) (seconds * MICROS_PER_SECOND);
    }

    private boolean isEndedInternal() {
        if (sourceId == 0) {
            return false;
        }

        cleanupProcessedBuffersInternal();
        if (sourceId == 0) {
            return false;
        }

        int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
        if (checkError("query ended state", AlErrorHandling.INVALIDATE_AUDIO_STATE)) {
            return false;
        }
        return state != AL10.AL_PLAYING && queued == 0;
    }

    private void closeInternal() {
        if (sourceId != 0) {
            AL10.alSourceStop(sourceId);
            checkError("stop source on close", AlErrorHandling.LOG_ONLY);
        }

        deleteSourceInternal();
        deleteBuffersInternal();
        resetAudioStateInternal();
    }

    private void deleteSourceInternal() {
        if (sourceId == 0) {
            return;
        }

        ACTIVE_SOURCES.remove(sourceId);
        AL10.alDeleteSources(new int[]{sourceId});
        checkError("delete source", AlErrorHandling.LOG_ONLY);
        sourceId = 0;
    }

    private void deleteBuffersInternal() {
        var ids = bufferIds;
        if (ids == null) {
            return;
        }

        AL10.alDeleteBuffers(ids);
        checkError("delete buffers", AlErrorHandling.LOG_ONLY);
        bufferIds = null;
    }

    private void invalidateAudioState() {
        ACTIVE_SOURCES.remove(sourceId);
        resetAudioStateInternal();
    }

    private void resetAudioStateInternal() {
        sourceId = 0;
        bufferIds = null;
        resetRuntimeState();
    }

    private void resetRuntimeState() {
        availableBuffers.clear();
        bufferTimestamp.clear();
        sampleRate = -1;
        lastFrameTimestamp = -1L;
        cachedPlaytime = -1L;
    }

    static float sanitizeMaxDistance(float maxDistance) {
        return Math.max(0.0F, maxDistance);
    }

    private boolean checkError(String status, AlErrorHandling handling) {
        int err = AL10.alGetError();
        if (err == AL10.AL_NO_ERROR) {
            return false;
        }

        LOGGER.error(
                "Got OpenAL error at {}: {} (sourceId={}, hasBuffers={}, closed={}, playing={}, sampleRate={})",
                status,
                err,
                sourceId,
                bufferIds != null,
                closed,
                playing,
                sampleRate
        );

        if (handling == AlErrorHandling.INVALIDATE_AUDIO_STATE && err == AL10.AL_INVALID_NAME) {
            invalidateAudioState();
        }
        return true;
    }


    private static ByteBuffer toPcm16Mono(Buffer monoBuffer) {
        return switch (monoBuffer) {
            case ByteBuffer byteBuffer -> fromByteBuffer(byteBuffer);
            case ShortBuffer shortBuffer -> fromShortBuffer(shortBuffer);
            case IntBuffer intBuffer -> fromIntBuffer(intBuffer);
            case FloatBuffer floatBuffer -> fromFloatBuffer(floatBuffer);
            default -> throw new IllegalArgumentException("Unsupported audio buffer type: " + monoBuffer.getClass());
        };
    }

    private static ByteBuffer fromByteBuffer(ByteBuffer source) {
        var input = source.duplicate();
        var output = BufferUtils.createByteBuffer(input.remaining() * 2);
        while (input.hasRemaining()) {
            output.putShort((short) (input.get() << 8));
        }
        output.flip();
        return output;
    }

    private static ByteBuffer fromShortBuffer(ShortBuffer source) {
        var input = source.duplicate();
        var output = BufferUtils.createByteBuffer(input.remaining() * 2);
        while (input.hasRemaining()) {
            output.putShort(input.get());
        }
        output.flip();
        return output;
    }

    private static ByteBuffer fromIntBuffer(IntBuffer source) {
        var input = source.duplicate();
        var output = BufferUtils.createByteBuffer(input.remaining() * 2);
        while (input.hasRemaining()) {
            int sample = input.get();
            output.putShort((short) (sample >> 16));
        }
        output.flip();
        return output;
    }

    private static ByteBuffer fromFloatBuffer(FloatBuffer source) {
        var input = source.duplicate();
        var output = BufferUtils.createByteBuffer(input.remaining() * 2);
        while (input.hasRemaining()) {
            float sample = Mth.clamp(input.get(), -1.0F, 1.0F);
            output.putShort((short) (sample * 32767.0F));
        }
        output.flip();
        return output;
    }
}

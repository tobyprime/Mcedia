package top.tobyprime.mcedia.core;

import top.tobyprime.mcedia.internal.AudioFrame;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class AudioSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioSource.class);
    private static final int BUFFER_COUNT = 8; // 预分配的缓冲区数量
    private Consumer<Runnable> alThreadExecutor;


    private final Queue<Integer> availableBuffers = new ConcurrentLinkedQueue<>();
//    private final SoundEngineExecutor executor;

    public boolean requireInit = true;
    private int[] alBuffers = null;
    private volatile int alSource = -1;
    private float volume = 1.0f;
    private float maxDistance = 32.0f;
    private double posX, posY, posZ;
    private boolean isClosed = false;
    private final Object alLock = new Object();

    public AudioSource(Consumer<Runnable> alThreadExecutor) {
        this.alThreadExecutor = alThreadExecutor;
    }

    private void alInit() {
        if (!requireInit || isClosed) return;
        try {
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.warn("初始化失败: {}", error);
            }
            alBuffers = new int[BUFFER_COUNT];
            AL10.alGenBuffers(alBuffers);
            error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.error("生成 buffers 失败: {}", error);
                return;
            }
            for (int bufferId : alBuffers) {
                availableBuffers.offer(bufferId);
            }
            alSource = AL10.alGenSources();
            error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.error("创建 Source 失败: {}", error);
                return;
            }
            if (alSource == 0) {
                LOGGER.error("创建的 Source 无效: {}", alSource);
                return;
            }
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
            AL10.alSourcef(alSource, AL10.AL_MAX_DISTANCE, maxDistance);
            AL10.alSourcef(alSource, AL10.AL_REFERENCE_DISTANCE, 1.0f);
            AL10.alSourcef(alSource, AL10.AL_ROLLOFF_FACTOR, 1.0f);
            AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSourcei(alSource, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alDistanceModel(AL10.AL_INVERSE_DISTANCE_CLAMPED);
            AL10.alSource3f(alSource, AL10.AL_POSITION, (float) posX, (float) posY, (float) posZ);
            error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.error("配置属性失败: {}", error);
                AL10.alDeleteSources(alSource);
                alSource = -1;
                return;
            }
            requireInit = false;
            LOGGER.info("OpenAL 音频初始化完成: {}", alSource);
        } catch (Exception e) {
            LOGGER.error("OpenAL 音频初始化异常: ", e);
            if (alSource != -1) {
                try { AL10.alDeleteSources(alSource); } catch (Exception ignored) {}
                alSource = -1;
            }
        }
    }

    private void alCleanupBuffers() {
        if (alSource == -1) return;
        int processedCount = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
        int queuedCount = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);

        if (processedCount > 0 && queuedCount > 0 && (state == AL10.AL_PLAYING || state == AL10.AL_PAUSED)) {
            for (int i = 0; i < processedCount && queuedCount > 0; i++) {
                try {
                    int bufferId = AL10.alSourceUnqueueBuffers(alSource);
                    int error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        LOGGER.error("buffer 回收失败: {}", error);
                        break;
                    }
                    if (bufferId > 0) {
                        availableBuffers.offer(bufferId);
                    }
                    queuedCount--;
                } catch (Exception e) {
                    LOGGER.error("buffer 回收异常", e);
                    break;
                }
            }
        }
    }
    private void alRestartIfNeed(){
        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        if (state == AL10.AL_STOPPED) {
            AL10.alSourcePlay(alSource);
        }
    }
    private void alUpload(AudioFrame frame) {
        synchronized (alLock) {
            if (isClosed) return;
            alInitIfNeed();
            alRestartIfNeed();
            alCleanupBuffers();
            Integer bufferId = availableBuffers.poll();
            if (bufferId == null) {
                LOGGER.warn("buffer 不足");
                return;
            }
            int format = frame.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            AL10.alBufferData(bufferId, format, frame.pcm, frame.sampleRate);
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.error("上传失败: {}", error);
                availableBuffers.offer(bufferId);
                return;
            }
            AL10.alSourceQueueBuffers(alSource, bufferId);
            error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.error("buffer 加入队列失败: {}", error);
                availableBuffers.offer(bufferId);
                return;
            }
            int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
                AL10.alSourcePlay(alSource);
            }
        }
    }

    private void alSetPos(float x, float y, float z) {
        alInitIfNeed();

        this.posX = x;
        this.posY = y;
        this.posZ = z;
        try {
            AL10.alSource3f(alSource, AL10.AL_POSITION, x, y, z);
        } catch (Exception e) {
            LOGGER.error("设置位置异常", e);
        }
    }

    private void alInitIfNeed() {
        if (requireInit) {
            alInit();
            if (alSource == -1) {
                throw new IllegalStateException("OpenAL source not initialized");
            }
        }
    }

    private void alSetVolume(float volume) {
        alInitIfNeed();

        this.volume = volume;
        try {
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
        } catch (Exception e) {
            LOGGER.error("设置音量异常", e);
        }
    }

    private void alSetMaxDistance(float maxDistance) {
        alInitIfNeed();
        this.maxDistance = maxDistance;
        try {
            AL10.alSourcef(alSource, AL10.AL_MAX_DISTANCE, maxDistance);
        } catch (Exception e) {
            LOGGER.error("设置最大距离失败", e);
        }
    }

    public void uploadAudioFrame(AudioFrame audioFrame) {
        if (isClosed) return;

        try {
            alThreadExecutor.accept(() -> alUpload(audioFrame));
        } catch (Exception e) {
            LOGGER.error("Failed to schedule audio frame upload", e);
        }
    }

    public void setPos(float x, float y, float z) {

        alThreadExecutor.accept(() -> alSetPos(x, y, z));
    }

    public void setVolume(float volume) {
        alThreadExecutor.accept(() -> alSetVolume(volume));
    }

    public void setMaxDistance(float maxDistance) {
        alThreadExecutor.accept(() -> alSetMaxDistance(maxDistance));
    }

    public void alClose() {
        synchronized (alLock) {
            if (isClosed) return;

            isClosed = true;

            if (!requireInit && alSource != -1) {
                try {
                    // 停止播放
                    AL10.alSourceStop(alSource);

                    // 清理所有缓冲区
                    alCleanupBuffers();

                    // 清空队列
                    availableBuffers.clear();

                    // 删除音频源
                    AL10.alDeleteSources(alSource);
                    alSource = -1;

                    requireInit = true;
                    LOGGER.info("OpenAL audio source closed.");

                } catch (Exception e) {
                    LOGGER.error("Exception during OpenAL cleanup", e);
                }
            }
        }
    }

    public void close() throws IOException {
        synchronized (alLock) {
            alThreadExecutor.accept(this::alClose);
        }
    }
}

package top.tobyprime.mcedia.core;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.decoders.AudioBufferData;
import top.tobyprime.mcedia.interfaces.IAudioSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class AudioSource implements IAudioSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioSource.class);
    private static final int BUFFER_COUNT = 4; // 预分配的缓冲区数量
    private final Consumer<Runnable> alThreadExecutor;

    private final Queue<Integer> availableBuffers = new ConcurrentLinkedQueue<>();
    private final Object alLock = new Object();
    public boolean requireInit = true;
    private int[] alBuffers = null;
    private volatile int alSource = -1;
    private boolean isClosed = false;

    public AudioSource(Consumer<Runnable> alThreadExecutor) {
        this.alThreadExecutor = alThreadExecutor;
    }

    private void alInit() {
        if (!requireInit || isClosed) return;
        try {
            alCleanAll();
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
            AL10.alSourcef(alSource, AL10.AL_GAIN, 1);
            AL10.alSourcef(alSource, AL10.AL_MAX_DISTANCE, 500);
            AL10.alSourcef(alSource, AL10.AL_REFERENCE_DISTANCE, 1.0f);
            AL10.alSourcef(alSource, AL10.AL_ROLLOFF_FACTOR, 1.0f);
            AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSourcei(alSource, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alDistanceModel(AL10.AL_INVERSE_DISTANCE_CLAMPED);
            AL10.alSource3f(alSource, AL10.AL_POSITION, 0, 0, 0);

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
                try {
                    AL10.alDeleteSources(alSource);
                } catch (Exception ignored) {
                }
                alSource = -1;
            }
        }
    }

    private void alCleanAll() {
        synchronized (alLock) {
            try {
                // 停止播放
                if (alSource != -1) {
                    AL10.alSourceStop(alSource);

                    // 回收所有队列中的 buffer
                    int queuedCount = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
                    while (queuedCount-- > 0) {
                        int bufferId = AL10.alSourceUnqueueBuffers(alSource);
                        if (bufferId > 0) {
                            availableBuffers.offer(bufferId);
                        }
                    }

                    // 删除 Source
                    AL10.alDeleteSources(alSource);
                    alSource = -1;
                }

                // 删除所有 Buffer
                if (alBuffers != null) {
                    for (int bufferId : alBuffers) {
                        try {
                            AL10.alDeleteBuffers(bufferId);
                        } catch (Exception e) {
                            LOGGER.warn("删除 buffer {} 失败", bufferId, e);
                        }
                    }
                    alBuffers = null;
                }

                // 清空缓冲池
                availableBuffers.clear();

                // 重置状态
                requireInit = true;

                LOGGER.info("OpenAL 所有资源已清理，等待重新初始化。");
            } catch (Exception e) {
                LOGGER.error("alCleanAll 清理 OpenAL 资源时出错", e);
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

    private void alRestartIfNeed() {
        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        if (state == AL10.AL_STOPPED) {
            AL10.alSourcePlay(alSource);
        }
    }

    private void alUpload(AudioBufferData bufferData) {
        synchronized (alLock) {
            if (isClosed) return;
            alInitIfNeed();
            alRestartIfNeed();
            alCleanupBuffers();
            Integer bufferId = availableBuffers.poll();
            if (bufferId == null) {
                return;
            }

            switch (bufferData.pcm) {
                case ByteBuffer bb -> AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, bb, bufferData.sampleRate);
                case ShortBuffer sb -> AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, sb, bufferData.sampleRate);
                case FloatBuffer fb -> AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, fb, bufferData.sampleRate);
                case null -> throw new NullPointerException("Buffer is null");
                default -> throw new IllegalArgumentException("Unsupported Buffer type: " + bufferData.pcm.getClass());
            }
            bufferData.close();
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.error("上传失败: {}", error);
                availableBuffers.offer(bufferId);
                requireInit = true;
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
        if (volume < 0) {
            volume = 0;
        }
        try {
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
        } catch (Exception e) {
            LOGGER.error("设置音量异常", e);
        }
    }

    private void alSetMaxDistance(float maxDistance) {
        alInitIfNeed();
        if (maxDistance < 0) {
            maxDistance = 0;
        }
        try {
            AL10.alSourcef(alSource, AL10.AL_MAX_DISTANCE, maxDistance);
        } catch (Exception e) {
            LOGGER.error("设置最大距离失败", e);
        }
    }

    private void alSetMinDistance(float minDistance) {
        alInitIfNeed();
        if (minDistance < 0) {
            minDistance = 0;
        }
        try {
            AL10.alSourcef(alSource, AL10.AL_REFERENCE_DISTANCE, minDistance);
        } catch (Exception e) {
            LOGGER.error("设置最大距离失败", e);
        }
    }

    public void upload(@Nullable AudioBufferData audioFrame) {
        if (isClosed) return;
        if (audioFrame == null) {
            return;
        }

        try {
            alThreadExecutor.accept(() -> alUpload(audioFrame));
        } catch (Exception e) {
            LOGGER.error("Failed to schedule audio frame upload", e);
        }
    }

    public void alSetPitch(float pitch) {
        alInitIfNeed();
        if (pitch < 0) {
            pitch = 0;
        }
        try {
            AL10.alSourcef(alSource, AL10.AL_PITCH, pitch);
        } catch (Exception e) {
            LOGGER.error("设置最大距离失败", e);
        }
    }

    @Override
    public void setPitch(float pitch) {
        alThreadExecutor.accept(() -> alSetPitch(pitch));
    }

    @Override
    public void clearBuffer() {
        alThreadExecutor.accept(() -> {
            synchronized (alLock) {
                if (isClosed || requireInit || alSource == -1) return;

                try {
                    // 停止播放
                    AL10.alSourceStop(alSource);

                    // 查询已排队的缓冲数量
                    int queuedCount = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
                    while (queuedCount-- > 0) {
                        int bufferId = AL10.alSourceUnqueueBuffers(alSource);
                        int error = AL10.alGetError();
                        if (error != AL10.AL_NO_ERROR) {
                            LOGGER.error("clearBuffer: unqueue 失败: {}", error);
                            break;
                        }
                        if (bufferId > 0) {
                            availableBuffers.offer(bufferId);
                        }
                    }

                    LOGGER.info("清空音频缓冲完成，缓冲池大小: {}", availableBuffers.size());

                } catch (Exception e) {
                    LOGGER.error("clearBuffer 异常", e);
                }
            }
        });
    }

    public void setPos(float x, float y, float z) {

        alThreadExecutor.accept(() -> alSetPos(x, y, z));
    }

    public void setVolume(float volume) {
        alThreadExecutor.accept(() -> alSetVolume(volume));
    }

    public void setRange(float min, float max) {
        alThreadExecutor.accept(() -> {
            alSetMinDistance(min);
            alSetMaxDistance(Math.max(max, min));
        });
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

package top.tobyprime.mcedia.player.internal.processors;

import org.jetbrains.annotations.Nullable;
import top.tobyprime.mcedia.api.stream.FrameStream;
import top.tobyprime.mcedia.api.video.MediaTexture;
import top.tobyprime.mcedia.api.video.VideoFrame;
import top.tobyprime.mcedia.player.runtime.McediaExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class VideoProcessor {
    /** 呈现提前量：帧时间戳略微超前于显示时间，补偿解码/上传管线延迟，避免画面落后于音频。 */
    private static final long PRESENTATION_LEAD_US = 12_000;
    @Nullable MediaTexture texture;
    @Nullable
    private FrameStream<VideoFrame> stream;
    private final Executor closeExecutor;
    private final AtomicBoolean uploadInFlight = new AtomicBoolean(false);
    private final AtomicReference<VideoFrame> pendingUploadFrame = new AtomicReference<>();
    private long lastClockTimeUs = Long.MIN_VALUE;

    public VideoProcessor() {
        this(McediaExecutors.ioExecutor);
    }

    VideoProcessor(Executor closeExecutor) {
        this.closeExecutor = closeExecutor;
    }

    public void bindTexture(@Nullable MediaTexture texture) {
        this.texture = texture;
        if (texture == null) {
            closePendingUploadFrame();
        }
    }

    public void bindStream(@Nullable FrameStream<VideoFrame> stream) {
        this.stream = stream;
        closePendingUploadFrame();
        lastClockTimeUs = Long.MIN_VALUE;
    }

    public void tick(long time) {
        if (texture != null) {
            texture.tick(time);
        }
        if (stream == null) {
            return;
        }

        VideoFrame selectedFrame = pollBestFrameForPresentation(stream, presentationTargetTime(time));
        lastClockTimeUs = time;
        if (selectedFrame == null) {
            return;
        }

        var tex = texture;
        if (tex == null) {
            closeExecutor.execute(selectedFrame::close);
            return;
        }

        replacePendingUploadFrame(selectedFrame);
        scheduleUploadIfIdle();
    }

    /** 目标呈现时刻：首帧按当前时刻，之后在时钟基础上加提前量。 */
    private long presentationTargetTime(long time) {
        if (lastClockTimeUs == Long.MIN_VALUE || time <= lastClockTimeUs) {
            return time;
        }
        return time + PRESENTATION_LEAD_US;
    }

    /** 消费所有不晚于目标时刻的帧，只保留最新一帧（其余直接关闭）。 */
    private @Nullable VideoFrame pollBestFrameForPresentation(FrameStream<VideoFrame> currentStream, long targetTime) {
        VideoFrame selected = null;
        VideoFrame frame;
        while ((frame = currentStream.poll(candidate -> candidate.getTime() <= targetTime)) != null) {
            if (selected != null) {
                closeExecutor.execute(selected::close);
            }
            selected = frame;
        }
        return selected;
    }

    /** 以最新帧替换待上传帧，被替换的旧帧立即关闭，避免上传积压。 */
    private void replacePendingUploadFrame(VideoFrame frame) {
        VideoFrame previous = pendingUploadFrame.getAndSet(frame);
        if (previous != null) {
            closeExecutor.execute(previous::close);
        }
    }

    private void closePendingUploadFrame() {
        VideoFrame pending = pendingUploadFrame.getAndSet(null);
        if (pending != null) {
            closeExecutor.execute(pending::close);
        }
    }

    /** 同一时刻至多一个上传任务；上传期间到达的新帧留作 pending，结束后再调度。 */
    private void scheduleUploadIfIdle() {
        if (!uploadInFlight.compareAndSet(false, true)) {
            return;
        }
        VideoFrame frame = pendingUploadFrame.getAndSet(null);
        if (frame == null) {
            uploadInFlight.set(false);
            if (pendingUploadFrame.get() != null) {
                scheduleUploadIfIdle();
            }
            return;
        }
        closeExecutor.execute(() -> {
            try {
                var tex = texture;
                if (tex != null) {
                    tex.upload(frame);
                }
            } finally {
                try {
                    frame.close();
                } finally {
                    uploadInFlight.set(false);
                    if (pendingUploadFrame.get() != null) {
                        scheduleUploadIfIdle();
                    }
                }
            }
        });
    }
}

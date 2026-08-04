package top.tobyprime.mcedia_core.client.renderer;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.decoder.metrics.DecoderMetrics;
import top.tobyprime.mcedia.api.video.MediaTexture;
import top.tobyprime.mcedia.api.video.VideoFrame;
import top.tobyprime.mcedia.player.config.Configs;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MediaTextureImpl extends AbstractTexture implements MediaTexture {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaTextureImpl.class);
    private static final AtomicLong COUNTER = new AtomicLong(0);

    private final Object pendingLock = new Object();
    private final Identifier textureId;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean uploadScheduled = new AtomicBoolean(false);

    private GpuTexture textureA;
    private GpuTexture textureB;
    private int writeIndex;
    private volatile int currentWidth;
    private volatile int currentHeight;

    private TextureUploader uploader;

    private ByteBuffer pendingBuffer;
    private int pendingWidth;
    private int pendingHeight;

    private volatile boolean uploadEnabled = true;
    private volatile boolean uploadThrottled;
    private long lastUploadTime;

    public MediaTextureImpl() {
        this(Identifier.fromNamespaceAndPath("mcedia_core", "dynamic/media_texture_" + COUNTER.incrementAndGet()));
    }

    public MediaTextureImpl(Identifier textureId) {
        this.textureId = textureId;
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public int getTextureWidth() {
        return currentWidth;
    }

    public int getTextureHeight() {
        return currentHeight;
    }

    public void setUploadEnabled(boolean enabled) {
        this.uploadEnabled = enabled;
    }

    public void setUploadThrottled(boolean throttled) {
        this.uploadThrottled = throttled;
    }

    @Override
    public void upload(VideoFrame frame) {
        if (closed.get() || !uploadEnabled) {
            return;
        }

        if (uploadThrottled) {
            int uploadFps = Math.max(1, Configs.LOW_OVERHEAD_UPLOAD_FPS);
            long minUploadIntervalMillis = Math.max(1L, 1000L / uploadFps);
            long now = System.currentTimeMillis();
            if (now - lastUploadTime < minUploadIntervalMillis) {
                return;
            }
            lastUploadTime = now;
        }

        int width = frame.getWidth();
        int height = frame.getHeight();
        if (width <= 0 || height <= 0) {
            LOGGER.warn("Ignore invalid video frame size {}x{} for {}", width, height, textureId);
            return;
        }

        long expectedBytesLong = (long) width * height * 4L;
        if (expectedBytesLong > Integer.MAX_VALUE) {
            LOGGER.warn("Ignore oversized video frame {}x{} for {}", width, height, textureId);
            return;
        }
        int expectedBytes = (int) expectedBytesLong;

        ByteBuffer source = frame.getBuffer();
        if (source.remaining() < expectedBytes) {
            LOGGER.warn("Ignore short video frame buffer for {}, remaining={}, expected={}", textureId, source.remaining(), expectedBytes);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread() && source.isDirect()) {
            flushDirect(width, height, source);
            return;
        }

        synchronized (pendingLock) {
            if (closed.get()) {
                return;
            }
            if (pendingBuffer == null || pendingBuffer.capacity() < expectedBytes) {
                if (pendingBuffer != null) {
                    MemoryUtil.memFree(pendingBuffer);
                }
                pendingBuffer = MemoryUtil.memAlloc(expectedBytes);
            }
            pendingBuffer.clear();
            int oldLimit = source.limit();
            source.limit(source.position() + expectedBytes);
            pendingBuffer.put(source);
            source.limit(oldLimit);
            pendingBuffer.flip();
            pendingWidth = width;
            pendingHeight = height;
        }

        requestFlush();
    }

    @Override
    public void tick(long time) {
        if (closed.get()) {
            return;
        }
        requestFlush();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Runnable releaseTask = () -> {
            synchronized (pendingLock) {
                if (pendingBuffer != null) {
                    MemoryUtil.memFree(pendingBuffer);
                    pendingBuffer = null;
                }
                pendingWidth = 0;
                pendingHeight = 0;
            }
            closeTextures();
            minecraft.getTextureManager().release(textureId);
        };
        if (minecraft.isSameThread()) {
            releaseTask.run();
        } else {
            minecraft.execute(releaseTask);
        }
    }

    private void requestFlush() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.isSameThread()) {
            flushPending();
            return;
        }

        if (uploadScheduled.compareAndSet(false, true)) {
            minecraft.execute(() -> {
                uploadScheduled.set(false);
                flushPending();
            });
        }
    }

    private void flushPending() {
        if (closed.get()) {
            return;
        }

        ByteBuffer buffer;
        int width;
        int height;
        synchronized (pendingLock) {
            if (pendingWidth == 0) {
                return;
            }
            buffer = pendingBuffer;
            width = pendingWidth;
            height = pendingHeight;
            pendingWidth = 0;
            pendingHeight = 0;
        }

        flushDirect(width, height, buffer);
    }

    private void flushDirect(int width, int height, ByteBuffer directBuffer) {
        ensureTextures(width, height);
        if (textureA == null || textureB == null) {
            return;
        }

        GpuTexture writeTex = (writeIndex == 0) ? textureA : textureB;

        if (writeTex instanceof GlTexture glTex && "OpenGL".equals(RenderSystem.getDevice().getBackendName())) {
            if (uploader == null) {
                uploader = new GlTextureUploader();
            }
            uploader.upload(glTex.glId(), width, height, directBuffer, writeIndex);
        } else {
            RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(writeTex, directBuffer, NativeImage.Format.RGBA, 0, 0, 0, 0, width, height);
        }
        DecoderMetrics.tracker().onVideoFrameUploaded();

        writeIndex ^= 1;
        this.texture = writeTex;
        this.textureView = RenderSystem.getDevice().createTextureView(writeTex);
    }

    private void ensureTextures(int width, int height) {
        if (currentWidth == width && currentHeight == height && textureA != null) {
            return;
        }

        closeTextures();

        var device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;

        textureA = device.createTexture(() -> "mcedia:" + textureId + ":a", usage, TextureFormat.RGBA8, width, height, 1, 1);
        textureB = device.createTexture(() -> "mcedia:" + textureId + ":b", usage, TextureFormat.RGBA8, width, height, 1, 1);

        currentWidth = width;
        currentHeight = height;
        writeIndex = 0;

        this.texture = textureB;
        this.textureView = device.createTextureView(textureB);

        Minecraft.getInstance().getTextureManager().register(textureId, this);
    }

    private void closeTextures() {
        if (uploader != null) {
            uploader.close();
            uploader = null;
        }
        if (textureA != null) {
            textureA.close();
            textureA = null;
        }
        if (textureB != null) {
            textureB.close();
            textureB = null;
        }
        currentWidth = 0;
        currentHeight = 0;
        this.texture = null;
    }
}
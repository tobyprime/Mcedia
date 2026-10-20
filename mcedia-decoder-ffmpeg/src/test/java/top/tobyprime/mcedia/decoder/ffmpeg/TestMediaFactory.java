package top.tobyprime.mcedia.decoder.ffmpeg;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ShortBuffer;
import java.nio.file.Path;

final class TestMediaFactory {
    static final int VIDEO_WIDTH = 32;
    static final int VIDEO_HEIGHT = 32;
    static final int VIDEO_FRAME_COUNT = 6;
    static final int VIDEO_FRAME_RATE = 6;
    static final int AUDIO_SAMPLE_RATE = 44_100;
    static final int AUDIO_CHANNELS = 1;
    static final double AUDIO_DURATION_SECONDS = 0.35D;

    private TestMediaFactory() {
    }

    static MediaFixture createVideoWithSeparateAudio(Path tempDir) throws Exception {
        Path videoPath = tempDir.resolve("sample-video.mp4");
        Path audioPath = tempDir.resolve("sample-audio.wav");
        writeVideo(videoPath);
        writeAudio(audioPath);
        return new MediaFixture(videoPath, audioPath);
    }

    static Path createVideoOnly(Path tempDir) throws Exception {
        Path videoPath = tempDir.resolve("sample-video-only.mp4");
        writeVideo(videoPath);
        return videoPath;
    }

    static Path createH264VideoOnly(Path tempDir) throws Exception {
        Path videoPath = tempDir.resolve("sample-h264-video-only.mp4");
        writeH264Video(videoPath);
        return videoPath;
    }

    private static void writeVideo(Path outputPath) throws Exception {
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath.toFile(), VIDEO_WIDTH, VIDEO_HEIGHT, 0)) {
            recorder.setFormat("mp4");
            recorder.setFrameRate(VIDEO_FRAME_RATE);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();
            for (int i = 0; i < VIDEO_FRAME_COUNT; i++) {
                recorder.record(converter.convert(createFrameImage(i)));
            }
        }
    }

    private static void writeH264Video(Path outputPath) throws Exception {
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath.toFile(), VIDEO_WIDTH, VIDEO_HEIGHT, 0)) {
            recorder.setFormat("mp4");
            recorder.setFrameRate(VIDEO_FRAME_RATE);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setVideoOption("strict", "-2");
            recorder.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();
            for (int i = 0; i < VIDEO_FRAME_COUNT; i++) {
                recorder.record(converter.convert(createFrameImage(i)));
            }
        }
    }

    private static void writeAudio(Path outputPath) throws Exception {
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath.toFile(), 0, 0, AUDIO_CHANNELS)) {
            recorder.setFormat("wav");
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_PCM_S16LE);
            recorder.setSampleRate(AUDIO_SAMPLE_RATE);
            recorder.setAudioChannels(AUDIO_CHANNELS);
            recorder.start();

            int sampleCount = (int) (AUDIO_SAMPLE_RATE * AUDIO_DURATION_SECONDS);
            short[] samples = new short[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                double angle = 2.0D * Math.PI * 440.0D * i / AUDIO_SAMPLE_RATE;
                samples[i] = (short) (Math.sin(angle) * Short.MAX_VALUE * 0.2D);
            }
            recorder.recordSamples(AUDIO_SAMPLE_RATE, AUDIO_CHANNELS, ShortBuffer.wrap(samples));
        }
    }

    private static BufferedImage createFrameImage(int index) {
        BufferedImage image = new BufferedImage(VIDEO_WIDTH, VIDEO_HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color((index * 40) % 255, (index * 70) % 255, (index * 110) % 255));
            graphics.fillRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(index, index, 8, 8);
            graphics.setColor(Color.BLACK);
            graphics.drawLine(0, VIDEO_HEIGHT - 1 - index, VIDEO_WIDTH - 1, index);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    record MediaFixture(Path videoPath, Path audioPath) {
    }
}

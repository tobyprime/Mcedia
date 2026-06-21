package top.tobyprime.mcedia_core.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.config.DecoderConfiguration;
import top.tobyprime.mcedia.api.resolver.MediaResolverSettings;
import top.tobyprime.mcedia.api.resolver.MediaResolvers;
import top.tobyprime.mcedia.player.config.Configs;
import top.tobyprime.mcedia.player.core.SingleMediaPlayer;
import top.tobyprime.mcedia_core.client.audio.SpeakerAudioChannelMode;
import top.tobyprime.mcedia_core.client.player.HudScreenPeripheral;
import top.tobyprime.mcedia_core.client.player.HudSpeakerPeripheral;
import top.tobyprime.mcedia_core.client.player.MediaPlayerHostManager;
import top.tobyprime.mcedia_core.client.player.ScreenPeripheral;
import top.tobyprime.mcedia_core.client.player.SpeakerPeripheral;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ClientCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCommands.class);
    private static final float SCREEN_DIAGONAL_INCHES = 120.0F;
    private static final float SCREEN_ASPECT_WIDTH = 16.0F;
    private static final float SCREEN_ASPECT_HEIGHT = 9.0F;
    private static final float SCREEN_SCALE = (float) (SCREEN_DIAGONAL_INCHES * 0.0254F
            / Math.sqrt(SCREEN_ASPECT_WIDTH * SCREEN_ASPECT_WIDTH + SCREEN_ASPECT_HEIGHT * SCREEN_ASPECT_HEIGHT));
    private static final float SCREEN_WIDTH = SCREEN_SCALE * SCREEN_ASPECT_WIDTH;
    private static final float SCREEN_HEIGHT = SCREEN_SCALE * SCREEN_ASPECT_HEIGHT;

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RESOLUTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(java.util.List.of("320p", "720p", "1080p", "2k", "4k", "8k", "unlimited"), builder);
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_HOST_IDS = (context, builder) -> {
        var hostEntries = MediaPlayerHostManager.get().getHostsByIdSnapshot();
        var suggestions = new ArrayList<String>();
        for (var hostId : hostEntries.keySet()) {
            suggestions.add(Integer.toString(hostId));
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

    private ClientCommands() {
    }

    public static void register() {
        // Called from FabricEntryPointClient, but commands are now registered via MixinCommands mixin
        // to avoid dependency on Fabric client command API. This entry point remains for lifecycle logging.
        LOGGER.info("Mcedia commands registered via MixinCommands");
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("mcedia");

        root.then(Commands.literal("dev")
                .then(Commands.literal("create")
                        .executes(context -> createHostWithPeripherals(context.getSource())))
                .then(Commands.literal("createHud")
                        .executes(context -> createHudHost(context.getSource()))));

        root.then(Commands.literal("config")
                .executes(context -> showConfig(context.getSource()))
                .then(Commands.literal("resolution")
                        .then(Commands.argument("level", StringArgumentType.word())
                                .suggests(SUGGEST_RESOLUTIONS)
                                .executes(context -> setResolution(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "level")))))
                .then(Commands.literal("activedecoderlimit")
                        .executes(context -> showActiveDecoderLimit(context.getSource()))
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(context -> setActiveDecoderLimit(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("throttleddecoderlimit")
                        .executes(context -> showThrottledDecoderLimit(context.getSource()))
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(context -> setThrottledDecoderLimit(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("lowoverheadfps")
                        .executes(context -> showLowOverheadFPS(context.getSource()))
                        .then(Commands.argument("fps", IntegerArgumentType.integer(1))
                                .executes(context -> setLowOverheadFPS(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "fps")))))
                .then(Commands.literal("danmaku")
                        .executes(context -> showDanmakuVisible(context.getSource()))
                        .then(Commands.argument("visible", BoolArgumentType.bool())
                                .executes(context -> setDanmakuVisible(
                                        context.getSource(),
                                        BoolArgumentType.getBool(context, "visible")))))
                .then(Commands.literal("volume")
                        .executes(context -> showVolume(context.getSource()))
                        .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.0D, 4.0D))
                                .executes(context -> setVolume(
                                        context.getSource(),
                                        DoubleArgumentType.getDouble(context, "factor"))))));

        root.then(Commands.literal("host")
                .then(Commands.literal("create")
                        .executes(context -> createHost(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> listHosts(context.getSource())))
                .then(Commands.literal("seturl")
                        .then(Commands.argument("hostId", IntegerArgumentType.integer(1))
                                .suggests(SUGGEST_HOST_IDS)
                                .then(Commands.argument("mediaUrl", StringArgumentType.greedyString())
                                        .executes(context -> setHostUrl(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "hostId"),
                                                StringArgumentType.getString(context, "mediaUrl"))))))
                .then(Commands.literal("forward")
                        .then(Commands.argument("hostId", IntegerArgumentType.integer(1))
                                .suggests(SUGGEST_HOST_IDS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> seekHostRelative(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "hostId"),
                                                IntegerArgumentType.getInteger(context, "seconds"),
                                                true)))))
                .then(Commands.literal("backward")
                        .then(Commands.argument("hostId", IntegerArgumentType.integer(1))
                                .suggests(SUGGEST_HOST_IDS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> seekHostRelative(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "hostId"),
                                                IntegerArgumentType.getInteger(context, "seconds"),
                                                false)))))
                .then(Commands.literal("setspeed")
                        .then(Commands.argument("hostId", IntegerArgumentType.integer(1))
                                .suggests(SUGGEST_HOST_IDS)
                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.1D, 8.0D))
                                        .executes(context -> setHostSpeed(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "hostId"),
                                                DoubleArgumentType.getDouble(context, "speed"))))))
                .then(Commands.literal("pause")
                        .then(Commands.argument("hostId", IntegerArgumentType.integer(1))
                                .suggests(SUGGEST_HOST_IDS)
                                .executes(context -> setHostPaused(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "hostId"),
                                        true))
                                .then(Commands.argument("paused", BoolArgumentType.bool())
                                        .executes(context -> setHostPaused(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "hostId"),
                                                BoolArgumentType.getBool(context, "paused")))))));

        dispatcher.register(root);
    }

    private static int createHost(CommandSourceStack source) {
        var client = Minecraft.getInstance();
        var decoderH = decoderMaxHeight(Configs.MAX_RESOLUTION_HEIGHT);
        var handle = MediaPlayerHostManager.get().createHostAndGetId(new DecoderConfiguration.Builder()
                .maxVideoSize(decoderMaxWidth(decoderH), decoderH)
                .build());
        source.sendSuccess(() -> Component.literal("Created host " + handle.hostId()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int createHudHost(CommandSourceStack source) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            var window = client.getWindow();
            int sw = window.getGuiScaledWidth();
            int sh = window.getGuiScaledHeight();

            var hudScreen = new HudScreenPeripheral();
            hudScreen.setScreenSize(sw / 3, sh / 3);
            hudScreen.setPosition(sw - sw / 3, 0);

            var hudSpeakerLeft = new HudSpeakerPeripheral();
            hudSpeakerLeft.setAudioChannelMode(SpeakerAudioChannelMode.LEFT);

            var hudSpeakerRight = new HudSpeakerPeripheral();
            hudSpeakerRight.setAudioChannelMode(SpeakerAudioChannelMode.RIGHT);

            var manager = MediaPlayerHostManager.get();
            var decoderH = decoderMaxHeight(Configs.MAX_RESOLUTION_HEIGHT);
            var handle = manager.createHostAndGetId(new DecoderConfiguration.Builder()
                    .maxVideoSize(decoderMaxWidth(decoderH), decoderH)
                    .build());
            manager.assignPeripheralToHost(handle.hostId(), hudScreen);
            manager.assignPeripheralToHost(handle.hostId(), hudSpeakerLeft);
            manager.assignPeripheralToHost(handle.hostId(), hudSpeakerRight);

            source.sendSuccess(() -> Component.literal(
                    "Created host " + handle.hostId() + " with HUD screen and stereo speakers"), false);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int createHostWithPeripherals(CommandSourceStack source) {
        var client = Minecraft.getInstance();
        var player = client.player;
        var level = client.level;  // 使用客户端世界，以便 ScreenPeripheral.isAlive() 正确匹配
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Client player or level not ready"));
            return 0;
        }
        var look = player.getLookAngle();
        var horizontalLook = new Vec3(look.x, 0.0, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-6) {
            var fallback = player.getForward();
            horizontalLook = new Vec3(fallback.x, 0.0, fallback.z);
        }
        horizontalLook = horizontalLook.normalize();
        var right = new Vec3(-horizontalLook.z, 0.0, horizontalLook.x);

        var basePos = player.position().add(0.0, 1.1, 0.0);
        var screenPos = basePos.add(horizontalLook.scale(2.0));

        var screen = new ScreenPeripheral(level);
        screen.setScreenSize(SCREEN_WIDTH, SCREEN_HEIGHT);
        screen.setPosition(screenPos);
        // Rotate screen so its +Z face points back toward the player
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        screen.setWorldRotation(new Quaternionf().rotationY((float) Math.PI + yawRad));

        var speakerOffset = screen.getScreenWidth() * 0.5F + 0.35F;
        var speakerOffsetVector = right.scale(speakerOffset);
        var leftSpeakerPos = screenPos.subtract(speakerOffsetVector);
        var rightSpeakerPos = screenPos.add(speakerOffsetVector);

        var leftSpeaker = new SpeakerPeripheral(level);
        leftSpeaker.setMaxRange(SpeakerPeripheral.DEFAULT_MAX_RANGE);
        leftSpeaker.setAudioChannelMode(SpeakerAudioChannelMode.LEFT);
        leftSpeaker.setPosition(leftSpeakerPos);

        var rightSpeaker = new SpeakerPeripheral(level);
        rightSpeaker.setMaxRange(SpeakerPeripheral.DEFAULT_MAX_RANGE);
        rightSpeaker.setAudioChannelMode(SpeakerAudioChannelMode.RIGHT);
        rightSpeaker.setPosition(rightSpeakerPos);

        var manager = MediaPlayerHostManager.get();
        var decoderH = decoderMaxHeight(Configs.MAX_RESOLUTION_HEIGHT);
        var handle = manager.createHostAndGetId(new DecoderConfiguration.Builder()
                .maxVideoSize(decoderMaxWidth(decoderH), decoderH)
                .build());
        manager.assignPeripheralToHost(handle.hostId(), screen);
        manager.assignPeripheralToHost(handle.hostId(), leftSpeaker);
        manager.assignPeripheralToHost(handle.hostId(), rightSpeaker);

        source.sendSuccess(() -> Component.literal(
                "Created host " + handle.hostId() + " with runtime screen and stereo speakers"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int listHosts(CommandSourceStack source) {
        var hostEntries = MediaPlayerHostManager.get().getHostsByIdSnapshot();
        if (hostEntries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No hosts, use /mcedia host create"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.literal("Hosts:"), false);
        for (var entry : hostEntries.entrySet()) {
            int hostId = entry.getKey();
            int peripheralCount = entry.getValue().peripheralCount();
            source.sendSuccess(() -> Component.literal("- " + hostId + " (peripherals=" + peripheralCount + ")"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showDanmakuVisible(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Danmaku visible: " + Configs.DANMAKU_VISIBLE), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setDanmakuVisible(CommandSourceStack source, boolean visible) {
        Configs.DANMAKU_VISIBLE = visible;
        source.sendSuccess(() -> Component.literal("Set danmaku visible to " + visible), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showVolume(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Global volume factor: " + String.format(Locale.ROOT, "%.2f", Configs.VOLUME_FACTOR)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setVolume(CommandSourceStack source, double factor) {
        Configs.VOLUME_FACTOR = (float) Math.min(Math.max(factor, 0.0), 4.0);
        source.sendSuccess(() -> Component.literal("Set global volume factor to " + String.format(Locale.ROOT, "%.2f", Configs.VOLUME_FACTOR)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static String resolutionLevelName(int height) {
        if (height <= 0) return "unlimited";
        return switch (height) {
            case Configs.RES_320P -> "320p";
            case Configs.RES_720P -> "720p";
            case Configs.RES_1080P -> "1080p";
            case Configs.RES_2K -> "2k";
            case Configs.RES_4K -> "4k";
            case Configs.RES_8K -> "8k";
            default -> height + "p";
        };
    }

    private static int resolutionHeight(String level) {
        return switch (level.toLowerCase()) {
            case "unlimited" -> 0;
            case "320p" -> Configs.RES_320P;
            case "720p" -> Configs.RES_720P;
            case "1080p" -> Configs.RES_1080P;
            case "2k" -> Configs.RES_2K;
            case "4k" -> Configs.RES_4K;
            case "8k" -> Configs.RES_8K;
            default -> throw new IllegalArgumentException("未知分辨率: " + level);
        };
    }

    private static int decoderMaxHeight(int resolutionHeight) {
        if (resolutionHeight <= 0) return 0;
        return (int) (resolutionHeight * 1.2f);
    }

    private static int decoderMaxWidth(int decoderHeight) {
        if (decoderHeight <= 0) return 0;
        return decoderHeight * 16 / 9;
    }

    private static int showConfig(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Max video resolution: " + resolutionLevelName(Configs.MAX_RESOLUTION_HEIGHT)), false);
        source.sendSuccess(() -> Component.literal("Global volume factor: " + String.format(Locale.ROOT, "%.2f", Configs.VOLUME_FACTOR)), false);
        source.sendSuccess(() -> Component.literal("Active decoder limit: " + Configs.ACTIVE_DECODER_LIMIT), false);
        source.sendSuccess(() -> Component.literal("Throttled decoder limit: " + Configs.THROTTLED_DECODER_LIMIT), false);
        source.sendSuccess(() -> Component.literal("Low overhead upload FPS: " + Configs.LOW_OVERHEAD_UPLOAD_FPS), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showActiveDecoderLimit(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Active decoder limit: " + Configs.ACTIVE_DECODER_LIMIT), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setActiveDecoderLimit(CommandSourceStack source, int count) {
        Configs.ACTIVE_DECODER_LIMIT = Math.max(0, count);
        source.sendSuccess(() -> Component.literal("Set active decoder limit to " + Configs.ACTIVE_DECODER_LIMIT), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showThrottledDecoderLimit(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Throttled decoder limit: " + Configs.THROTTLED_DECODER_LIMIT), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setThrottledDecoderLimit(CommandSourceStack source, int count) {
        Configs.THROTTLED_DECODER_LIMIT = Math.max(0, count);
        source.sendSuccess(() -> Component.literal("Set throttled decoder limit to " + Configs.THROTTLED_DECODER_LIMIT), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showLowOverheadFPS(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Low overhead upload FPS: " + Configs.LOW_OVERHEAD_UPLOAD_FPS), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setLowOverheadFPS(CommandSourceStack source, int fps) {
        Configs.LOW_OVERHEAD_UPLOAD_FPS = Math.max(1, fps);
        source.sendSuccess(() -> Component.literal("Set low overhead upload FPS to " + Configs.LOW_OVERHEAD_UPLOAD_FPS), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setResolution(CommandSourceStack source, String level) {
        var height = resolutionHeight(level);
        Configs.MAX_RESOLUTION_HEIGHT = height;
        MediaResolverSettings.setResolutionLimit(height);
        source.sendSuccess(() -> Component.literal("Set max video resolution to " + resolutionLevelName(height)), false);
        source.sendSuccess(() -> Component.literal("New hosts will use this limit. Reload media on existing hosts to apply."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setHostUrl(CommandSourceStack source, int hostId, String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            source.sendFailure(Component.literal("mediaUrl cannot be blank"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Loading media for host " + hostId + "..."), false);
        var host = MediaPlayerHostManager.get().getHostById(hostId);
        if (host == null) {
            source.sendFailure(Component.literal("Host not found: " + hostId));
            return 0;
        }

        host.playAsync(() -> MediaResolvers.resolve(mediaUrl))
                .whenComplete((ignored, throwable) -> {
                    var client = Minecraft.getInstance();
                    client.execute(() -> {
                        if (throwable == null) {
                            source.sendSuccess(() -> Component.literal("Host " + hostId + " loaded media"), false);
                            return;
                        }
                        source.sendFailure(Component
                                .literal("Failed to load media for host " + hostId + ": " + rootMessage(throwable)));
                    });
                });
        return Command.SINGLE_SUCCESS;
    }

    private static int seekHostRelative(CommandSourceStack source, int hostId, int seconds, boolean forward) {
        var host = MediaPlayerHostManager.get().getHostById(hostId);
        if (host == null) {
            source.sendFailure(Component.literal("Host not found: " + hostId));
            return 0;
        }

        var player = host.getPlayer();
        var media = player.getMedia();
        if (media == null) {
            source.sendFailure(Component.literal("Host " + hostId + " has no active media"));
            return 0;
        }

        var deltaUs = seconds * 1_000_000L;
        if (!forward) {
            deltaUs = -deltaUs;
        }

        var currentUs = media.getEstimatedTime();
        var targetUs = currentUs + deltaUs;
        if (targetUs < 0L) {
            targetUs = 0L;
        }

        var durationUs = media.getDuration();
        if (durationUs > 0L && targetUs > durationUs) {
            targetUs = durationUs;
        }

        long seekTargetUs = targetUs;

        if (player instanceof SingleMediaPlayer singlePlayer) {
            singlePlayer.seekAsync(seekTargetUs);
        } else {
            media.seek(seekTargetUs);
        }
        source.sendSuccess(() -> Component.literal((forward ? "Forwarded " : "Moved backward ")
                + seconds + "s for host " + hostId
                + " (time=" + formatSeconds(seekTargetUs) + "s)"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setHostSpeed(CommandSourceStack source, int hostId, double speed) {
        var host = MediaPlayerHostManager.get().getHostById(hostId);
        if (host == null) {
            source.sendFailure(Component.literal("Host not found: " + hostId));
            return 0;
        }

        LOGGER.info("Set host {} speed to {}x", hostId, speed);
        host.getPlayer().setSpeed(speed);
        source.sendSuccess(() -> Component.literal("Set host " + hostId + " speed to " + speed + "x"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setHostPaused(CommandSourceStack source, int hostId, boolean paused) {
        var host = MediaPlayerHostManager.get().getHostById(hostId);
        if (host == null) {
            source.sendFailure(Component.literal("Host not found: " + hostId));
            return 0;
        }

        var player = host.getPlayer();
        if (!(player instanceof SingleMediaPlayer singlePlayer)) {
            source.sendFailure(Component.literal("Host player does not support pause control"));
            return 0;
        }

        singlePlayer.setPaused(paused);
        source.sendSuccess(() -> Component.literal((paused ? "Paused " : "Resumed ") + "host " + hostId), false);
        return Command.SINGLE_SUCCESS;
    }

    private static String rootMessage(Throwable throwable) {
        var cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }

        var message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            return cursor.getClass().getSimpleName();
        }
        return message;
    }

    private static String formatSeconds(long microseconds) {
        return String.format(Locale.ROOT, "%.3f", microseconds / 1_000_000.0D);
    }
}

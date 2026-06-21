package top.tobyprime.mcedia_core.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private ClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("mcedia");

            root.then(ClientCommandManager.literal("dev")
                    .then(ClientCommandManager.literal("create")
                            .executes(context -> createHostWithPeripherals(context.getSource())))
                    .then(ClientCommandManager.literal("createHud")
                            .executes(context -> createHudHost(context.getSource()))));

            root.then(ClientCommandManager.literal("config")
                    .executes(context -> showConfig(context.getSource()))
                    .then(ClientCommandManager.literal("resolution")
                            .then(ClientCommandManager.argument("level", StringArgumentType.word())
                                    .suggests(ClientCommands::suggestResolutionLevels)
                                    .executes(context -> setResolution(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "level")))))
                    .then(ClientCommandManager.literal("activedecoderlimit")
                            .executes(context -> showActiveDecoderLimit(context.getSource()))
                            .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(0))
                                    .executes(context -> setActiveDecoderLimit(
                                            context.getSource(),
                                            IntegerArgumentType.getInteger(context, "count")))))
                    .then(ClientCommandManager.literal("throttleddecoderlimit")
                            .executes(context -> showThrottledDecoderLimit(context.getSource()))
                            .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(0))
                                    .executes(context -> setThrottledDecoderLimit(
                                            context.getSource(),
                                            IntegerArgumentType.getInteger(context, "count")))))
                    .then(ClientCommandManager.literal("lowoverheadfps")
                            .executes(context -> showLowOverheadFPS(context.getSource()))
                            .then(ClientCommandManager.argument("fps", IntegerArgumentType.integer(1))
                                    .executes(context -> setLowOverheadFPS(
                                            context.getSource(),
                                            IntegerArgumentType.getInteger(context, "fps")))))
                    .then(ClientCommandManager.literal("danmaku")
                            .executes(context -> showDanmakuVisible(context.getSource()))
                            .then(ClientCommandManager.argument("visible", BoolArgumentType.bool())
                                    .executes(context -> setDanmakuVisible(
                                            context.getSource(),
                                            BoolArgumentType.getBool(context, "visible")))))
                    .then(ClientCommandManager.literal("volume")
                            .executes(context -> showVolume(context.getSource()))
                            .then(ClientCommandManager.argument("factor", DoubleArgumentType.doubleArg(0.0D, 4.0D))
                                    .executes(context -> setVolume(
                                            context.getSource(),
                                            DoubleArgumentType.getDouble(context, "factor"))))));

            root.then(ClientCommandManager.literal("host")
                    .then(ClientCommandManager.literal("create")
                            .executes(context -> createHost(context.getSource())))
                    .then(ClientCommandManager.literal("list")
                            .executes(context -> listHosts(context.getSource())))
                    .then(ClientCommandManager.literal("seturl")
                            .then(ClientCommandManager.argument("hostId", IntegerArgumentType.integer(1))
                                    .suggests(ClientCommands::suggestHostIds)
                                    .then(ClientCommandManager.argument("mediaUrl", StringArgumentType.greedyString())
                                            .executes(context -> setHostUrl(
                                                    context.getSource(),
                                                    IntegerArgumentType.getInteger(context, "hostId"),
                                                    StringArgumentType.getString(context, "mediaUrl"))))))
                    .then(ClientCommandManager.literal("forward")
                            .then(ClientCommandManager.argument("hostId", IntegerArgumentType.integer(1))
                                    .suggests(ClientCommands::suggestHostIds)
                                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1))
                                            .executes(context -> seekHostRelative(
                                                    context.getSource(),
                                                    IntegerArgumentType.getInteger(context, "hostId"),
                                                    IntegerArgumentType.getInteger(context, "seconds"),
                                                    true)))))
                    .then(ClientCommandManager.literal("backward")
                            .then(ClientCommandManager.argument("hostId", IntegerArgumentType.integer(1))
                                    .suggests(ClientCommands::suggestHostIds)
                                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1))
                                            .executes(context -> seekHostRelative(
                                                    context.getSource(),
                                                    IntegerArgumentType.getInteger(context, "hostId"),
                                                    IntegerArgumentType.getInteger(context, "seconds"),
                                                    false)))))
                    .then(ClientCommandManager.literal("setspeed")
                            .then(ClientCommandManager.argument("hostId", IntegerArgumentType.integer(1))
                                    .suggests(ClientCommands::suggestHostIds)
                                    .then(ClientCommandManager.argument("speed", DoubleArgumentType.doubleArg(0.1D, 8.0D))
                                            .executes(context -> setHostSpeed(
                                                    context.getSource(),
                                                    IntegerArgumentType.getInteger(context, "hostId"),
                                                    DoubleArgumentType.getDouble(context, "speed"))))))
                    .then(ClientCommandManager.literal("pause")
                            .then(ClientCommandManager.argument("hostId", IntegerArgumentType.integer(1))
                                    .suggests(ClientCommands::suggestHostIds)
                                    .executes(context -> setHostPaused(
                                            context.getSource(),
                                            IntegerArgumentType.getInteger(context, "hostId"),
                                            true))
                                    .then(ClientCommandManager.argument("paused", BoolArgumentType.bool())
                                            .executes(context -> setHostPaused(
                                                    context.getSource(),
                                                    IntegerArgumentType.getInteger(context, "hostId"),
                                                    BoolArgumentType.getBool(context, "paused")))))));

            dispatcher.register(root);
        });
    }

    private static int createHost(FabricClientCommandSource source) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            var decoderH = decoderMaxHeight(Configs.MAX_RESOLUTION_HEIGHT);
            var handle = MediaPlayerHostManager.get().createHostAndGetId(new DecoderConfiguration.Builder()
                    .maxVideoSize(decoderMaxWidth(decoderH), decoderH)
                    .build());
            source.sendFeedback(Component.literal("Created host " + handle.hostId()));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int createHudHost(FabricClientCommandSource source) {
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

            source.sendFeedback(Component.literal(
                    "Created host " + handle.hostId() + " with HUD screen and stereo speakers"));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int createHostWithPeripherals(FabricClientCommandSource source) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            var player = client.player;
            var level = source.getWorld();
            if (player == null || level == null) {
                source.sendError(Component.literal("Client player or level not ready"));
                return;
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
            var yaw = 0.0F;

            var screen = new ScreenPeripheral(level);
            screen.setScreenSize(SCREEN_WIDTH, SCREEN_HEIGHT);
            screen.setPosition(screenPos);

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

            source.sendFeedback(Component.literal(
                    "Created host " + handle.hostId()
                            + " with runtime screen and stereo speakers"));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int listHosts(FabricClientCommandSource source) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            var hostEntries = MediaPlayerHostManager.get().getHostsByIdSnapshot();
            if (hostEntries.isEmpty()) {
                source.sendFeedback(Component.literal("No hosts, use /mcedia host create"));
                return;
            }

            source.sendFeedback(Component.literal("Hosts:"));
            for (var entry : hostEntries.entrySet()) {
                source.sendFeedback(Component
                        .literal("- " + entry.getKey() + " (peripherals=" + entry.getValue().peripheralCount() + ")"));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int showDanmakuVisible(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Danmaku visible: " + Configs.DANMAKU_VISIBLE));
        return Command.SINGLE_SUCCESS;
    }

    private static int setDanmakuVisible(FabricClientCommandSource source, boolean visible) {
        Configs.DANMAKU_VISIBLE = visible;
        source.sendFeedback(Component.literal("Set danmaku visible to " + visible));
        return Command.SINGLE_SUCCESS;
    }

    private static int showVolume(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Global volume factor: " + String.format(Locale.ROOT, "%.2f", Configs.VOLUME_FACTOR)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setVolume(FabricClientCommandSource source, double factor) {
        Configs.VOLUME_FACTOR = (float) Math.min(Math.max(factor, 0.0), 4.0);
        source.sendFeedback(Component.literal("Set global volume factor to " + String.format(Locale.ROOT, "%.2f", Configs.VOLUME_FACTOR)));
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

    private static int showConfig(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Max video resolution: " + resolutionLevelName(Configs.MAX_RESOLUTION_HEIGHT)));
        source.sendFeedback(Component.literal("Global volume factor: " + String.format(Locale.ROOT, "%.2f", Configs.VOLUME_FACTOR)));
        source.sendFeedback(Component.literal("Active decoder limit: " + Configs.ACTIVE_DECODER_LIMIT));
        source.sendFeedback(Component.literal("Throttled decoder limit: " + Configs.THROTTLED_DECODER_LIMIT));
        source.sendFeedback(Component.literal("Low overhead upload FPS: " + Configs.LOW_OVERHEAD_UPLOAD_FPS));
        return Command.SINGLE_SUCCESS;
    }

    private static int showActiveDecoderLimit(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Active decoder limit: " + Configs.ACTIVE_DECODER_LIMIT));
        return Command.SINGLE_SUCCESS;
    }

    private static int setActiveDecoderLimit(FabricClientCommandSource source, int count) {
        Configs.ACTIVE_DECODER_LIMIT = Math.max(0, count);
        source.sendFeedback(Component.literal("Set active decoder limit to " + Configs.ACTIVE_DECODER_LIMIT));
        return Command.SINGLE_SUCCESS;
    }

    private static int showThrottledDecoderLimit(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Throttled decoder limit: " + Configs.THROTTLED_DECODER_LIMIT));
        return Command.SINGLE_SUCCESS;
    }

    private static int setThrottledDecoderLimit(FabricClientCommandSource source, int count) {
        Configs.THROTTLED_DECODER_LIMIT = Math.max(0, count);
        source.sendFeedback(Component.literal("Set throttled decoder limit to " + Configs.THROTTLED_DECODER_LIMIT));
        return Command.SINGLE_SUCCESS;
    }

    private static int showLowOverheadFPS(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Low overhead upload FPS: " + Configs.LOW_OVERHEAD_UPLOAD_FPS));
        return Command.SINGLE_SUCCESS;
    }

    private static int setLowOverheadFPS(FabricClientCommandSource source, int fps) {
        Configs.LOW_OVERHEAD_UPLOAD_FPS = Math.max(1, fps);
        source.sendFeedback(Component.literal("Set low overhead upload FPS to " + Configs.LOW_OVERHEAD_UPLOAD_FPS));
        return Command.SINGLE_SUCCESS;
    }

    private static int setResolution(FabricClientCommandSource source, String level) {
        var height = resolutionHeight(level);
        Configs.MAX_RESOLUTION_HEIGHT = height;
        MediaResolverSettings.setResolutionLimit(height);
        source.sendFeedback(Component.literal("Set max video resolution to " + resolutionLevelName(height)));
        source.sendFeedback(Component.literal("New hosts will use this limit. Reload media on existing hosts to apply."));
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestResolutionLevels(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                java.util.List.of("320p", "720p", "1080p", "2k", "4k", "8k", "unlimited"), builder);
    }

    private static int setHostUrl(FabricClientCommandSource source, int hostId, String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            source.sendError(Component.literal("mediaUrl cannot be blank"));
            return 0;
        }

        var client = Minecraft.getInstance();
        client.execute(() -> {
            var host = MediaPlayerHostManager.get().getHostById(hostId);
            if (host == null) {
                source.sendError(Component.literal("Host not found: " + hostId));
                return;
            }

            source.sendFeedback(Component.literal("Loading media for host " + hostId + "..."));
            host.playAsync(() -> MediaResolvers.resolve(mediaUrl))
                    .whenComplete((ignored, throwable) -> client.execute(() -> {
                        if (throwable == null) {
                            source.sendFeedback(Component.literal("Host " + hostId + " loaded media"));
                            return;
                        }
                        source.sendError(Component
                                .literal("Failed to load media for host " + hostId + ": " + rootMessage(throwable)));
                    }));
        });
        return Command.SINGLE_SUCCESS;
    }


    private static int seekHostRelative(FabricClientCommandSource source, int hostId, int seconds, boolean forward) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            var host = MediaPlayerHostManager.get().getHostById(hostId);
            if (host == null) {
                source.sendError(Component.literal("Host not found: " + hostId));
                return;
            }

            var media = host.getPlayer().getMedia();
            if (media == null) {
                source.sendError(Component.literal("Host " + hostId + " has no active media"));
                return;
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

            if (host.getPlayer() instanceof SingleMediaPlayer singlePlayer) {
                singlePlayer.seekAsync(targetUs);
            } else {
                media.seek(targetUs);
            }
            source.sendFeedback(Component.literal((forward ? "Forwarded " : "Moved backward ")
                    + seconds + "s for host " + hostId
                    + " (time=" + formatSeconds(targetUs) + "s)"));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int setHostSpeed(FabricClientCommandSource source, int hostId, double speed) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            var host = MediaPlayerHostManager.get().getHostById(hostId);
            if (host == null) {
                source.sendError(Component.literal("Host not found: " + hostId));
                return;
            }

            LOGGER.info("Set host {} speed to {}x", hostId, speed);
            host.getPlayer().setSpeed(speed);
            source.sendFeedback(Component.literal("Set host " + hostId + " speed to " + speed + "x"));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int setHostPaused(FabricClientCommandSource source, int hostId, boolean paused) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            var host = MediaPlayerHostManager.get().getHostById(hostId);
            if (host == null) {
                source.sendError(Component.literal("Host not found: " + hostId));
                return;
            }

            var player = host.getPlayer();
            if (!(player instanceof SingleMediaPlayer singlePlayer)) {
                source.sendError(Component.literal("Host player does not support pause control"));
                return;
            }

            singlePlayer.setPaused(paused);
            source.sendFeedback(Component.literal((paused ? "Paused " : "Resumed ") + "host " + hostId));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestHostIds(CommandContext<FabricClientCommandSource> context,
            SuggestionsBuilder builder) {
        var hostEntries = MediaPlayerHostManager.get().getHostsByIdSnapshot();
        var suggestions = new ArrayList<String>();
        for (var hostId : hostEntries.keySet()) {
            suggestions.add(Integer.toString(hostId));
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
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

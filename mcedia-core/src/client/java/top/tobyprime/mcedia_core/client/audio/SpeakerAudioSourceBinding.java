package top.tobyprime.mcedia_core.client.audio;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import top.tobyprime.mcedia_core.client.player.PlayerHost;

import java.util.function.Supplier;

public final class SpeakerAudioSourceBinding implements AudioSourceBinding {

    private final Object lock = new Object();
    private final OpenAlAudioSource source;
    private @Nullable PlayerHost host;
    private boolean closed;

    public SpeakerAudioSourceBinding(MinecraftSoundEngineAdapter soundEngineAdapter, Supplier<Vec3> positionSupplier) {
        this(soundEngineAdapter, positionSupplier, false);
    }

    public SpeakerAudioSourceBinding(MinecraftSoundEngineAdapter soundEngineAdapter, Supplier<Vec3> positionSupplier, boolean relative) {
        this.source = new OpenAlAudioSource(soundEngineAdapter, positionSupplier);
        this.source.setRelative(relative);
    }

    @Override
    public void attach(@Nullable PlayerHost host) {
        synchronized (lock) {
            if (closed || this.host == host) {
                return;
            }

            var oldHost = this.host;
            this.host = host;

            if (oldHost != null) {
                oldHost.getPlayer().unbindAudioSource(source);
            }
            if (host != null) {
                host.getPlayer().bindAudioSource(source);
            }
        }
    }

    public void setMaxRange(float maxRange) {
        source.setMaxDistance(maxRange);
    }

    public void setVolume(float gain) {
        source.setVolume(gain);
    }

    public void setChannelMode(SpeakerAudioChannelMode channelMode) {
        source.setChannelMode(channelMode);
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;

            if (host != null) {
                host.getPlayer().unbindAudioSource(source);
                host = null;
            }
            source.close();
        }
    }
}

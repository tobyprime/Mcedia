package top.tobyprime.mcedia_core.client.player;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import top.tobyprime.mcedia_core.client.audio.MinecraftSoundEngineAdapter;
import top.tobyprime.mcedia_core.client.audio.SpeakerAudioChannelMode;
import top.tobyprime.mcedia_core.client.audio.SpeakerAudioSourceBinding;

public final class HudSpeakerPeripheral implements MediaPlayerPeripheral, AutoCloseable {
    private static final Vec3 ORIGIN = Vec3.ZERO;

    private final SpeakerAudioSourceBinding audioBinding;
    private boolean closed;

    public HudSpeakerPeripheral() {
        this.audioBinding = new SpeakerAudioSourceBinding(
                new MinecraftSoundEngineAdapter(), () -> ORIGIN, true
        );
    }

    public void setVolume(float gain) {
        audioBinding.setVolume(gain);
    }

    public void setAudioChannelMode(SpeakerAudioChannelMode channelMode) {
        audioBinding.setChannelMode(channelMode);
    }

    @Override
    public void setPlayerHost(@Nullable PlayerHost host) {
        audioBinding.attach(host);
    }

    @Override
    public boolean isActive() {
        return !closed;
    }

    @Override
    public boolean isAlive() {
        return isActive();
    }

    @Override
    public double getDistance() {
        return 0.0;
    }

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.Speaker;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        audioBinding.close();
    }
}

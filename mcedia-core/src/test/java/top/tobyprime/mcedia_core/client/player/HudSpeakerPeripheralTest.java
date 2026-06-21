package top.tobyprime.mcedia_core.client.player;

import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_core.client.audio.SpeakerAudioChannelMode;

import static org.junit.jupiter.api.Assertions.*;

class HudSpeakerPeripheralTest {

    @Test
    void isActiveFalseAfterClose() {
        var p = new HudSpeakerPeripheral();
        assertTrue(p.isActive());
        assertTrue(p.isAlive());
        p.close();
        assertFalse(p.isActive());
        assertFalse(p.isAlive());
    }

    @Test
    void getDistanceAlwaysZero() {
        var p = new HudSpeakerPeripheral();
        assertEquals(0.0, p.getDistance());
    }

    @Test
    void peripheralTypeIsSpeaker() {
        var p = new HudSpeakerPeripheral();
        assertEquals(PeripheralType.Speaker, p.getPeripheralType());
    }

    @Test
    void closeIsIdempotent() {
        var p = new HudSpeakerPeripheral();
        p.close();
        p.close(); // should not throw
        assertFalse(p.isAlive());
    }

    @Test
    void setVolumeDoesNotThrow() {
        var p = new HudSpeakerPeripheral();
        p.setVolume(0.5F);
    }

    @Test
    void setAudioChannelModeDoesNotThrow() {
        var p = new HudSpeakerPeripheral();
        p.setAudioChannelMode(SpeakerAudioChannelMode.LEFT);
        p.setAudioChannelMode(SpeakerAudioChannelMode.RIGHT);
        p.setAudioChannelMode(SpeakerAudioChannelMode.MIX);
    }
}

package top.tobyprime.mcedia_core.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudScreenRendererTest {

    @Test
    void applyMinimumBrightnessActsAsFloor() {
        var darker = 0;
        var brighter = PlayerScreenEntityRenderer.applyMinimumBrightness(darker, 10);
        assertNotEquals(darker, brighter);
        assertEquals(brighter, PlayerScreenEntityRenderer.applyMinimumBrightness(brighter, 10));
    }

    @Test
    void keepsBrightLightUnchanged() {
        var light = PlayerScreenEntityRenderer.applyMinimumBrightness(0, 12);
        assertEquals(light, PlayerScreenEntityRenderer.applyMinimumBrightness(light, 5));
    }
}

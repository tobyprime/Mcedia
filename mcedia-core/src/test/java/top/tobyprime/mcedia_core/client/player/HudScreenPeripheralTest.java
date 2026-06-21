package top.tobyprime.mcedia_core.client.player;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudScreenPeripheralTest {

    @Test
    void defaultsToKeepAspectCover() {
        var p = new HudScreenPeripheral();
        assertEquals(ScreenPeripheral.ScreenFillMode.KEEP_ASPECT_COVER, p.getFillMode());
    }

    @Test
    void defaultsToIdleBackgroundTexture() {
        var p = new HudScreenPeripheral();
        assertNotNull(p.getBackgroundTextureId());
    }

    @Test
    void defaultsSize() {
        var p = new HudScreenPeripheral();
        assertTrue(p.getWidth() > 0);
        assertTrue(p.getHeight() > 0);
    }

    @Test
    void setScreenSizeEnforcesMinimum() {
        var p = new HudScreenPeripheral();
        p.setScreenSize(0, 0);
        assertEquals(1, p.getWidth());
        assertEquals(1, p.getHeight());
    }

    @Test
    void setPosition() {
        var p = new HudScreenPeripheral();
        p.setPosition(100, 200);
        assertEquals(100, p.getX());
        assertEquals(200, p.getY());
    }

    @Test
    void setFillModeNullUsesFill() {
        var p = new HudScreenPeripheral();
        p.setFillMode(null);
        assertEquals(ScreenPeripheral.ScreenFillMode.FILL, p.getFillMode());
    }

    @Test
    void isActiveFalseAfterClose() {
        var p = new HudScreenPeripheral();
        assertTrue(p.isActive());
        assertTrue(p.isAlive());
        p.close();
        assertFalse(p.isActive());
        assertFalse(p.isAlive());
    }

    @Test
    void getDistanceAlwaysZero() {
        var p = new HudScreenPeripheral();
        assertEquals(0.0, p.getDistance());
    }

    @Test
    void peripheralTypeIsScreen() {
        var p = new HudScreenPeripheral();
        assertEquals(PeripheralType.Screen, p.getPeripheralType());
    }

    @Test
    void getMediaPlayReturnsNullWhenNoHost() {
        var p = new HudScreenPeripheral();
        assertNull(p.getMediaPlay());
    }

    @Test
    void getTextureReturnsNullWhenNoHost() {
        var p = new HudScreenPeripheral();
        assertNull(p.getTexture());
    }

    @Test
    void closeIsIdempotent() {
        var p = new HudScreenPeripheral();
        p.close();
        p.close(); // should not throw
        assertFalse(p.isAlive());
    }
}

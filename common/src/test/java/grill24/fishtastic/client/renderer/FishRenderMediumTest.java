package grill24.fishtastic.client.renderer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FishRenderMediumTest {
    private final FishRenderMedium.Submerged property = new FishRenderMedium.Submerged();

    @AfterEach
    void reset() {
        FishRenderMedium.set(false);
        FishRenderMedium.setForced(FishRenderMedium.Forced.AUTO);
    }

    @Test
    void defaultsToDry() {
        assertFalse(FishRenderMedium.wet());
        assertFalse(property.get(null, null, null, 0, null));
    }

    @Test
    void propertyFollowsTheRendererFlagAndSetReturnsThePrevious() {
        assertFalse(FishRenderMedium.set(true));
        assertTrue(property.get(null, null, null, 0, null));
        assertTrue(FishRenderMedium.set(false));
        assertFalse(property.get(null, null, null, 0, null));
    }

    @Test
    void forcedOverridesTheRendererFlag() {
        FishRenderMedium.set(true);
        FishRenderMedium.setForced(FishRenderMedium.Forced.DRY);
        assertFalse(FishRenderMedium.wet());
        FishRenderMedium.set(false);
        FishRenderMedium.setForced(FishRenderMedium.Forced.WET);
        assertTrue(FishRenderMedium.wet());
        FishRenderMedium.setForced(FishRenderMedium.Forced.AUTO);
        assertFalse(FishRenderMedium.wet());
    }

    @Test
    void resolveRestoresTheFlagWhenTheResolverThrows() {
        // A null resolver throws inside the scope; the flag must come back dry.
        assertThrows(NullPointerException.class, () -> FishRenderMedium.resolve(null, null, null, true));
        assertFalse(FishRenderMedium.wet());
    }
}

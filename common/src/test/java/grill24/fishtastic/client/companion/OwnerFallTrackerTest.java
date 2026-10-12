package grill24.fishtastic.client.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnerFallTrackerTest {
    @Test
    void reportsLandingOnceIncludingTheFinalDescentAfterVanillaReset() {
        OwnerFallTracker fall = new OwnerFallTracker();
        assertEquals(0, fall.update(5, true, 0, true, false));
        assertEquals(0, fall.update(5, false, 0, true, false));
        assertEquals(0, fall.update(3, false, 2, true, false));
        assertEquals(5, fall.update(0, true, 0, true, false));
        assertEquals(0, fall.update(0, true, 0, true, false));
    }

    @Test
    void teleportAndProtectedMovementDiscardPendingFall() {
        for (boolean teleport : new boolean[]{false, true}) {
            OwnerFallTracker fall = new OwnerFallTracker();
            fall.update(5, true, 0, true, false);
            fall.update(4, false, 1, true, false);
            assertEquals(0, fall.update(0, true, 0, teleport, teleport));
            assertEquals(0, fall.update(0, true, 0, true, false));
        }
        OwnerFallTracker ladder = new OwnerFallTracker();
        ladder.update(5, false, 0, false, false);
        assertEquals(0, ladder.update(0, true, 0, true, false));
    }
}

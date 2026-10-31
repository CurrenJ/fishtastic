package grill24.fishtastic.client.encyclopedia;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishPageModelTest {

    @Test
    void observationRevealsTheNameWithoutACatchOrClaim() {
        assertTrue(FishPageModel.nameRevealed(true, 0, 5, false));
    }

    @Test
    void anUnobservedSpeciesNeedsACatchEvenWhenItsNameRewardWasClaimed() {
        assertFalse(FishPageModel.nameRevealed(false, 0, 1, false));
        assertFalse(FishPageModel.nameRevealed(false, 0, 1, true));
    }

    @Test
    void catchesRevealTheNameAtTheThreshold() {
        assertFalse(FishPageModel.nameRevealed(false, 3, 4, false));
        assertTrue(FishPageModel.nameRevealed(false, 4, 4, false));
        assertTrue(FishPageModel.nameRevealed(false, 5, 4, false));
        assertTrue(FishPageModel.nameRevealed(false, 1, 1, false));
    }

    @Test
    void aClaimedNameStaysRevealedForACaughtSpeciesBelowTheCurrentThreshold() {
        assertTrue(FishPageModel.nameRevealed(false, 1, 4, true));
    }
}

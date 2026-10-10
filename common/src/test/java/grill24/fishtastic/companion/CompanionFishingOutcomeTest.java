package grill24.fishtastic.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The pure session-outcome mapping shared by the owner's client and the server's tracker events
 * (docs/fish-companions.md §5): a caught fish beats everything, an escaped fish is a droop, and a
 * cast with no fish on the line at all (treasure/trash only) is nothing to react to.
 */
class CompanionFishingOutcomeTest {

    @Test
    void caughtFishWinsEvenIfAnotherEscaped() {
        assertEquals(CompanionFishingOutcome.CAUGHT, CompanionFishingOutcome.of(true, true));
    }

    @Test
    void escapedFishWhenNoneWasCaught() {
        assertEquals(CompanionFishingOutcome.ESCAPED, CompanionFishingOutcome.of(true, false));
    }

    @Test
    void noFishAtAllIsNothing() {
        assertEquals(CompanionFishingOutcome.NONE, CompanionFishingOutcome.of(false, false));
        assertEquals(CompanionFishingOutcome.CAUGHT, CompanionFishingOutcome.of(false, true),
                "a caught fish implies a fish target; the degenerate input still reads as a catch");
    }
}

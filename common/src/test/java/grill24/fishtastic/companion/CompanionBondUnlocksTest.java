package grill24.fishtastic.companion;

import grill24.fishtastic.data.FishEncyclopediaEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure "entry complete" rule behind companion bond unlocks (docs/fish-companions.md,
 * R6.5): every derived threshold on the one species — not just the lore count — must be met.
 * The quest-hook half of the derivation touches {@code Quest}'s registry-backed codecs and runs
 * as a game test instead (CompanionQuestUnlockGameTests).
 */
class CompanionBondUnlocksTest {

    @Test
    void entryCompleteRequiresEveryThreshold() {
        FishEncyclopediaEntry.UnlockThresholds thresholds =
                new FishEncyclopediaEntry.UnlockThresholds(1, 1, 2, 2, 6);

        assertFalse(CompanionBondUnlocks.isEntryComplete(5, thresholds), "5 catches misses the lore threshold of 6");
        assertTrue(CompanionBondUnlocks.isEntryComplete(6, thresholds), "6 catches meets every threshold");
        assertTrue(CompanionBondUnlocks.isEntryComplete(50, thresholds));
    }

    @Test
    void entryCompleteUsesTheHighestThresholdNotJustLore() {
        // A (contrived) spread where the lore count is not the furthest: completeness must still
        // mean every section, matching the server's gate rule.
        FishEncyclopediaEntry.UnlockThresholds thresholds =
                new FishEncyclopediaEntry.UnlockThresholds(9, 1, 2, 2, 6);
        assertFalse(CompanionBondUnlocks.isEntryComplete(6, thresholds), "name reveal still needs 9");
        assertTrue(CompanionBondUnlocks.isEntryComplete(9, thresholds));
    }
}

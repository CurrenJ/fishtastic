package grill24.fishtastic.companion;

/**
 * How a finished fishing minigame session reads to a companion fish (docs/fish-companions.md §5):
 * it caught a fish, the fish it was watching got away, or nothing happened that concerns it (a
 * treasure/trash-only cast). The owner's client derives this from its own minigame animation; the
 * server derives the same thing from its session targets when it tells everyone else (§11), so the
 * mapping lives here, pure, rather than on either side.
 */
public enum CompanionFishingOutcome {
    /** Nothing worth a reaction: no fish was ever on the line, or only treasure/trash. */
    NONE,
    /** A fish was landed — quiet happy wiggle, or the celebration for a tiered catch. */
    CAUGHT,
    /** A fish got away — droop, sink, swim back (§5, D5). */
    ESCAPED;

    /** @param hadFishTarget whether any target this cast was a fish (not treasure/trash) */
    public static CompanionFishingOutcome of(boolean hadFishTarget, boolean caughtFish) {
        if (caughtFish) return CAUGHT;
        return hadFishTarget ? ESCAPED : NONE;
    }
}

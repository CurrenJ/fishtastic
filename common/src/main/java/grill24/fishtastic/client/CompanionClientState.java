package grill24.fishtastic.client;

import grill24.fishtastic.network.CompanionFeedbackPacket;

/**
 * Client-side companion state that outlives a single screen: the last bond/rename outcome the
 * server sent, so the Companions tab can say why a request did nothing (docs/fish-companions.md
 * §8.2). Held here rather than in the screen because the request round-trips and the screen may
 * close or rebuild between send and answer.
 */
public final class CompanionClientState {
    private CompanionClientState() {}

    private static CompanionFeedbackPacket.Outcome lastFeedback;
    private static long lastFeedbackAtTick;

    public static final CompanionFeedbackPacket.ClientHandler PACKET_HANDLER = packet -> {
        lastFeedback = packet.outcome();
        lastFeedbackAtTick = System.currentTimeMillis();
    };

    /**
     * The most recent outcome, if it arrived recently enough to still be about what the player is
     * looking at. Stale outcomes (from an earlier session or minutes ago) are dropped so a fresh
     * picker never shows an old refusal.
     */
    public static CompanionFeedbackPacket.Outcome consumeRecentFeedback() {
        if (lastFeedback == null) return null;
        if (System.currentTimeMillis() - lastFeedbackAtTick > 10_000L) {
            lastFeedback = null;
            return null;
        }
        CompanionFeedbackPacket.Outcome outcome = lastFeedback;
        lastFeedback = null;
        return outcome;
    }

    public static void reset() {
        lastFeedback = null;
    }
}

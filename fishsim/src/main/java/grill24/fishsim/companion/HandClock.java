package grill24.fishsim.companion;

import java.util.List;

/**
 * When the unseen timer may next call a percher to the owner's hand (owner feedback 2026-10-14:
 * the hand visit came about every 20 s, and three ground fish made it 8 a minute). After any
 * landing on a hand the clock waits {@link CompanionTunables#handGapMinSeconds}..max before the
 * unseen timer may call anyone again. Looking down at it or holding something it wants still
 * brings it at once.
 *
 * <p>Alone, each percher keeps its own clock. A party shares one ({@link #shared}), so a party
 * visits no more often than a solo: when it is ready, the turn goes round (owner, 2026-10-14). The
 * percher longest since its own last visit comes to the hand, seen lately or not (the lower party
 * slot on a tie), read before anyone steps so the step order can't matter.
 */
final class HandClock {

    /** Ticks until the unseen timer may call a fish to the hand again. */
    int wait;
    /** In a party, whose turn it is once {@link #wait} runs out; null alone (its own clock). */
    PerchBody turn;

    private final PerchBody[] bodies;

    /** A percher's own clock: it counts down in {@link PerchBody}'s tick. */
    HandClock() {
        this.bodies = null;
    }

    private HandClock(List<PerchBody> bodies) {
        this.bodies = bodies.toArray(new PerchBody[0]);
        for (PerchBody b : this.bodies) b.handClock = this;
    }

    /** One clock for the party's perchers, which they all reset. */
    static HandClock shared(List<PerchBody> bodies) {
        return new HandClock(bodies);
    }

    boolean shared() {
        return bodies != null;
    }

    /** A shared clock's tick: count down, then pick whose turn it is. Call before any of them steps. */
    void tick() {
        if (wait > 0) wait--;
        turn = null;
        if (wait > 0) return;
        // Someone already on a hand or on the way there has this visit: nobody else is called.
        for (PerchBody b : bodies) {
            PerchBody.Slot to = b.heading();
            if (to != null && to.hand() || b.attached() && b.slot.hand()) return;
        }
        for (PerchBody b : bodies) {
            if (!b.mayTakeHandTurn()) continue;
            if (turn == null || b.sinceHand > turn.sinceHand
                    || b.sinceHand == turn.sinceHand && b.partySlot < turn.partySlot) {
                turn = b;
            }
        }
    }

    /** Whether the unseen timer may call {@code b} to the hand now. */
    boolean ready(PerchBody b) {
        return wait == 0 && (bodies == null || turn == b);
    }
}

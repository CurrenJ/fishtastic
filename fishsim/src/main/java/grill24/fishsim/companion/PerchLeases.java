package grill24.fishsim.companion;

import java.util.List;

/**
 * One owner's perches, shared by the party's perchers (docs/fish-companions-party.md §6.1, Phase
 * 2d). Every percher has its own home; a transient move (shoulder-wander, a hand, cover, a climb)
 * takes a perch only if no party-mate calls it home, sits on it or is heading for it, or doesn't
 * happen. So a fade or a return always lands on the fish's own home, and that home is always free.
 *
 * <p>Determinism: every percher reads the others as they were at the end of the previous tick
 * ({@link #snapshot}), so the step order can't change results. Two that set off for one free perch
 * on the same tick both see it next tick, while still crouching: whoever sits there keeps it,
 * otherwise the lower party slot (the lead first) goes and the other stays.
 */
final class PerchLeases {

    private final PerchBody[] bodies;
    private final PerchBody.Slot[] home, sits, heading;

    PerchLeases(List<PerchBody> bodies) {
        this.bodies = bodies.toArray(new PerchBody[0]);
        int n = this.bodies.length;
        home = new PerchBody.Slot[n];
        sits = new PerchBody.Slot[n];
        heading = new PerchBody.Slot[n];
        for (PerchBody b : this.bodies) b.leases = this;
        snapshot();
    }

    /** Records where each percher is and is going: call once a tick, before any of them steps. */
    void snapshot() {
        for (int i = 0; i < bodies.length; i++) {
            PerchBody b = bodies[i];
            home[i] = b.home;
            sits[i] = b.attached() ? b.slot : null;
            heading[i] = b.heading();
        }
    }

    /** Whether {@code self} may set off for {@code s}: no mate calls it home, sits on it or heads for it. */
    boolean free(PerchBody self, PerchBody.Slot s) {
        for (int i = 0; i < bodies.length; i++) {
            if (bodies[i] == self) continue;
            if (!s.hand() && home[i] == s) return false;
            if (sits[i] == s || heading[i] == s) return false;
        }
        return true;
    }

    /** Whether {@code self}, heading for {@code s}, must give way: a mate sits there, or claimed it with a lower slot. */
    boolean outranked(PerchBody self, PerchBody.Slot s) {
        for (int i = 0; i < bodies.length; i++) {
            if (bodies[i] == self) continue;
            if (sits[i] == s) return true;
            if (heading[i] == s && bodies[i].partySlot < self.partySlot) return true;
        }
        return false;
    }
}

// PORT STUB: deleted in A5.1
package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.Locomotion;

/**
 * Stand-in for the tank flock adapter (A5.1). {@link GroupSplit} is copied verbatim (pure logic),
 * so {@code GroupSplitTest} keeps testing real behaviour.
 */
public final class TankFlockAdapter {
    private TankFlockAdapter() {}

    /**
     * The single rule deciding whether a fish joins the anchor's group engine or stays behind on
     * its own tank's — and, for the ones that stay, what class they keep (docs/fish-sim-locomotion.md
     * §3.5).
     *
     * <p>It exists as an object rather than as two copies of an {@code if} because
     * {@link #rebuildGroupMode} applies it twice: once per member deciding what that tank keeps,
     * and once at the anchor collecting what the group takes. The two passes walk the same slots
     * in the same order and <b>must agree on every one of them</b> — disagree and a fish is drawn
     * twice or not at all — so they share this, quota counters included. One instance per tank; a
     * fresh one per member in the anchor's pass.
     *
     * <p>Each simulated class counts against its <b>own</b> copy of the quota. They compete for
     * different resources — water volume, floor area, and the vertical column a jellyfish pulses
     * through — so a tank full of one must not evict a creature the group has ample room for.
     */
    static final class GroupSplit {
        private final float gateRun;
        private final float gateFactor;
        private final int quota;
        private final int[] taken = new int[Locomotion.values().length];

        GroupSplit(float gateRun, float gateFactor, int quota) {
            this.gateRun = gateRun;
            this.gateFactor = gateFactor;
            this.quota = quota;
        }

        /** Whether this fish joins the group engine, consuming a slot of its class's quota if so. */
        boolean joins(Locomotion locomotion, float length) {
            boolean eligible = switch (locomotion) {
                // The group's size gate, applied here as well as in the engine, for the two
                // classes that measure a straight run: a fish the group is too cramped for stays
                // home and hovers in its own tank, which is the behaviour it has always had, and
                // it spends none of the group's budget on the way.
                case FREE_SWIM, GLIDE -> gateRun >= gateFactor * length;
                // Crawlers, drifters and eels are admitted ungated and let the engine's own
                // per-class gate demote them if it must: a demoted one still belongs in group
                // space, on the group's sand or hanging in its water, which is where the player
                // sees it.
                case BENTHIC, DRIFT, ANCHORED -> true;
                // The one class with no motion model: renders out of its own tank, frozen.
                case STATIC -> false;
            };
            if (!eligible) return false;
            int idx = locomotion.ordinal();
            if (taken[idx] >= quota) return false;
            taken[idx]++;
            return true;
        }

        /**
         * The class a fish that stayed behind runs under on its own tank's engine. A class that
         * can move on its own in a lone tank keeps it — a crawler walks its tank's floor, a
         * drifter drifts its own water, a glider that only lost the quota draw still glides.
         * Everything else is pinned {@link Locomotion#STATIC}, which includes the swimmers and
         * gliders the <em>group's</em> size gate turned away: those must not start swimming
         * locally just because this member's own box is individually big enough. That is the
         * asymmetry — the gate is applied in {@link #joins}, so anything reaching here having
         * failed it is already unable to pass its own tank's copy of the same gate.
         */
        static Locomotion stayingHomeAs(Locomotion locomotion) {
            return switch (locomotion) {
                case BENTHIC, DRIFT, GLIDE, ANCHORED -> locomotion;
                default -> Locomotion.STATIC;
            };
        }
    }
}

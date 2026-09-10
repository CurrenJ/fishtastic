package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.client.renderer.TankFlockAdapter.GroupSplit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that splits a member tank's contents between the group engine and its own
 * (docs/fish-sim-locomotion.md §3.5). Worth its own test because the failure it guards against is
 * silent in every cheaper check: {@code rebuildGroupMode} applies this twice over the same slots —
 * once deciding what a tank keeps, once collecting what the group takes — and any disagreement
 * draws a fish twice or not at all.
 */
class GroupSplitTest {

    private static final float GATE_RUN = 10f;
    private static final float GATE_FACTOR = 2.5f;

    private static GroupSplit split(int quota) {
        return new GroupSplit(GATE_RUN, GATE_FACTOR, quota);
    }

    @Test
    @DisplayName("every class with a motion model joins the group")
    void simulatedClassesJoin() {
        GroupSplit s = split(8);
        assertTrue(s.joins(Locomotion.FREE_SWIM, 0.5f));
        assertTrue(s.joins(Locomotion.GLIDE, 0.5f));
        assertTrue(s.joins(Locomotion.BENTHIC, 0.5f));
        assertTrue(s.joins(Locomotion.DRIFT, 0.5f));
    }

    @Test
    @DisplayName("classes with no motion model stay on their own tank")
    void unsimulatedClassesStayHome() {
        GroupSplit s = split(8);
        assertFalse(s.joins(Locomotion.ANCHORED, 0.5f));
        assertFalse(s.joins(Locomotion.STATIC, 0.5f));
    }

    @Test
    @DisplayName("each class gets its own copy of the quota")
    void quotasArePerClass() {
        GroupSplit s = split(1);
        assertTrue(s.joins(Locomotion.FREE_SWIM, 0.5f));
        assertFalse(s.joins(Locomotion.FREE_SWIM, 0.5f), "second swimmer is over quota");
        // A tank full of swimmers must not evict the crab or the jellyfish: they compete for
        // floor area and vertical column, not for the swimmers' water volume.
        assertTrue(s.joins(Locomotion.BENTHIC, 0.5f));
        assertTrue(s.joins(Locomotion.DRIFT, 0.5f));
        assertTrue(s.joins(Locomotion.GLIDE, 0.5f));
        assertFalse(s.joins(Locomotion.BENTHIC, 0.5f));
        assertFalse(s.joins(Locomotion.DRIFT, 0.5f));
        assertFalse(s.joins(Locomotion.GLIDE, 0.5f));
    }

    @Test
    @DisplayName("a rejected fish spends no quota")
    void rejectionDoesNotConsumeQuota() {
        GroupSplit s = split(1);
        assertFalse(s.joins(Locomotion.FREE_SWIM, GATE_RUN), "way over the group's size gate");
        assertTrue(s.joins(Locomotion.FREE_SWIM, 0.5f), "the gate failure must not have eaten the slot");
    }

    @Test
    @DisplayName("only the straight-run classes are size-gated here; the rest are the engine's business")
    void onlySwimmersAreGatedByTheAdapter() {
        float tooLong = GATE_RUN / GATE_FACTOR + 0.1f;
        GroupSplit s = split(8);
        assertFalse(s.joins(Locomotion.FREE_SWIM, tooLong));
        assertFalse(s.joins(Locomotion.GLIDE, tooLong), "a glider shares the swimmer's gate");
        // A crawler too big for the group's floor, or a jellyfish too tall for its water, is
        // demoted by the engine's own gate — but it belongs in group space either way, because
        // that is where the player sees the sand it stands on.
        assertTrue(s.joins(Locomotion.BENTHIC, tooLong));
        assertTrue(s.joins(Locomotion.DRIFT, tooLong));
    }

    @Test
    @DisplayName("a fish that stays behind keeps its class only if it can move in a lone tank")
    void stayingHomeKeepsOnlySelfSufficientClasses() {
        assertEquals(Locomotion.BENTHIC, GroupSplit.stayingHomeAs(Locomotion.BENTHIC));
        assertEquals(Locomotion.DRIFT, GroupSplit.stayingHomeAs(Locomotion.DRIFT));
        assertEquals(Locomotion.GLIDE, GroupSplit.stayingHomeAs(Locomotion.GLIDE));
        // The stay-behind list also holds swimmers the GROUP turned away; they must not start
        // swimming locally just because this member's own box is big enough.
        assertEquals(Locomotion.STATIC, GroupSplit.stayingHomeAs(Locomotion.FREE_SWIM));
        assertEquals(Locomotion.STATIC, GroupSplit.stayingHomeAs(Locomotion.ANCHORED));
        assertEquals(Locomotion.STATIC, GroupSplit.stayingHomeAs(Locomotion.STATIC));
    }

    @Test
    @DisplayName("the two passes agree slot for slot")
    void bothPassesReachTheSameVerdict() {
        // The per-member pass and the anchor's collection pass, run over the same tank in the
        // same order: every slot must land on exactly one side.
        Locomotion[] contents = {
            Locomotion.FREE_SWIM, Locomotion.BENTHIC, Locomotion.FREE_SWIM, Locomotion.DRIFT,
            Locomotion.GLIDE, Locomotion.FREE_SWIM, Locomotion.DRIFT, Locomotion.BENTHIC,
            Locomotion.FREE_SWIM, Locomotion.ANCHORED, Locomotion.GLIDE, Locomotion.GLIDE,
        };
        GroupSplit keeps = split(2);
        GroupSplit takes = split(2);
        int joined = 0;
        for (Locomotion loc : contents) {
            boolean a = keeps.joins(loc, 0.5f);
            boolean b = takes.joins(loc, 0.5f);
            assertEquals(a, b, "the two passes disagreed on a " + loc + " slot");
            if (a) joined++;
        }
        assertEquals(2 + 2 + 2 + 2, joined, "two each of swimmer, glider, crawler and drifter");
    }
}

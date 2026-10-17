package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.TwoOwnerRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-owner harness's contract (docs/fish-companions-party.md §28.3, step 3a-0): each party
 * sees the other as the game shows it (companion points by owner-keyed id within 16 blocks, the
 * other owner's feet within 4), runs are deterministic, and every pair script plays.
 */
class TwoOwnerRunTest {

    private static TwoOwnerRun.Pair pair(String name) {
        return TwoOwnerRun.PAIRS.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void pointIdsAreTheGamesAndNeverCollide() {
        // ClientCompanions.pointId: -1 - (owner entity id * party size + lowest slot).
        assertEquals(-1 - (TwoOwnerRun.OWNER_A * 3 + 0), TwoOwnerRun.pointId(TwoOwnerRun.OWNER_A, 0));
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (int owner : new int[]{TwoOwnerRun.OWNER_A, TwoOwnerRun.OWNER_B}) {
            for (int slot = 0; slot < PartyGrouping.SLOTS; slot++) {
                assertTrue(ids.add(TwoOwnerRun.pointId(owner, slot)), "duplicate id");
            }
        }
    }

    @Test
    void closeOwnersSeeEachOthersFishAndFeet() {
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair("two free swimmers"), "pair_close", 1,
                CompanionTunables.defaults(), Temperament.BASELINE);
        boolean fish = false, feet = false;
        for (int k = 0; k < 200; k++) {
            sim.step();
            for (PointOfInterest p : sim.a.seen(0).points()) {
                if (p.kind() == PointOfInterest.Kind.COMPANION
                        && p.id() == TwoOwnerRun.pointId(TwoOwnerRun.OWNER_B, 0)) fish = true;
                if (p.kind() == PointOfInterest.Kind.PLAYER && p.id() == TwoOwnerRun.OWNER_B) {
                    feet = true;
                    assertEquals(sim.b.owner().feet().distanceTo(p.pos()), 0, 1e-9, "feet, not centre");
                }
            }
        }
        assertTrue(fish, "A never saw B's fish");
        assertTrue(feet, "A never saw B's feet");
    }

    @Test
    void ownersSixBlocksApartSeeFishButNotFeet() {
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair("two free swimmers"), "pair_far", 1,
                CompanionTunables.defaults(), Temperament.BASELINE);
        boolean fish = false;
        for (int k = 0; k < 200; k++) {
            sim.step();
            for (PointOfInterest p : sim.a.seen(0).points()) {
                assertNotEquals(PointOfInterest.Kind.PLAYER, p.kind(), "B's feet seen from 6 blocks");
                if (p.kind() == PointOfInterest.Kind.COMPANION) fish = true;
            }
        }
        assertTrue(fish, "A never saw B's fish from 6 blocks");
    }

    @Test
    void aPartyNeverSeesItsOwnFishAsTheOthers() {
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair("mixed vs mixed"), "pair_close", 2,
                CompanionTunables.defaults(), Temperament.BASELINE);
        for (int k = 0; k < 200; k++) {
            sim.step();
            for (int i = 0; i < sim.a.layer.size(); i++) {
                for (PointOfInterest p : sim.a.seen(i).points()) {
                    if (p.kind() != PointOfInterest.Kind.COMPANION) continue;
                    for (int slot = 0; slot < PartyGrouping.SLOTS; slot++) {
                        assertNotEquals(TwoOwnerRun.pointId(TwoOwnerRun.OWNER_A, slot), p.id(),
                                "A's own fish arrived as the other party's");
                    }
                }
            }
        }
    }

    @Test
    void runsAreDeterministic() {
        TwoOwnerRun.Pair p = pair("school vs solo");
        assertEquals(TwoOwnerFingerprintProbeHash.of(p, "pair_approach_stand", 4, 600),
                TwoOwnerFingerprintProbeHash.of(p, "pair_approach_stand", 4, 600));
    }

    @Test
    void everyPairScriptPlaysAndBMeetsA() {
        for (String script : OwnerScript.PAIR_NAMES) {
            TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair("two free swimmers"), script, 1,
                    CompanionTunables.defaults(), Temperament.BASELINE);
            double closest = Double.MAX_VALUE;
            for (int k = 0; k < 600; k++) {
                sim.step();
                closest = Math.min(closest, sim.a.owner().feet().distanceTo(sim.b.owner().feet()));
            }
            assertTrue(closest <= 6.01, script + ": owners never within 6 blocks (closest " + closest + ")");
            assertFalse(Double.isNaN(sim.a.layer.unit(0).leader().position().x()), script + ": NaN");
        }
    }

    /** The probe's hash over a shorter run, for the determinism check. */
    static final class TwoOwnerFingerprintProbeHash {
        static long of(TwoOwnerRun.Pair pair, String script, long seed, int ticks) {
            TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair, script, seed, CompanionTunables.defaults(),
                    Temperament.BASELINE);
            long h = 17;
            for (int k = 0; k < ticks; k++) {
                sim.step();
                for (double[] body : sim.a.bodies()) for (double v : body) h = 31 * h + Double.doubleToLongBits(v);
                for (double[] body : sim.b.bodies()) for (double v : body) h = 31 * h + Double.doubleToLongBits(v);
            }
            return h;
        }
    }
}

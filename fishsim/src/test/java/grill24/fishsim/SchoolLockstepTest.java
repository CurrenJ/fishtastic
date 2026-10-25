package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.WaterDomain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interactive fish schools run one {@link FlockEngine} on the server and a replica on every
 * watching client. The replica only stays honest if the engine is a pure function of what both
 * sides share — the build, the step count and the same inputs at the same step — so this pins
 * exactly that: two engines fed identically stay bitwise equal for a whole visit, including the
 * membership changes a visit makes (a member leaving for a session, returning at the hook).
 *
 * <p>A client that arrives mid-visit replays from the visit's start, so the cost of a full-visit
 * replay is reported too.
 */
class SchoolLockstepTest {

    /** {@code schools.activeLifetimeTicks}' default. */
    private static final int VISIT_STEPS = 12_000;
    private static final int BED = 58;
    private static final float SURFACE = 8f / 9f;

    /** A school region at its cap: 8 × 8 columns, 4 deep = 256 cells. */
    private static WaterDomain region() {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                cols.add(new WaterDomain.Column(100 + x, 200 + z, new float[]{BED, BED + 3 + SURFACE}, false));
            }
        }
        WaterDomain d = WaterDomain.build(cols, 100, BED - 1, 200, 0.35f, null);
        assertNotNull(d);
        return d;
    }

    private static FishSpec[] school(Locomotion loco, float length, int members) {
        FishSpec[] specs = new FishSpec[members];
        for (int i = 0; i < members; i++) specs[i] = new FishSpec(length, loco, (i & 1) == 0, 7);
        return specs;
    }

    /** One side of the lockstep: build, then the visit's inputs at fixed steps. */
    private static final class Side {
        final FlockEngine engine = new FlockEngine(Tunables.GROUP);
        final WaterDomain domain = region();
        FishSpec[] specs;
        boolean sent;
        int touches;

        Side(FishSpec[] specs) {
            this.specs = specs;
            engine.rebuild(specs, 0x5C001L, 0f, 20f, domain);
        }

        void step(int t) {
            if (t == 2_000) {
                // A bobber lands and member 3 is sent to it; it is still on its way when member 1
                // leaves below, so the errand is renumbered mid-swim.
                domain.rebuildShelters(List.of(Shelter.noseAnchor(1.5f, 2.5f, 1.5f,
                        1.5f - engine.posL()[3], 1.5f - engine.posD()[3], 4f, "bobber")));
                sent = engine.sendToTrigger(0, 3);
            } else if (t == 6_000) {
                // Its player reels in.
                domain.rebuildShelters(List.of());
            }
            if (t == 3_000) {
                // Member 1 enters a session: it leaves the engine, the rest carry over.
                FishSpec[] next = new FishSpec[specs.length - 1];
                int[] carry = new int[next.length];
                for (int i = 0, j = 0; i < specs.length; i++) {
                    if (i == 1) continue;
                    next[j] = specs[i];
                    carry[j++] = i;
                }
                specs = next;
                engine.rebuildPreserving(specs, carry, 0x5C001L, 0f, 20f, domain);
            } else if (t == 5_000) {
                // It comes back, placed at the hook.
                FishSpec[] next = new FishSpec[specs.length + 1];
                int[] carry = new int[next.length];
                for (int i = 0; i < specs.length; i++) {
                    next[i] = specs[i];
                    carry[i] = i;
                }
                next[specs.length] = specs[0];
                carry[specs.length] = -1;
                specs = next;
                engine.rebuildPreserving(specs, carry, 0x5C001L, 0f, 20f, domain);
                engine.placeFish(specs.length - 1, 3.5f, 2.5f, 4.5f);
            }
            engine.step();
            engine.drainTriggerEvents(e -> { if (e.start()) touches++; });
        }
    }

    private static void assertLockstep(Locomotion loco, float length) {
        assertLockstep(loco, length, -1);
    }

    /** {@code nudgeAt} >= 0 disturbs the client once by a hair, to prove divergence is caught. */
    private static void assertLockstep(Locomotion loco, float length, int nudgeAt) {
        Side server = new Side(school(loco, length, 8));
        Side client = new Side(school(loco, length, 8));
        assertEquals(loco, server.engine.locomotion[0], "the region must host this class, or the test is vacuous");
        double travelled = 0;
        for (int t = 0; t < VISIT_STEPS; t++) {
            server.step(t);
            client.step(t);
            if (t == nudgeAt) {
                FlockEngine c = client.engine;
                c.placeFish(0, c.posL()[0] + 1e-4f, c.posY()[0], c.posD()[0]);
            }
            for (int i = 0; i < server.engine.count(); i++) {
                travelled += Math.abs(server.engine.velL()[i]) + Math.abs(server.engine.velY()[i])
                        + Math.abs(server.engine.velD()[i]);
            }
            FlockEngine a = server.engine, b = client.engine;
            assertEquals(a.count(), b.count());
            for (int i = 0; i < a.count(); i++) {
                if (a.posL()[i] != b.posL()[i] || a.posY()[i] != b.posY()[i] || a.posD()[i] != b.posD()[i]
                        || a.velL()[i] != b.velL()[i] || a.velY()[i] != b.velY()[i] || a.velD()[i] != b.velD()[i]
                        || a.yawDeg[i] != b.yawDeg[i]) {
                    throw new AssertionError(loco + ": replica diverged at step " + t + ", fish " + i);
                }
            }
        }
        assertEquals(server.touches, client.touches);
        if (loco == Locomotion.FREE_SWIM && nudgeAt < 0) {
            assertTrue(server.sent && server.touches == 1, "the bobber errand must run, or the lockstep never covered it");
        }
        System.out.printf("[school-lockstep] %s: summed |vel| %.0f over the visit, bobber sent %s, touches %d%n",
                loco, travelled, server.sent, server.touches);
        assertTrue(travelled > 100, loco + " barely moved; the lockstep proves nothing");
    }

    @Test
    void aOneOffNudgeIsCaught() {
        AssertionError e = assertThrows(AssertionError.class,
                () -> assertLockstep(Locomotion.FREE_SWIM, 0.3f, 100));
        assertTrue(e.getMessage().contains("diverged"), e.getMessage());
    }

    @Test void freeSwimmersStayInLockstep() { assertLockstep(Locomotion.FREE_SWIM, 0.3f); }
    @Test void glidersStayInLockstep() { assertLockstep(Locomotion.GLIDE, 0.5f); }
    @Test void driftersStayInLockstep() { assertLockstep(Locomotion.DRIFT, 0.3f); }
    @Test void crawlersStayInLockstep() { assertLockstep(Locomotion.BENTHIC, 0.3f); }

    @Test
    void aFullVisitReplaysQuickly() {
        for (int warm = 0; warm < 3; warm++) {
            Side s = new Side(school(Locomotion.FREE_SWIM, 0.3f, 8));
            for (int t = 0; t < VISIT_STEPS; t++) s.step(t);
        }
        long start = System.nanoTime();
        Side s = new Side(school(Locomotion.FREE_SWIM, 0.3f, 8));
        for (int t = 0; t < VISIT_STEPS; t++) s.step(t);
        double ms = (System.nanoTime() - start) / 1e6;
        System.out.printf("[school-lockstep] full %d-step replay, 8 fish: %.1f ms (%.2f us/step)%n",
                VISIT_STEPS, ms, ms * 1000.0 / VISIT_STEPS);
    }

    /**
     * Math.sin/cos/atan2 may differ by an ulp between platforms, so a replica on another machine
     * can start one ulp apart. Reports how long such a difference takes to grow to a visible gap.
     */
    @Test
    void anUlpOfDifferenceGrowsSlowly() {
        for (Locomotion loco : new Locomotion[]{Locomotion.FREE_SWIM, Locomotion.GLIDE, Locomotion.DRIFT}) {
            Side server = new Side(school(loco, loco == Locomotion.GLIDE ? 0.5f : 0.3f, 8));
            Side client = new Side(school(loco, loco == Locomotion.GLIDE ? 0.5f : 0.3f, 8));
            int to1cm = -1, to10cm = -1, to50cm = -1;
            for (int t = 0; t < VISIT_STEPS; t++) {
                server.step(t);
                client.step(t);
                if (t == 100) {
                    FlockEngine c = client.engine;
                    c.placeFish(0, Math.nextUp(c.posL()[0]), c.posY()[0], c.posD()[0]);
                }
                float worst = 0;
                for (int i = 0; i < server.engine.count(); i++) {
                    float dl = server.engine.posL()[i] - client.engine.posL()[i];
                    float dy = server.engine.posY()[i] - client.engine.posY()[i];
                    float dd = server.engine.posD()[i] - client.engine.posD()[i];
                    worst = Math.max(worst, (float) Math.sqrt(dl * dl + dy * dy + dd * dd));
                }
                if (to1cm < 0 && worst > 0.01f) to1cm = t - 100;
                if (to10cm < 0 && worst > 0.1f) to10cm = t - 100;
                if (to50cm < 0 && worst > 0.5f) to50cm = t - 100;
            }
            System.out.printf("[school-lockstep] %s ulp nudge -> 1cm after %d, 10cm after %d, 50cm after %d steps%n",
                    loco, to1cm, to10cm, to50cm);
        }
    }
}

package grill24.fishsim;

import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sprite in a shelter's hollow stays clear of its floor and roof (docs/fish-shelters.md §5.7).
 * The engine keeps the fish's centre in a box shortened by its sprite's half-height, and reports
 * the room left over as {@code renderBobRoom}, which the renderer caps its bob at. In game, a
 * loach bobbing at the default ±0.125 inside the Hollow Log's 0.18-tall bore went through both.
 */
class ShelterBobRoomTest {

    private static final int TICKS = 6_000;
    /** Sprite height to length — the engine's mouth gate (§5.2). */
    private static final float HEIGHT_RATIO = 0.4f;
    /** The default swimmer's bob amplitude, {@code FishAnimationConfig.HorizontalSwim}. */
    private static final float DEFAULT_BOB = 0.125f;

    private record Config(String domain, String cast, long seed) {
        String name() {
            return domain + " " + cast + " seed=" + seed;
        }
    }

    @TestFactory
    List<DynamicTest> aBobbingSpriteStaysInsideTheHollow() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String domain : new String[]{"3x1x1+log", "4x1x2+log"}) {
            for (String cast : new String[]{"loaches", "skittish", "lurker"}) {
                for (long seed : new long[]{12345L, -987654321L}) {
                    Config c = new Config(domain, cast, seed);
                    tests.add(DynamicTest.dynamicTest(c.name(), () -> run(c)));
                }
            }
        }
        return tests;
    }

    private static void run(Config c) {
        VoxelDomain domain = Scenarios.domain(c.domain());
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(Scenarios.cast(c.cast(), 8, c.seed()), c.seed(), 30f, 20f, domain);
        List<Shelter> shelters = domain.shelters();
        int n = engine.count();
        float[] lastRoom = new float[n];
        float worstOvershoot = 0f, worstStep = 0f, worstRoamRatio = 0f;
        String stepWhere = null;
        float[] lastY = new float[n], lastL = new float[n], lastD = new float[n];
        int insideTicks = 0;
        String first = null;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            engine.interpolate(1f);
            for (int i = 0; i < n; i++) {
                float l = engine.posL()[i], y = engine.posY()[i], d = engine.posD()[i];
                float room = engine.renderBobRoom[i];
                float half = 0.5f * HEIGHT_RATIO * engine.lengths[i];
                for (Shelter s : shelters) {
                    Shelter.OrientedBox box = s.interior();
                    if (!box.contains(l, y, d)) continue;
                    insideTicks++;
                    // The sprite at the top and bottom of its capped bob, against the bore.
                    float reach = Math.abs(y - box.centerY()) + half + Math.min(DEFAULT_BOB, room);
                    float overshoot = reach - box.halfY();
                    if (overshoot > worstOvershoot) {
                        worstOvershoot = overshoot;
                        first = "fish " + i + " tick " + tick + " state " + engine.shelterState(i)
                                + " off-centre " + Math.abs(y - box.centerY()) + " half-height " + half
                                + " room " + room + " bore half " + box.halfY();
                    }
                }
                // The cap follows position, so it never jumps: an amplitude that drops faster than
                // the bob itself moves reads as the fish ducking.
                if (tick > 0 && room < DEFAULT_BOB && lastRoom[i] < DEFAULT_BOB) {
                    float step = Math.abs(room - lastRoom[i]);
                    if (engine.shelterState(i) == FlockEngine.SHELTER_ROAMING) {
                        float dl = l - lastL[i], dy = y - lastY[i], dd = d - lastD[i];
                        float moved = (float) Math.sqrt(dl * dl + dy * dy + dd * dd);
                        worstRoamRatio = Math.max(worstRoamRatio, step / Math.max(moved, 1e-4f));
                    } else if (step > worstStep) {
                        worstStep = step;
                        float dl = l - lastL[i], dy = y - lastY[i], dd = d - lastD[i];
                        stepWhere = "fish " + i + " state " + engine.shelterState(i) + " room " + lastRoom[i] + "->" + room
                                + " dy " + dy + " dh " + (float) Math.sqrt(dl * dl + dd * dd);
                    }
                }
                lastL[i] = l; lastY[i] = y; lastD[i] = d;
                lastRoom[i] = room;
            }
        }
        System.out.printf("%s: %d fish-ticks in a hollow, worst overshoot %.4f, worst visiting room step %.5f/tick (%s), worst roaming room/move %.2f%n",
                c.name(), insideTicks, worstOvershoot, worstStep, stepWhere, worstRoamRatio);
        assertTrue(insideTicks > 0, "nobody went in, so nothing was tested");
        assertTrue(worstOvershoot <= 0.001f,"sprite reached " + worstOvershoot + " past the bore; " + first);
        // Measured 2026-10-02: 0.0027/tick entering (the default bob's own peak is 0.0031/tick),
        // 0.0055 on an approach, which swims in faster; roaming past the log, the room moves at
        // most 1.04x as far as the fish does.
        assertTrue(worstStep <= 0.0065f, "bob room stepped " + worstStep + " in one tick; " + stepWhere);
        assertTrue(worstRoamRatio <= 1.5f, "a roaming fish's bob room moved " + worstRoamRatio + "x as far as it did");
    }

    /** With no shelter in the domain nothing is capped: the bob is exactly what it was. */
    @Test
    void noShelterMeansNoCap() {
        VoxelDomain domain = Scenarios.domain("3x1x1");
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(Scenarios.cast("loaches", 8, 3L), 3L, 0f, 20f, domain);
        for (int tick = 0; tick < 200; tick++) {
            engine.step();
            engine.interpolate(0.5f);
            for (int i = 0; i < engine.count(); i++) {
                assertEquals(Float.POSITIVE_INFINITY, engine.renderBobRoom[i], "fish " + i + " capped");
            }
        }
    }
}

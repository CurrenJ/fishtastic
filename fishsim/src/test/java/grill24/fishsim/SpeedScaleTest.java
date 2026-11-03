package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.WaterDomain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FishSpec#speedScale} (docs/ambient-fish-schools-design-spec.md §5.2): 1 leaves the motion
 * bit-identical, and a scale moves a fish proportionally faster through the same water.
 */
class SpeedScaleTest {

    private static WaterDomain pond() {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 24; z++) cols.add(new WaterDomain.Column(x, z, new float[]{62, 69.9f}, false));
        }
        return WaterDomain.build(cols, 0, 61, 0, 0.35f, null);
    }

    private static FishSpec[] school(float scale) {
        FishSpec[] specs = new FishSpec[6];
        for (int i = 0; i < specs.length; i++) {
            specs[i] = new FishSpec(0.3f, Locomotion.FREE_SWIM, (i & 1) == 0, 1).withSpeedScale(scale);
        }
        return specs;
    }

    /** Mean speed over a run, from position deltas. */
    private static double meanSpeed(Tunables tunables, float scale) {
        FlockEngine engine = new FlockEngine(tunables);
        engine.rebuild(school(scale), 11L, 0f, 20f, pond());
        for (int t = 0; t < 100; t++) engine.step();
        double sum = 0;
        int n = 0;
        for (int t = 0; t < 600; t++) {
            float[] l = engine.posL().clone(), y = engine.posY().clone(), d = engine.posD().clone();
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                double dl = engine.posL()[i] - l[i], dy = engine.posY()[i] - y[i], dd = engine.posD()[i] - d[i];
                sum += Math.sqrt(dl * dl + dy * dy + dd * dd) * 20.0;
                n++;
            }
        }
        return sum / n;
    }

    @Test
    void unitScaleIsBitIdentical() {
        FishSpec plain = new FishSpec(0.3f, Locomotion.FREE_SWIM, false, 1);
        assertEquals(1f, plain.speedScale());
        FlockEngine a = new FlockEngine(Tunables.GROUP), b = new FlockEngine(Tunables.GROUP);
        FishSpec[] withDefault = new FishSpec[6], withOne = school(1f);
        for (int i = 0; i < 6; i++) withDefault[i] = new FishSpec(0.3f, Locomotion.FREE_SWIM, (i & 1) == 0, 1);
        a.rebuild(withDefault, 11L, 0f, 20f, pond());
        b.rebuild(withOne, 11L, 0f, 20f, pond());
        for (int t = 0; t < 300; t++) {
            a.step();
            b.step();
        }
        assertArrayEquals(a.posL(), b.posL());
        assertArrayEquals(a.posD(), b.posD());
    }

    @Test
    void aScaleMovesTheFishProportionallyFaster() {
        double base = meanSpeed(Tunables.OPEN_WATER, 1f);
        double fast = meanSpeed(Tunables.OPEN_WATER, 1.5f);
        double slow = meanSpeed(Tunables.OPEN_WATER, 0.6f);
        assertTrue(fast > base * 1.3 && fast < base * 1.7, "fast " + fast + " vs " + base);
        assertTrue(slow < base * 0.75 && slow > base * 0.45, "slow " + slow + " vs " + base);
    }

    @Test
    void theScaleIsClamped() {
        assertEquals(FishSpec.MAX_SPEED_SCALE, new FishSpec(0.3f, Locomotion.FREE_SWIM, false, 1).withSpeedScale(9f).speedScale());
        assertEquals(FishSpec.MIN_SPEED_SCALE, new FishSpec(0.3f, Locomotion.FREE_SWIM, false, 1).withSpeedScale(0.01f).speedScale());
        assertEquals(1f, new FishSpec(0.3f, Locomotion.FREE_SWIM, false, 1).withSpeedScale(Float.NaN).speedScale());
    }
}

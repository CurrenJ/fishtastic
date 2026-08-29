package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code rebuildPreserving} — the fix for "tank contents changing teleports every fish".
 *
 * <p>A tank rebuilds its flock whenever its contents change, and the plain {@code rebuild} re-seeds
 * every fish from its index: taking one fish out shifted every later fish's index and therefore its
 * scatter position, seed and rotation, so the whole tank jumped. These tests pin the two halves of
 * the fix — surviving fish are bit-for-bit untouched, and genuinely new fish still get a fresh,
 * non-overlapping placement.
 */
class RebuildCarryTest {

    private static final long SEED = 12345L;

    private static FishSpec[] specs(int n, long seed) {
        Random r = new Random(seed * 31 + n);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.06f + r.nextFloat() * 0.2f, true, r.nextBoolean(), i % 3);
        }
        return specs;
    }

    private static FlockEngine settled(FishSpec[] specs, int ticks) {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, SEED, 30f, 3, 0.35f, 0.3f, 20f);
        for (int t = 0; t < ticks; t++) engine.step();
        return engine;
    }

    /** Identity map for "fish {@code removed} was taken out; everyone else stayed". */
    private static int[] carryAfterRemoval(int oldCount, int removed) {
        int[] carry = new int[oldCount - 1];
        for (int i = 0, j = 0; i < oldCount; i++) {
            if (i == removed) continue;
            carry[j++] = i;
        }
        return carry;
    }

    private static FishSpec[] without(FishSpec[] specs, int removed) {
        FishSpec[] out = new FishSpec[specs.length - 1];
        for (int i = 0, j = 0; i < specs.length; i++) {
            if (i == removed) continue;
            out[j++] = specs[i];
        }
        return out;
    }

    @Test
    void removingOneFishLeavesEveryOtherFishExactlyWhereItWas() {
        FishSpec[] specs = specs(8, SEED);
        FlockEngine engine = settled(specs, 400);

        int removed = 3;
        float[] beforeL = new float[8], beforeY = new float[8], beforeD = new float[8];
        float[] beforeVelL = new float[8];
        long[] beforeSeeds = new long[8];
        float[] beforeRot = new float[8];
        for (int i = 0; i < 8; i++) {
            beforeL[i] = engine.posL()[i];
            beforeY[i] = engine.posY()[i];
            beforeD[i] = engine.posD()[i];
            beforeVelL[i] = engine.velL()[i];
            beforeSeeds[i] = engine.seeds[i];
            beforeRot[i] = engine.baseRotations[i];
        }

        engine.rebuildPreserving(without(specs, removed), carryAfterRemoval(8, removed),
                SEED, 30f, 3, 0.35f, 0.3f, 20f);

        assertEquals(7, engine.count());
        for (int i = 0, j = 0; i < 8; i++) {
            if (i == removed) continue;
            String at = "fish " + i + " -> " + j;
            assertEquals(beforeL[i], engine.posL()[j], 0f, at + " lateral");
            assertEquals(beforeY[i], engine.posY()[j], 0f, at + " vertical");
            assertEquals(beforeD[i], engine.posD()[j], 0f, at + " depth");
            assertEquals(beforeVelL[i], engine.velL()[j], 0f, at + " velocity");
            assertEquals(beforeSeeds[i], engine.seeds[j], at + " animation seed");
            assertEquals(beforeRot[i], engine.baseRotations[j], 0f, at + " base rotation");
            j++;
        }
    }

    /** The regression this exists to prevent: the plain rebuild moves everybody. */
    @Test
    void plainRebuildStillRescattersEveryone() {
        FishSpec[] specs = specs(8, SEED);
        FlockEngine engine = settled(specs, 400);

        int removed = 3;
        float before = engine.posL()[4];
        engine.rebuild(without(specs, removed), SEED, 30f, 3, 0.35f, 0.3f, 20f);

        assertNotEquals(before, engine.posL()[3], "index-seeded rebuild should still re-scatter");
    }

    @Test
    void addedFishGetsAFreshPlacementClearOfTheResidents() {
        FishSpec[] specs = specs(5, SEED);
        FlockEngine engine = settled(specs, 400);

        FishSpec[] grown = new FishSpec[6];
        System.arraycopy(specs, 0, grown, 0, 5);
        grown[5] = new FishSpec(0.12f, true, false, 0);

        float[] beforeL = engine.posL().clone();
        float[] beforeY = engine.posY().clone();
        float[] beforeD = engine.posD().clone();

        // The newcomer lands at the end, the way the adapter appends a newly filled slot.
        engine.rebuildPreserving(grown, new int[]{0, 1, 2, 3, 4, -1}, SEED, 30f, 3, 0.35f, 0.3f, 20f);

        assertEquals(6, engine.count());
        for (int i = 0; i < 5; i++) {
            assertEquals(beforeL[i], engine.posL()[i], 0f, "resident " + i + " lateral");
            assertEquals(beforeY[i], engine.posY()[i], 0f, "resident " + i + " vertical");
            assertEquals(beforeD[i], engine.posD()[i], 0f, "resident " + i + " depth");
        }
        // Residents are registered with the placement scratch before the scatter runs, so the
        // newcomer is rejection-sampled away from them rather than popping in on top of one.
        for (int i = 0; i < 5; i++) {
            float dl = engine.posL()[5] - engine.posL()[i];
            float dy = engine.posY()[5] - engine.posY()[i];
            float dd = engine.posD()[5] - engine.posD()[i];
            assertTrue(dl * dl + dy * dy + dd * dd > 1e-6f, "newcomer landed on resident " + i);
        }
    }

    @Test
    void carryFromIsBoundsCheckedAgainstAShrunkFlock() {
        FishSpec[] specs = specs(3, SEED);
        FlockEngine engine = settled(specs, 100);

        // Indices past the previous count (and negatives) must scatter, not read another fish.
        engine.rebuildPreserving(specs(3, SEED), new int[]{-1, 99, Integer.MIN_VALUE},
                SEED, 30f, 3, 0.35f, 0.3f, 20f);

        assertEquals(3, engine.count());
        for (int i = 0; i < 3; i++) {
            assertTrue(Float.isFinite(engine.posL()[i]) && Float.isFinite(engine.posY()[i]));
        }
    }

    @Test
    void carryOverAppliesToThePlanarVoxelPathToo() {
        boolean[][][] occupancy = new boolean[3][1][1];
        for (int x = 0; x < 3; x++) occupancy[x][0][0] = true;
        VoxelDomain domain = new VoxelDomain(occupancy);

        FishSpec[] specs = specs(6, SEED);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, SEED, 0f, 20f, domain);
        for (int t = 0; t < 400; t++) engine.step();

        int removed = 2;
        float[] beforeL = engine.posL().clone();
        float[] beforeYaw = engine.yawDeg.clone();

        engine.rebuildPreserving(without(specs, removed), carryAfterRemoval(6, removed),
                SEED, 0f, 20f, domain);

        assertEquals(5, engine.count());
        for (int i = 0, j = 0; i < 6; i++) {
            if (i == removed) continue;
            assertEquals(beforeL[i], engine.posL()[j], 0f, "planar fish " + i + " lateral");
            assertEquals(beforeYaw[i], engine.yawDeg[j], 0f, "planar fish " + i + " yaw");
            j++;
        }
    }

    /** A null carry map has to leave the locked scatter untouched — the parity suite's contract. */
    @Test
    void rebuildPreservingWithNoCarriedFishMatchesAPlainRebuild() {
        FishSpec[] specs = specs(7, SEED);

        FlockEngine plain = new FlockEngine(Tunables.DEFAULT);
        plain.rebuild(specs, SEED, 30f, 3, 0.35f, 0.3f, 20f);

        FlockEngine preserved = new FlockEngine(Tunables.DEFAULT);
        preserved.rebuildPreserving(specs, new int[]{-1, -1, -1, -1, -1, -1, -1},
                SEED, 30f, 3, 0.35f, 0.3f, 20f);

        assertArrayEquals(plain.posL(), preserved.posL(), 0f);
        assertArrayEquals(plain.posY(), preserved.posY(), 0f);
        assertArrayEquals(plain.posD(), preserved.posD(), 0f);
        assertArrayEquals(plain.baseRotations, preserved.baseRotations, 0f);
        assertArrayEquals(plain.seeds, preserved.seeds);
    }
}

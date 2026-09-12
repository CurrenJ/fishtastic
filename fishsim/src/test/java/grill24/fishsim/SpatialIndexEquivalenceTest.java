package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The specification for {@link FlockEngine}'s uniform-grid spatial index
 * (docs/fish-tank-group-scaling.md §5.2): it must be a <em>pure</em> optimisation.
 *
 * <p>{@link VoxelGoldenTrajectoryTest} already guards against drift, but its scenario is 12 fish
 * in a small L — at that size essentially every fish is a candidate, so the fixture passing does
 * not prove the pruning is sound. These cases run the same seeds at densities and domain sizes
 * where the index actually discards most of the domain, and compare raw float bits against the
 * brute-force scan.
 *
 * <p>The failure this exists to catch is a candidate radius that is too small. Verified to have
 * teeth: shrinking the radius to 0.75 × {@code neighborRange} fails every case below on tick 1.
 *
 * <p>One caveat worth recording. The shipped radius carries a margin above {@code neighborRange}
 * for the Tier 2 anticipatory separation term, which is not distance-guarded and can in principle
 * fire out to
 * {@code separationRadiusOther + 2 * maxSpeed * (1 + traitJitter) * separationLookahead} ≈ 1.05.
 * That margin is a <em>proven bound</em>, not an empirically exercised one: sizing the grid to
 * exactly {@code neighborRange} (0.9) also passes every case here, because reaching past 0.9
 * needs two fish closing head-on at very near the speed cap, which these scenarios never sustain.
 * Keep the derived bound anyway — it is what makes the index correct for tunables the suite does
 * not cover, and the cost is one slightly larger cell.
 */
class SpatialIndexEquivalenceTest {

    private static final int TICKS = 600;

    @Test
    void cube4At256FishMatchesBruteForce() {
        assertIdentical(VoxelDomainTest.fullGrid(4, 4, 4), 256, 11L);
    }

    @Test
    void cube6At864FishMatchesBruteForce() {
        assertIdentical(VoxelDomainTest.fullGrid(6, 6, 6), 864, 22L);
    }

    @Test
    void sparseCube8MatchesBruteForce() {
        // 1 fish/block — the low-density end, where the candidate list is nearly empty and an
        // off-by-one in the cell range would be easiest to miss.
        assertIdentical(VoxelDomainTest.fullGrid(8, 8, 8), 512, 33L);
    }

    @Test
    void concaveLDomainMatchesBruteForce() {
        // Concave seams are where the domain is least like its bounding box, so the grid holds
        // large empty regions and fish cluster into a few cells.
        assertIdentical(VoxelDomainTest.lShape(), 120, 44L);
    }

    @Test
    void flatSlabMatchesBruteForce() {
        // One block tall: ny collapses to a single cell, so the vertical axis exercises the
        // clamped-degenerate path rather than a real range.
        assertIdentical(VoxelDomainTest.fullGrid(10, 1, 4), 200, 55L);
    }

    @Test
    void longCorridorMatchesBruteForce() {
        // A 16-block run is the closest the model gets to sustained max-speed head-on traffic,
        // which is the regime the anticipatory separation term reaches farthest in — the case the
        // candidate radius has to be sized for rather than for neighborRange alone.
        assertIdentical(VoxelDomainTest.fullGrid(16, 1, 1), 64, 66L);
    }

    private static void assertIdentical(boolean[][][] occupancy, int fishCount, long seed) {
        FishSpec[] specs = specs(fishCount, seed);

        FlockEngine brute = new FlockEngine(Tunables.GROUP);
        brute.setSpatialIndexEnabled(false);
        brute.rebuild(specs, seed, 0f, 20f, new VoxelDomain(occupancy));

        FlockEngine indexed = new FlockEngine(Tunables.GROUP);
        indexed.rebuild(specs, seed, 0f, 20f, new VoxelDomain(occupancy));

        for (int tick = 1; tick <= TICKS; tick++) {
            brute.step();
            indexed.step();
            for (int i = 0; i < fishCount; i++) {
                assertBits(brute.posL()[i], indexed.posL()[i], tick, i, "posL");
                assertBits(brute.posY()[i], indexed.posY()[i], tick, i, "posY");
                assertBits(brute.posD()[i], indexed.posD()[i], tick, i, "posD");
                assertBits(brute.velL()[i], indexed.velL()[i], tick, i, "velL");
                assertBits(brute.velY()[i], indexed.velY()[i], tick, i, "velY");
                assertBits(brute.velD()[i], indexed.velD()[i], tick, i, "velD");
            }
        }
        assertEquals(0L, indexed.backstopEngagements(), "soft containment must stay soft");
    }

    private static void assertBits(float expected, float actual, int tick, int fish, String what) {
        if (Float.floatToRawIntBits(expected) == Float.floatToRawIntBits(actual)) return;
        assertEquals(
                Integer.toHexString(Float.floatToRawIntBits(expected)),
                Integer.toHexString(Float.floatToRawIntBits(actual)),
                "tick " + tick + " fish " + fish + " " + what + " (brute force vs spatial index)");
    }

    /** A mixed-species, mixed-length population — species drives both radii and the schooling filter. */
    private static FishSpec[] specs(int n, long seed) {
        Random rng = new Random(seed);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(
                    0.07f + rng.nextFloat() * 0.20f,
                    // ~10% unsimulated hover obstacles (separation still sees them)
                    rng.nextInt(10) > 0 ? Locomotion.FREE_SWIM : Locomotion.STATIC,
                    rng.nextBoolean(),
                    rng.nextInt(3));
        }
        return specs;
    }
}

package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Metrics;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 6's gate (docs/fish-sim-engine-handoff.md): the voxel occupancy domain — invariants 1–6 on
 * the multi-tank domain matrix, invariant 7 (L-domain: both arms visited, concave corner never
 * penetrated, no stuck fish), and 1-voxel parity with the legacy Box geometry. Thresholds
 * calibrated the same way as {@link InvariantTest}'s (measured 2026-08-22, then tightened; voxel
 * shoals spread wider than the single box, hence the looser NN ceiling).
 */
class VoxelDomainTest {

    private static final float SPEED_TOLERANCE = 1.001f;
    // Finite-difference accel can slightly exceed maxForce: vertical damping and the speed clamp
    // add to |Δv| on top of the force-capped steering. Darting would exceed this by multiples.
    private static final float ACCEL_TOLERANCE = 1.06f; // (1.029 observed)
    private static final float MAX_JERK = 12f;                        // (8.06)
    private static final double MAX_NEAR_WALL_VELY_VARIANCE = 1.5e-3; // (8.6e-4)
    // GROUP tunes for loose patrolling shoals, so the acceptable NN band is wider than the
    // single-box set's; the ceiling still catches true scattering. (observed 0.16 .. 0.43)
    private static final double NN_MEAN_MIN = 0.08, NN_MEAN_MAX = 0.55;
    // Raised 2026-09-10 with anticipatory separation (Tunables.separationLookahead): repelling
    // from the PREDICTED point of closest approach rather than only the present one gives a fish
    // room to resolve a head-on pass by turning instead of by shoving, and the crowded 1x1x1 case
    // that set the previous floor went from ~0.037 to 0.072 — while separationSpeed came DOWN from
    // 0.40 to 0.25. Floor kept at roughly half the observed worst, as before.
    //
    // This is a real improvement in the model, not a threshold moved to make a test pass; the
    // reverse move (raising a bound because behaviour got worse) is never the right fix here — see
    // docs/fish-swarm-tier2-handoff.md on the backstop invariant.
    private static final float MIN_PAIRWISE_FLOOR = 0.035f;            // (0.072)
    /**
     * Invariant 7's stuck bound: every fish must move at least this far in every 30 s window.
     * A genuinely corner-stuck fish shows near-zero; the observed floor (0.075) is a lone fish
     * calmly wandering a narrow depth-arm plane — the same idle a solo single-tank fish has today.
     */
    private static final float MIN_WINDOW_DISPLACEMENT = 0.05f;
    private static final int WINDOW_TICKS = 600;

    // ── Domain fixtures ─────────────────────────────────────────────────────

    static boolean[][][] fullGrid(int sx, int sy, int sz) {
        boolean[][][] g = new boolean[sx][sy][sz];
        for (boolean[][] a : g) for (boolean[] b : a) Arrays.fill(b, true);
        return g;
    }

    /** Lateral arm of 3 cells plus a 2-cell depth arm off its end — the concave-corner case. */
    static boolean[][][] lShape() {
        boolean[][][] g = new boolean[3][1][3];
        for (int ix = 0; ix < 3; ix++) g[ix][0][0] = true;
        g[2][0][1] = true;
        g[2][0][2] = true;
        return g;
    }

    private static FishSpec[] swimmerSpecs(int n, long seed) {
        Random r = new Random(seed * 31 + n);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.06f + r.nextFloat() * 0.2f, Locomotion.FREE_SWIM, r.nextBoolean(), r.nextInt(3));
        }
        return specs;
    }

    // ── 1-voxel parity with the legacy Box geometry ─────────────────────────

    @Test
    void oneVoxelDomainReproducesTheLegacyBoxGeometry() {
        VoxelDomain d = new VoxelDomain(fullGrid(1, 1, 1));
        assertEquals(-0.35f, d.minLateral(), 1e-6f);
        assertEquals(0.35f, d.maxLateral(), 1e-6f);
        assertEquals(-0.35f, d.minDepth(), 1e-6f);
        assertEquals(0.35f, d.maxDepth(), 1e-6f);
        assertEquals(0.7f, d.sizeGateRun(), 1e-6f);
        assertArrayEquals(new float[]{-0.25f, 0f, 0.25f}, d.layerDepths(), 1e-6f);

        // Gate verdicts agree with the Box for the same lengths (invariant 8, voxel edition).
        Tunables t = Tunables.GROUP;
        for (float len : new float[]{0.05f, 0.2f, 0.28f, 0.3f, 0.5f}) {
            FlockEngine engine = new FlockEngine(t);
            engine.rebuild(new FishSpec[]{new FishSpec(len, Locomotion.FREE_SWIM, false, 0), new FishSpec(0.1f, Locomotion.FREE_SWIM, false, 0)},
                    99L, 0f, 20f, new VoxelDomain(fullGrid(1, 1, 1)));
            assertEquals(d.sizeGateRun() >= t.gateFactor() * len, engine.swimmers[0], "gate at length " + len);
        }
    }

    @Test
    void layerPlanesSpanTheDepthExtentAtQuarterBlockSpacing() {
        // 2-deep slab: interior depth extent 1.7 → 7 planes ±0.75, centered.
        VoxelDomain slab = new VoxelDomain(fullGrid(2, 1, 2));
        assertArrayEquals(new float[]{-0.75f, -0.5f, -0.25f, 0f, 0.25f, 0.5f, 0.75f},
                slab.layerDepths(), 1e-6f);
        // Every plane lies strictly inside the domain.
        for (float p : slab.layerDepths()) {
            assertTrue(p > slab.minDepth() && p < slab.maxDepth());
        }
    }

    @Test
    void sizeGateReadsTheLongestHorizontalRunOnEitherAxis() {
        // 3×1×1 row: one 3-cell run → 2.7 usable blocks.
        assertEquals(2.7f, new VoxelDomain(fullGrid(3, 1, 1)).sizeGateRun(), 1e-6f);
        // Depth-oriented row must gate identically — the horizontal axes are symmetric.
        assertEquals(2.7f, new VoxelDomain(fullGrid(1, 1, 3)).sizeGateRun(), 1e-6f);
        // L-shape: the longest run is still the 3-cell arm.
        assertEquals(2.7f, new VoxelDomain(lShape()).sizeGateRun(), 1e-6f);
    }

    /**
     * Axis parity: a depth-oriented 1×1×3 row must behave like the lateral 3×1×1 row — the planar
     * model has no preferred horizontal axis. Measured as long-axis coverage (position range over
     * a 10k-tick run as a fraction of the domain's long extent) matching within tolerance.
     */
    @Test
    void lateralAndDepthRowsBehaveIdentically() {
        double lateralCoverage = longAxisCoverage(fullGrid(3, 1, 1), true);
        double depthCoverage = longAxisCoverage(fullGrid(1, 1, 3), false);
        assertTrue(lateralCoverage > 0.55, "lateral row coverage " + lateralCoverage);
        assertTrue(depthCoverage > 0.55, "depth row coverage " + depthCoverage);
        assertTrue(Math.abs(lateralCoverage - depthCoverage) < 0.3,
                "axis asymmetry: lateral " + lateralCoverage + " vs depth " + depthCoverage);
    }

    /**
     * Species-aware flocking: shoal-mates tolerate closeness and school together, strangers keep
     * the wider cross-species separation — measured as post-warmup mean pairwise distance within
     * a species vs across species.
     */
    @Test
    void sameSpeciesSwimCloserThanStrangers() {
        VoxelDomain domain = new VoxelDomain(fullGrid(4, 1, 2));
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        // 12 fish, three species of four — fixed lengths so only species drives the difference.
        FishSpec[] specs = new FishSpec[12];
        for (int i = 0; i < 12; i++) {
            specs[i] = new FishSpec(0.12f, Locomotion.FREE_SWIM, (i & 1) == 0, i % 3);
        }
        engine.rebuild(specs, 987L, 0f, 20f, domain);

        double sameSum = 0, crossSum = 0;
        long samePairs = 0, crossPairs = 0;
        for (int tick = 0; tick < 10_000; tick++) {
            engine.step();
            if (tick < 2_000 || tick % 10 != 0) continue; // warmup, then sample sparsely
            for (int i = 0; i < 12; i++) {
                for (int j = i + 1; j < 12; j++) {
                    float dx = engine.posL()[i] - engine.posL()[j];
                    float dy = engine.posY()[i] - engine.posY()[j];
                    float dz = engine.posD()[i] - engine.posD()[j];
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (engine.species[i] == engine.species[j]) {
                        sameSum += d;
                        samePairs++;
                    } else {
                        crossSum += d;
                        crossPairs++;
                    }
                }
            }
        }
        double sameMean = sameSum / samePairs;
        double crossMean = crossSum / crossPairs;
        assertTrue(sameMean < crossMean,
                "same-species mean dist " + sameMean + " should be below cross-species " + crossMean);
    }

    private static double longAxisCoverage(boolean[][][] occ, boolean lateralLong) {
        VoxelDomain domain = new VoxelDomain(occ);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(swimmerSpecs(8, 424242L), 424242L, 0f, 20f, domain);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int tick = 0; tick < 10_000; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                if (!engine.swimmers[i]) continue;
                float p = lateralLong ? engine.posL()[i] : engine.posD()[i];
                min = Math.min(min, p);
                max = Math.max(max, p);
            }
        }
        float span = lateralLong
                ? domain.maxLateral() - domain.minLateral()
                : domain.maxDepth() - domain.minDepth();
        return (max - min) / span;
    }

    // ── Honeycomb-sealed connections between merged members ─────────────────

    /**
     * Two tanks honeycomb-sealed against each other but still merged into one occupancy union
     * (e.g. both also connect to a third tank around a corner) must keep a real wall on that one
     * sealed face — occupancy alone is one bit per cell, so without the blocked-face mask the seam
     * silently reads as open water (docs/fish-tank-... honeycomb-through-walls investigation).
     */
    @Test
    void blockedInternalFaceStaysAWallDespiteMergedOccupancy() {
        boolean[][][] occ = fullGrid(2, 1, 1);
        boolean[][][] blockedX = {{{true}}}; // the one face, between cell 0 and cell 1

        VoxelDomain sealed = new VoxelDomain(occ, VoxelDomain.DEFAULT_INSET, 0f, null, blockedX, null, null);
        // Each cell keeps its own swimmable interior...
        assertTrue(sealed.contains(-0.5f, 0f, 0f), "left cell center must be swimmable");
        assertTrue(sealed.contains(0.5f, 0f, 0f), "right cell center must be swimmable");
        // ...but the seam itself is walled off exactly like an exterior boundary (same inset).
        assertFalse(sealed.contains(-0.1f, 0f, 0f), "left cell must not reach past the sealed face");
        assertFalse(sealed.contains(0.1f, 0f, 0f), "right cell must not reach past the sealed face");

        // Control: the same union with no mask is one open interior — the bug this guards against.
        VoxelDomain merged = new VoxelDomain(occ, VoxelDomain.DEFAULT_INSET, 0f, null, null, null, null);
        assertTrue(merged.contains(-0.1f, 0f, 0f), "sanity: unmasked union is open water at the seam");
        assertTrue(merged.contains(0.1f, 0f, 0f), "sanity: unmasked union is open water at the seam");
    }

    /**
     * Whichever side of a sealed pair a fish starts on, it must never cross to the other side over
     * a long run — the end-to-end guarantee, not just the static distance-field check above. Fish
     * may spawn on either side, so this tracks each fish's own starting side rather than assuming
     * one; a bug that let the seam pass would show up as some fish's side flipping mid-run.
     */
    @Test
    void fishNeverCrossesASealedInternalFace() {
        boolean[][][] occ = fullGrid(2, 1, 1);
        boolean[][][] blockedX = {{{true}}};
        VoxelDomain domain = new VoxelDomain(occ, VoxelDomain.DEFAULT_INSET, 0f, null, blockedX, null, null);

        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(swimmerSpecs(8, 20260911L), 20260911L, 0f, 20f, domain);
        Boolean[] startedNegative = new Boolean[engine.count()];
        for (int tick = 0; tick < 10_000; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                if (!engine.swimmers[i]) continue;
                float l = engine.posL()[i];
                assertTrue(l <= -1e-3f || l >= 1e-3f,
                    "fish " + i + " sat inside the sealed face's zero-width plane at tick " + tick + " (l=" + l + ")");
                boolean negative = l < 0f;
                if (startedNegative[i] == null) {
                    startedNegative[i] = negative;
                } else {
                    assertTrue(startedNegative[i] == negative,
                        "fish " + i + " crossed the sealed face at tick " + tick + " (l=" + l + ")");
                }
            }
        }
    }

    // ── Invariants 1–6 + stuck detector over the voxel matrix ───────────────

    private record Config(String name, boolean[][][] occ, int fishCount, long seed) {}

    private static List<Config> matrix() {
        List<Config> list = new ArrayList<>();
        record D(String name, boolean[][][] occ) {}
        D[] domains = {
                new D("1x1x1", fullGrid(1, 1, 1)),
                new D("3x1x1", fullGrid(3, 1, 1)),
                new D("L", lShape()),
                new D("2x2slab", fullGrid(2, 1, 2)),
        };
        for (D d : domains) {
            for (int n : new int[]{6, 12}) {
                for (long seed : new long[]{12345L, -987654321L}) {
                    list.add(new Config(d.name() + " n=" + n + " seed=" + seed, d.occ(), n, seed));
                }
            }
        }
        return list;
    }

    @TestFactory
    List<DynamicTest> measuredInvariantsAcrossVoxelMatrix() {
        Tunables t = Tunables.GROUP;
        List<DynamicTest> tests = new ArrayList<>();
        for (Config c : matrix()) {
            tests.add(DynamicTest.dynamicTest(c.name(), () -> {
                VoxelDomain domain = new VoxelDomain(c.occ());
                FlockEngine engine = new FlockEngine(t);
                engine.rebuild(swimmerSpecs(c.fishCount(), c.seed()), c.seed(), 30f, 20f, domain);
                Metrics m = new Metrics(engine, t, 200);

                int n = c.fishCount();
                float[] winL = new float[n], winY = new float[n], winD = new float[n];
                float[] windowMax = new float[n];
                float minWindowDisp = Float.MAX_VALUE;

                for (int tick = 0; tick < 10_000; tick++) {
                    if (tick % WINDOW_TICKS == 0) {
                        for (int i = 0; i < n; i++) {
                            if (tick > 0 && engine.swimmers[i] && windowMax[i] < minWindowDisp) {
                                minWindowDisp = windowMax[i];
                            }
                            winL[i] = engine.posL()[i];
                            winY[i] = engine.posY()[i];
                            winD[i] = engine.posD()[i];
                            windowMax[i] = 0f;
                        }
                    }
                    engine.step();
                    m.sample();
                    for (int i = 0; i < n; i++) {
                        float dx = engine.posL()[i] - winL[i];
                        float dy = engine.posY()[i] - winY[i];
                        float dz = engine.posD()[i] - winD[i];
                        float disp = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (disp > windowMax[i]) windowMax[i] = disp;
                    }
                }

                // 1 — containment (the concave corner counts: contains() is occupancy-aware).
                assertEquals(0, m.wallPenetrations(), "wall penetrations");
                assertEquals(0, m.hardClampContacts(), "hard backstop engagements");

                // 2 — bounded dynamics. The speed ceiling is per-fish in the planar model (Tier 1
                // trait jitter), so the bound is the fastest individual the tunables can produce,
                // not the nominal maxSpeed — this still catches a runaway, it just no longer
                // asserts that every fish in a school tops out at exactly the same speed.
                float speedCeiling = t.maxSpeed() * (1f + t.traitJitter());
                assertTrue(m.maxObservedSpeed() <= speedCeiling * SPEED_TOLERANCE, "speed " + m.maxObservedSpeed());
                assertTrue(m.maxObservedAccel() <= t.maxForce() * ACCEL_TOLERANCE, "accel " + m.maxObservedAccel());
                assertTrue(m.maxObservedJerk() <= MAX_JERK, "jerk " + m.maxObservedJerk());

                // 3 (planar edition) — the flip-storm class of bug shows up as unbounded jerk
                // here (yaw is continuous and turn-rate capped, so mirror-flip metrics don't
                // apply); the jerk bound above covers it.

                // 4 — near-wall vertical calm (field distance ⇒ interior walls count too).
                assertTrue(m.nearWallVelYVariance() <= MAX_NEAR_WALL_VELY_VARIANCE,
                        "near-wall velY variance " + m.nearWallVelYVariance());

                // 6 — shoal shape.
                double nn = m.meanNearestNeighborDist();
                assertTrue(nn >= NN_MEAN_MIN && nn <= NN_MEAN_MAX, "mean NN dist " + nn);
                assertTrue(m.minPairwiseDist() >= MIN_PAIRWISE_FLOOR, "min pairwise " + m.minPairwiseDist());

                // 7 — the stuck detector: windowed displacement never collapses.
                assertTrue(minWindowDisp >= MIN_WINDOW_DISPLACEMENT,
                        "a fish moved only " + minWindowDisp + " blocks in a " + WINDOW_TICKS + "-tick window");
            }));
        }
        return tests;
    }

    // ── Invariant 10: the nonholonomic turn limit actually binds ────────────

    /**
     * A fish's TRAVEL direction may not swing faster than it can turn its body.
     *
     * <p>This is the guarantee {@code Tunables.turnRateDegPerTick} exists to provide, and the
     * reason it is worth a test of its own is that the engine used to satisfy it only by accident:
     * the sprite yaw was rate-capped while the velocity vector was free to swing as fast as
     * {@code maxForce} allowed. Measured before the cap, travel direction turned up to 20°/tick
     * against a 7°/tick sprite limit, and the fish visibly crabbed — pointing one way, moving
     * another — during wall avoids and separation shoves.
     *
     * <p>Two allowances, both deliberate. Per-fish trait jitter scales each fish's turn rate, so
     * the bound is the nimblest individual the tunables can produce. And below
     * {@code TURN_CAP_MIN_SPEED_FRACTION} of top speed the cap stops shrinking with speed (a fish
     * that slow is pivoting, and its travel direction is numerically ill-defined), so the
     * measurement only counts fish genuinely under way — which is also the only time a viewer
     * could see the artifact.
     */
    @TestFactory
    List<DynamicTest> travelDirectionNeverOutrunsTheTurnRate() {
        Tunables t = Tunables.GROUP;
        // The cap is on the commanded lateral acceleration; the realised per-tick turn is its
        // arctangent plus one tick of integration slack, so allow a small margin over the bound.
        float bound = t.turnRateDegPerTick() * (1f + t.traitJitter()) * 1.35f;
        float underway = 0.25f * t.maxSpeed();
        List<DynamicTest> tests = new ArrayList<>();
        for (Config c : matrix()) {
            tests.add(DynamicTest.dynamicTest(c.name(), () -> {
                VoxelDomain domain = new VoxelDomain(c.occ());
                FlockEngine engine = new FlockEngine(t);
                engine.rebuild(swimmerSpecs(c.fishCount(), c.seed()), c.seed(), 30f, 20f, domain);

                int n = c.fishCount();
                float[] prevDir = new float[n];
                boolean[] havePrev = new boolean[n];
                float worst = 0f;
                String where = "";
                for (int tick = 0; tick < 10_000; tick++) {
                    engine.step();
                    for (int i = 0; i < n; i++) {
                        if (!engine.swimmers[i]) continue;
                        float vl = engine.velL()[i], vd = engine.velD()[i];
                        float hsp = (float) Math.sqrt(vl * vl + vd * vd);
                        if (hsp <= underway) { havePrev[i] = false; continue; }
                        float dir = (float) Math.toDegrees(Math.atan2(-vd, vl));
                        if (havePrev[i]) {
                            float turn = Math.abs(wrapDeg(dir - prevDir[i]));
                            if (turn > worst) {
                                worst = turn;
                                where = "fish " + i + " at tick " + tick;
                            }
                        }
                        prevDir[i] = dir;
                        havePrev[i] = true;
                    }
                }
                assertTrue(worst <= bound,
                        "travel direction turned " + worst + " deg/tick (" + where + "), bound " + bound);
            }));
        }
        return tests;
    }

    /** Wraps an angle to (−180, 180] — the engine's own convention. */
    private static float wrapDeg(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }

    // ── Invariant 7: L-domain arm traversal ─────────────────────────────────

    @Test
    void flockOccupiesBothArmsOfTheLDomain() {
        VoxelDomain domain = new VoxelDomain(lShape());
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(swimmerSpecs(12, 424242L), 424242L, 0f, 20f, domain);

        // Local frame: grid is 3×1×3 centered at origin — the lateral arm's far cell is
        // l < −0.5 (any depth it exists at), the depth arm's far cell is d > 0.5.
        boolean lateralArmVisited = false;
        boolean depthArmVisited = false;
        for (int tick = 0; tick < 20_000; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                if (!engine.swimmers[i]) continue;
                if (engine.posL()[i] < -0.5f) lateralArmVisited = true;
                if (engine.posD()[i] > 0.5f) depthArmVisited = true;
            }
            if (lateralArmVisited && depthArmVisited) break;
        }
        assertTrue(lateralArmVisited, "no fish ever entered the lateral arm's far cell");
        assertTrue(depthArmVisited, "no fish ever entered the depth arm's far cell");
    }

    /** The concave corner cell (unoccupied bounding-box space) is never entered — spot check. */
    @Test
    void concaveCornerIsNeverPenetrated() {
        VoxelDomain domain = new VoxelDomain(lShape());
        // The empty corner cells are ix∈{0,1}, iz∈{1,2} → local l ∈ [−1.5, 0.5), d ∈ (−0.5, 1.5]
        // (the lateral arm sits at iz=0, i.e. d ∈ [−1.5, −0.5]).
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(swimmerSpecs(12, 31337L), 31337L, 0f, 20f, domain);
        for (int tick = 0; tick < 20_000; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                if (!engine.swimmers[i]) continue;
                float l = engine.posL()[i], d = engine.posD()[i];
                assertTrue(!(l < 0.5f && d > -0.5f),
                        "fish " + i + " inside the empty corner at tick " + tick + " (l=" + l + ", d=" + d + ")");
            }
        }
    }
}

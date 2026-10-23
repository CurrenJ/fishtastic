package grill24.fishsim.domain;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 2 natural-water domain contract (docs/fish-in-world.md §domain): fractional
 * surfaces and partial collision end up in the spans, containment and avoidance are exact where it
 * matters, whole animated bodies either fit or are rejected, the floor follows the real bed, the
 * soft avoidance keeps the hard backstop from ever engaging for a shoal that actually swims, and a
 * rebuild over changed water carries positions through unchanged because the frame never moves.
 */
class WaterDomainTest {
    @Test
    void deepWaterExposesKnownBottomWithoutInventingASeaBed() {
        var domain = build(List.of(new WaterDomain.Column(0, 0, new float[]{-16, 2}, true)), 0, 0, 0);
        assertTrue(Float.isNaN(domain.bedAt(0.5f, 0.5f)));
        assertEquals(-16, domain.bottomAt(0.5f, 0.5f));
        assertTrue(Float.isNaN(domain.bottomAt(10, 10)));
    }

    private static final float SURFACE = 8f / 9f;
    /** The head-room a hosted body needs above its centre; the domain keeps it clear of the surface. */
    private static final float TOP_MARGIN = 0.35f;

    private static WaterDomain.Column col(int x, int z, float... spans) {
        return new WaterDomain.Column(x, z, spans, false);
    }

    /** A flat pond: one span per column from the bed to the surface of its top cell. */
    private static List<WaterDomain.Column> pond(int x0, int x1, int z0, int z1, int bed, int depth) {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                cols.add(col(x, z, bed, bed + depth - 1 + SURFACE));
            }
        }
        return cols;
    }

    private static WaterDomain build(List<WaterDomain.Column> cols, int originX, int originY, int originZ) {
        return build(cols, originX, originY, originZ, TOP_MARGIN, null);
    }

    private static WaterDomain build(List<WaterDomain.Column> cols, int originX, int originY, int originZ,
                                     float topMargin, boolean[] openFaces) {
        WaterDomain d = WaterDomain.build(cols, originX, originY, originZ, topMargin, openFaces);
        assertNotNull(d, "domain should have been built");
        return d;
    }

    // ── Shape ───────────────────────────────────────────────────────────────

    @Test
    void boundsCoverTheDiscoveredColumns() {
        WaterDomain d = build(pond(10, 13, 20, 23, 60, 3), 10, 59, 20);
        assertEquals(0f, d.minLateral(), 1e-6);
        assertEquals(4f, d.maxLateral(), 1e-6);
        assertEquals(0f, d.minDepth(), 1e-6);
        assertEquals(4f, d.maxDepth(), 1e-6);
        assertEquals(1f, d.minVertical(), 1e-6, "the bed, one above a local zero one below the origin");
        assertEquals(3f + SURFACE, d.maxVertical(), 1e-6, "the fractional surface of the top cell");
        assertEquals(16, d.columnCount());
    }

    @Test
    void aDomainWithNoWaterAtAllIsNotBuilt() {
        List<WaterDomain.Column> dry = new ArrayList<>();
        dry.add(col(0, 0, new float[0]));
        assertEquals(null, WaterDomain.build(dry, 0, 0, 0, 0f, null));
        assertEquals(null, WaterDomain.build(List.of(), 0, 0, 0, 0f, null));
    }

    @Test
    void aGridBiggerThanTheCapIsRefusedAndTheReasonIsNameable() {
        // The cap is on the grid a build allocates — the bounding box, mostly dry land for a
        // scattered patch — and not on the column count, so it is twice MAX_COLUMNS. A 1 x 8192
        // strip is exactly at it; one column more is refused. gridFits is how a caller tells
        // "this water's rectangle is too big" apart from "there is no water here", which is the
        // difference between keeping the last known domain and retiring it.
        List<WaterDomain.Column> strip = new ArrayList<>();
        for (int z = 0; z < (int) WaterDomain.MAX_GRID_CELLS; z++) {
            strip.add(col(0, z, 60, 63 + SURFACE));
        }
        assertTrue(WaterDomain.gridFits(strip), "the strip is exactly at the cap");
        assertNotNull(WaterDomain.build(strip, 0, 59, 0, TOP_MARGIN, null),
                "a grid of exactly the cap builds");

        strip.add(col(0, (int) WaterDomain.MAX_GRID_CELLS, 60, 63 + SURFACE));
        assertFalse(WaterDomain.gridFits(strip), "one cell of bbox more does not fit");
        assertEquals(null, WaterDomain.build(strip, 0, 59, 0, TOP_MARGIN, null),
                "and the build refuses it");

        assertTrue(WaterDomain.gridFits(null), "no columns at all is not a grid problem");
        List<WaterDomain.Column> dry = new ArrayList<>();
        dry.add(col(0, 0, new float[0]));
        assertTrue(WaterDomain.gridFits(dry), "nor is a column set with no water in it");
    }

    @Test
    void containsStopsAtTheFractionalSurfaceAndTheBed() {
        WaterDomain d = build(pond(0, 3, 0, 3, 60, 3), 0, 59, 0); // water 1 .. 3.888, cut at 3.538
        assertTrue(d.contains(2f, 1f, 2f), "on the bed");
        assertTrue(d.contains(2f, 2.5f, 2f), "mid water");
        assertTrue(d.contains(2f, 3.5f, 2f), "inside the surface cut");
        assertFalse(d.contains(2f, 0.5f, 2f), "below the bed");
        assertFalse(d.contains(2f, 3.6f, 2f), "inside a body's head-room of the surface");
        assertFalse(d.contains(2f, 3.9f, 2f), "above the fractional surface");
        assertFalse(d.contains(-0.5f, 2f, 2f), "outside the columns");
    }

    @Test
    void containsUsesThePerColumnSurfaceNotAGlobalOne() {
        // The eastern half is one cell shallower, so its surface is a whole block lower.
        List<WaterDomain.Column> cols = new ArrayList<>(pond(0, 3, 0, 3, 60, 3));
        cols.addAll(pond(4, 7, 0, 3, 61, 1));
        WaterDomain d = build(cols, 0, 59, 0);
        assertTrue(d.contains(1f, 2.6f, 1f), "the deep half reaches high");
        assertFalse(d.contains(5f, 2.6f, 1f), "the shallow half's surface is lower");
        assertTrue(d.contains(5f, 2.4f, 1f), "and still swimmable below it");
        assertEquals(3f + SURFACE, d.surfaceAt(1.5f, 1.5f), 1e-4);
        assertEquals(2f + SURFACE, d.surfaceAt(5.5f, 1.5f), 1e-4);
    }

    @Test
    void gapsBetweenWaterBodiesAreNotSwimmable() {
        // A pond, a shelf of stone, then a cave pool below it in the same column.
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                cols.add(col(x, z, 60f, 62.888f, 65f, 65.888f));
            }
        }
        WaterDomain d = build(cols, 0, 59, 0);
        assertTrue(d.contains(1f, 1f, 1f), "the lower pool");
        assertTrue(d.contains(1f, 6f, 1f), "the upper pond");
        assertFalse(d.contains(1f, 4f, 1f), "the stone between them");
        assertEquals(1f, d.bedAt(1.5f, 1.5f), 1e-5, "the bed is the lowest span's floor");
    }

    // ── Avoidance ───────────────────────────────────────────────────────────

    @Test
    void avoidanceIsZeroInOpenWater() {
        WaterDomain d = build(pond(0, 7, 0, 7, 60, 5), 0, 59, 0);
        float[] out = new float[3];
        d.avoidance(4f, 3f, 4f, 0.20f, 0.05f, out);
        assertEquals(0f, out[0], 1e-6);
        assertEquals(0f, out[1], 1e-6);
        assertEquals(0f, out[2], 1e-6);
    }

    @Test
    void avoidancePushesInwardAtABank() {
        WaterDomain d = build(pond(0, 7, 0, 7, 60, 5), 0, 59, 0);
        float[] out = new float[3];
        // 0.05 from the low-lateral bank, inside the margin plus its cushion (0.20 + 0.10).
        d.avoidance(0.05f, 3f, 4f, 0.20f, 0.05f, out);
        assertTrue(out[0] > 0f, "pushed away from the low bank, got " + out[0]);
        assertTrue(out[0] <= 1f, "the ramp is unit-bounded");
        assertEquals(0f, out[2], 1e-6, "no depth push in the middle of the depth extent");

        d.avoidance(7.95f, 3f, 4f, 0.20f, 0.05f, out);
        assertTrue(out[0] < 0f, "pushed away from the high bank, got " + out[0]);
    }

    @Test
    void avoidanceFeelsTheNearWallAndIgnoresTheFarOne() {
        // A one-block corridor: the sides are walls, the ends are open water two steps away.
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int z = 0; z <= 8; z++) cols.addAll(pond(0, 0, z, z, 60, 5));
        WaterDomain d = build(cols, 0, 59, 0);
        float[] out = new float[3];
        d.avoidance(0.5f, 3f, 4f, 0.20f, 0.05f, out);
        assertEquals(0f, out[0], 1e-6, "dead centre: both side walls are half a block away");
        assertEquals(0f, out[2], 1e-6, "and the corridor's far ends are past the ramp's reach");

        d.avoidance(0.1f, 3f, 4f, 0.20f, 0.05f, out);
        assertTrue(out[0] > 0f, "a tenth of a block off the low wall and it pushes, got " + out[0]);
        assertEquals(0f, out[2], 1e-6, "still nothing along the corridor");
    }

    @Test
    void avoidanceKeepsClearOfTheSurfaceButNotOfTheBed() {
        WaterDomain d = build(pond(0, 7, 0, 7, 60, 3), 0, 59, 0); // water 1 .. 3.888
        float[] out = new float[3];
        // The avoidance cushion above a fish (marginVertical + head-room = 0.40) is wider than the
        // containment cut (0.35), so this is still inside the water yet already being pushed down.
        assertTrue(d.contains(4f, 3.5f, 4f));
        d.avoidance(4f, 3.5f, 4f, 0.20f, 0.05f, out);
        assertTrue(out[1] < 0f, "pushed down from the surface, got " + out[1]);

        d.avoidance(4f, 1.02f, 4f, 0.20f, 0.05f, out);
        assertTrue(out[1] > 0f, "and lifted off the bed inside its narrow margin, got " + out[1]);

        d.avoidance(4f, 1.5f, 4f, 0.20f, 0.05f, out);
        assertEquals(0f, out[1], 1e-6, "mid-water has no vertical bias");
    }

    @Test
    void hardBackstopBisectsBackIntoTheWater() {
        WaterDomain d = build(pond(0, 7, 0, 7, 60, 3), 0, 59, 0);
        float[] pos = {4f, 9f, 4f}; // far above the surface
        d.constrain(4f, 3f, 4f, pos);
        assertTrue(d.contains(pos[0], pos[1], pos[2]), "the backstop must land inside water");
        assertTrue(pos[1] < 9f);

        // A previous position that was already outside holds still rather than guessing.
        float[] stuck = {4f, 9f, 4f};
        d.constrain(4f, 9f, 4f, stuck);
        assertEquals(9f, stuck[1], 1e-6);
    }

    // ── Whole-body clearance ────────────────────────────────────────────────

    @Test
    void bodyFitsAcceptsMidWaterAndRejectsSurfaceHugging() {
        WaterDomain d = build(pond(0, 7, 0, 7, 0, 3), 0, -1, 0); // water 1 .. 3.888
        WaterDomain.BodyBox body = new WaterDomain.BodyBox(0.19f, 0.32f, 0.32f);

        assertTrue(d.bodyFits(4f, 2f, 4f, body), "mid-water fits");
        assertFalse(d.bodyFits(4f, 3.6f, 4f, body), "the body would cross the surface");
        assertFalse(d.bodyFits(4f, 1.2f, 4f, body), "the body would cross the bed");
    }

    @Test
    void bodyFitsRejectsAWideFishInANarrowCorridor() {
        // Rock either side of a one-block corridor: the ring samples fall outside it.
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int z = 0; z <= 8; z++) cols.addAll(pond(1, 1, z, z, 0, 4));
        WaterDomain d = build(cols, 0, -1, 0);
        WaterDomain.BodyBox small = new WaterDomain.BodyBox(0.10f, 0.20f, 0.20f);
        WaterDomain.BodyBox wide = new WaterDomain.BodyBox(0.60f, 0.20f, 0.20f);

        assertTrue(d.bodyFits(1.5f, 2.5f, 4f, small), "a narrow body fits the corridor");
        assertTrue(d.bodyFits(1.2f, 2.5f, 4f, small), "and fits close to the wall while it stays clear of it");
        assertFalse(d.bodyFits(1.05f, 2.5f, 4f, small), "a tenth from the wall, the body's own radius overlaps it");
        assertFalse(d.bodyFits(1.5f, 2.5f, 4f, wide), "and a body wider than the corridor fits nowhere in it");
    }

    @Test
    void aFishTooBigForTheWaterIsOmittedRatherThanShrunk() {
        // A one-cell-deep pond holds 0.888 of water: a body taller than that fits nowhere.
        WaterDomain d = build(pond(0, 7, 0, 7, 0, 1), 0, -1, 0);
        WaterDomain.BodyBox minnow = new WaterDomain.BodyBox(0.10f, 0.20f, 0.20f);
        WaterDomain.BodyBox big = new WaterDomain.BodyBox(0.30f, 0.55f, 0.55f);
        assertTrue(d.bodyFits(4f, 1.4f, 4f, minnow), "a small fish fits a shallow pond");
        assertFalse(d.bodyFits(4f, 1.4f, 4f, big), "a taller one has nowhere to go in it");
    }

    @Test
    void bodyFitsNeverAllowsAPositionContainsRejects() {
        // contains reserves the whole pool's head-room at the point itself; the engine steps fish
        // under contains, so a body whose own head-room is smaller must not be called fit above that
        // line — such a fish would be spawned outside its own water and pinned there.
        WaterDomain d = build(pond(0, 8, 0, 8, 0, 2), 0, 0, 0, 0.40f, null);
        WaterDomain.BodyBox small = new WaterDomain.BodyBox(0.15f, 0.20f, 0.20f); // needs only 0.20 above
        float surface = 1f + SURFACE;
        assertFalse(d.contains(4f, surface - 0.30f, 4f), "0.30 is above the pool's own 0.40 margin");
        assertFalse(d.bodyFits(4f, surface - 0.30f, 4f, small), "so no body may be declared fit there");
        assertTrue(d.contains(4f, surface - 0.40f, 4f), "on the margin it is contained");
        assertTrue(d.bodyFits(4f, surface - 0.40f, 4f, small), "and there it fits");
    }

    // ── Floor and gate ──────────────────────────────────────────────────────

    @Test
    void floorFollowsTheBedUnderEachColumn() {
        List<WaterDomain.Column> cols = new ArrayList<>();
        cols.add(col(0, 0, 0f, 2.888f));
        cols.add(col(1, 0, 0.5f, 2.888f)); // a slab bed
        cols.add(col(2, 0, 1f, 2.888f));   // a shallower shelf
        // (3, 0) is dry: no water, so nothing to walk on.
        WaterDomain d = build(cols, 0, -1, 0);
        assertEquals(1f, d.floor().heightAt(0.5f, 0.5f), 1e-5);
        assertEquals(1.5f, d.floor().heightAt(1.5f, 0.5f), 1e-5);
        assertEquals(2f, d.floor().heightAt(2.5f, 0.5f), 1e-5);
        assertFalse(d.floor().walkable(3.5f, 0.5f), "a dry column is not part of the domain's floor");
        assertFalse(d.floor().walkable(0.5f, 1.5f), "nor is outside the discovered columns");
        assertEquals(3f * FloorField.CELL_SIZE * FloorField.CELL_SIZE * 9f, d.floor().area(), 1e-5);
    }

    @Test
    void sizeGateRunMeasuresTheWidestSwimmableStretch() {
        WaterDomain square = build(pond(0, 5, 0, 5, 60, 3), 0, 59, 0);
        assertEquals(6f - 2 * WaterDomain.DEFAULT_INSET, square.sizeGateRun(), 1e-4);

        // A long thin river is gated on its length, not its width.
        List<WaterDomain.Column> river = new ArrayList<>();
        for (int x = 0; x < 12; x++) river.addAll(pond(x, x, 0, 0, 60, 2));
        WaterDomain r = build(river, 0, 59, 0);
        assertEquals(12f - 2 * WaterDomain.DEFAULT_INSET, r.sizeGateRun(), 1e-4);
    }

    @Test
    void aDryBankSplitsTheSwimRun() {
        List<WaterDomain.Column> cols = new ArrayList<>(pond(0, 5, 0, 0, 60, 3));
        cols.addAll(pond(7, 12, 0, 0, 60, 3)); // one dry column between them
        WaterDomain d = build(cols, 0, 59, 0);
        assertEquals(6f - 2 * WaterDomain.DEFAULT_INSET, d.sizeGateRun(), 1e-4,
                "the gate must see two six-block stretches, not one thirteen-block one");
    }

    @Test
    void layerDepthsSpanTheDepthExtentAtTheTankSpacing() {
        WaterDomain d = build(pond(0, 3, 0, 7, 60, 3), 0, 59, 0);
        float[] planes = d.layerDepths();
        assertEquals((int) Math.floor(8f / WaterDomain.LAYER_SPACING) + 1, planes.length);
        for (int i = 1; i < planes.length; i++) {
            assertEquals(WaterDomain.LAYER_SPACING, planes[i] - planes[i - 1], 1e-5f);
        }
        assertEquals(4f, planes[planes.length / 2], 1e-4, "centred on the depth extent");
    }

    @Test
    void openFacesReportTheirDistance() {
        WaterDomain d = build(pond(0, 7, 0, 7, 60, 3), 0, 59, 0, TOP_MARGIN,
                new boolean[]{false, true, false, false, false, false});
        assertEquals(7.6f, d.openFaceDistance(0.4f, 2f, 4f), 1e-4);
        assertEquals(Float.MAX_VALUE,
                build(pond(0, 7, 0, 7, 60, 3), 0, 59, 0).openFaceDistance(0.4f, 2f, 4f),
                "no open faces: nothing to retire against");

        // A column the scan cut short keeps that fact, and the runtime turns it into an open face.
        WaterDomain c = build(List.of(new WaterDomain.Column(0, 0, new float[]{60f, 61f}, true)),
                0, 59, 0, TOP_MARGIN, new boolean[]{false, false, true, false, false, false});
        assertTrue(c.columnTruncated(0, 0), "the cut is recorded on the column");
        assertTrue(c.openFace(WaterDomain.FACE_MIN_Y));
        assertEquals(0.5f, c.openFaceDistance(0.5f, 1.5f, 0.5f), 1e-5,
                "distance measured to the open face's plane");
    }

    // ── Swimming in it ──────────────────────────────────────────────────────

    private static FishSpec[] shoal(int n, long seed, Locomotion locomotion) {
        FishSpec[] specs = new FishSpec[n];
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.06f + r.nextFloat() * 0.15f, locomotion, r.nextBoolean(), i % 3);
        }
        return specs;
    }

    @Test
    void aShoalSwimsForTenThousandTicksWithoutTheBackstopEverEngaging() {
        WaterDomain d = build(pond(0, 9, 0, 9, 60, 5), 0, 59, 0);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(shoal(12, 424242L, Locomotion.FREE_SWIM), 424242L, 0f, 20f, d);

        for (int tick = 0; tick < 10_000; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                float l = engine.posL()[i], y = engine.posY()[i], depth = engine.posD()[i];
                assertTrue(d.contains(l, y, depth), "fish " + i + " left the water at tick " + tick);
                assertTrue(d.wallDistance(l, y, depth) > -1e-3f,
                        "fish " + i + " was outside the water at tick " + tick);
            }
        }
        assertEquals(0, engine.backstopEngagements(),
                "soft avoidance must keep the hard clamp from ever firing in open water");
    }

    @Test
    void benthicFishWalkTheRealBedAndStayInWater() {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                int bed = 60 + (x >= 4 ? 1 : 0); // a step up halfway across
                cols.add(col(x, z, bed, bed + 2 + SURFACE));
            }
        }
        WaterDomain d = build(cols, 0, 59, 0);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(shoal(6, 31337L, Locomotion.BENTHIC), 31337L, 0f, 20f, d);

        int steps = 0;
        for (int tick = 0; tick < 4_000; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                float l = engine.posL()[i], y = engine.posY()[i];
                float bed = d.floor().heightAt(l, engine.posD()[i]);
                assertFalse(Float.isNaN(bed), "crawler " + i + " walked off the floor");
                assertEquals(bed, y, 1e-5f, "a crawler stands on the bed, not above or below it");
                assertTrue(d.contains(l, y, engine.posD()[i]), "crawler left the water");
                steps++;
            }
        }
        assertEquals(0, engine.backstopEngagements());
        assertTrue(steps > 0);
    }

    @Test
    void driftersHoldStationInMidWater() {
        WaterDomain d = build(pond(0, 7, 0, 7, 60, 4), 0, 59, 0);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(shoal(4, 7L, Locomotion.DRIFT), 7L, 0f, 20f, d);
        for (int tick = 0; tick < 2_000; tick++) {
            engine.step();
        }
        for (int i = 0; i < engine.count(); i++) {
            assertTrue(d.contains(engine.posL()[i], engine.posY()[i], engine.posD()[i]));
            assertTrue(engine.posY()[i] > d.minVertical(), "a drifter never sits on the bed");
            assertTrue(engine.posY()[i] < d.maxVertical(), "nor floats out of the surface");
        }
        assertEquals(0, engine.backstopEngagements());
    }

    // ── Rebuild carry, and the frame that makes it exact ────────────────────

    @Test
    void rebuildOverChangedWaterCarriesWorldPositionsThroughUnchanged() {
        int originX = 0, originY = 59, originZ = 0;
        WaterDomain a = build(new ArrayList<>(pond(0, 7, 0, 7, 60, 4)), originX, originY, originZ);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        FishSpec[] specs = shoal(6, 99L, Locomotion.FREE_SWIM);
        engine.rebuild(specs, 99L, 0f, 20f, a);
        for (int tick = 0; tick < 300; tick++) engine.step();

        float[][] worldBefore = worldPositions(engine, originX, originY, originZ);

        // The window moved and the water changed: a row of shallower columns came in at the far
        // end. The origin is the patch's, so it does not move — which is the whole reason the
        // carry needs no rebasing.
        List<WaterDomain.Column> after = new ArrayList<>(pond(0, 7, 0, 7, 60, 4));
        after.addAll(pond(0, 7, 8, 8, 60, 2));
        WaterDomain b = build(after, originX, originY, originZ);
        engine.rebuildPreserving(specs, new int[]{0, 1, 2, 3, 4, 5}, 99L, 0f, 20f, b);

        float[][] worldAfter = worldPositions(engine, originX, originY, originZ);
        for (int i = 0; i < worldAfter.length; i++) {
            assertEquals(worldBefore[i][0], worldAfter[i][0], 1e-6f,
                    "fish " + i + " moved laterally when the water changed");
            assertEquals(worldBefore[i][1], worldAfter[i][1], 1e-6f,
                    "fish " + i + " moved vertically when the water changed");
            assertEquals(worldBefore[i][2], worldAfter[i][2], 1e-6f,
                    "fish " + i + " moved in depth when the water changed");
        }
    }

    private static float[][] worldPositions(FlockEngine engine, int originX, int originY, int originZ) {
        float[][] out = new float[engine.count()][3];
        for (int i = 0; i < engine.count(); i++) {
            out[i][0] = engine.posL()[i] + originX;
            out[i][1] = engine.posY()[i] + originY;
            out[i][2] = engine.posD()[i] + originZ;
        }
        return out;
    }

    @Test
    void aBuildOfATwoThousandColumnDomainIsCheap() {
        // The order-of-magnitude guard on the field-build budget: the harness reports the real
        // per-rebuild numbers for live domains (docs/fish-in-world.md §budgets).
        List<WaterDomain.Column> big = new ArrayList<>();
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                big.add(col(x, z, 60 - (x % 4), 65.888f));
            }
        }
        long start = System.nanoTime();
        WaterDomain d = build(big, 0, 59, 0);
        long elapsed = System.nanoTime() - start;
        assertEquals(48 * 48, d.columnCount());
        System.out.println("[waterdomain] 2304-column build: " + (elapsed / 1_000_000.0) + " ms");
        assertTrue(elapsed < 50_000_000L,
                "building 2304 columns took " + (elapsed / 1_000_000.0) + " ms; budget is 50 ms");
    }

    // ── The scan boundary, as the client runtime presents it ────────────────
    //
    // Discovery stops at a fixed distance from the player, so the water a domain is built from has
    // an edge that is not geometry. The runtime marks it as an open face and retires individuals
    // there. These two tests pin the two halves of that contract that the client cannot unit-test:
    // a bottom-truncated column has no floor, and the retirement band sits outside the avoidance
    // band, so a leaving fish is gone before any engine can turn it against the boundary.

    @Test
    void aColumnWhoseWaterContinuesBelowHasNoFloor() {
        List<WaterDomain.Column> cols = new ArrayList<>();
        cols.add(new WaterDomain.Column(0, 0, new float[]{60f, 65f}, true)); // cut at the scan floor
        cols.add(new WaterDomain.Column(1, 0, new float[]{60f, 65f}, false)); // real bed
        WaterDomain d = build(cols, 0, 59, 0, TOP_MARGIN,
                new boolean[]{false, false, true, false, false, false});

        assertTrue(Float.isNaN(d.bedAt(0.5f, 0.5f)), "an unseen bottom must not become a bed");
        assertFalse(d.floor().walkable(0.5f, 0.5f), "nothing can stand on a bed that was never seen");
        assertEquals(1f, d.bedAt(1.5f, 0.5f), 1e-5f, "the column with a real bed keeps it");
        assertTrue(d.floor().walkable(1.5f, 0.5f));
        assertTrue(d.contains(0.5f, 3f, 0.5f), "the water itself is still swimmable");
    }

    @Test
    void theRetirementBandSitsOutsideEveryAvoidanceZone() {
        // A 10-wide pond with its eastern boundary open: the runtime would mirror discovery's own
        // edge here. Nothing else is open, so the only lateral push a fish can feel is the western
        // bank and the east face's own edge.
        WaterDomain d = build(pond(0, 9, 0, 9, 60, 4), 0, 59, 0,
                TOP_MARGIN, new boolean[]{false, true, false, false, false, false});
        float[] out = new float[3];
        float margin = Math.max(Tunables.GROUP.wallMargin(), Tunables.GLIDE.wallMargin());
        float vertical = Tunables.GROUP.wallMarginVertical();
        // Where the soft push can first be felt: the widest locomotion set's zone plus the bank
        // cushion the domain adds to it.
        float pushStarts = margin + WaterDomain.DEFAULT_BANK_CUSHION;
        assertTrue(WaterDomain.OPEN_FACE_MARGIN > pushStarts,
                "the band (" + WaterDomain.OPEN_FACE_MARGIN + ") must reach beyond the push ("
                        + pushStarts + ")");

        // At the far edge of the band there is no push at all — only the fade.
        float l = d.maxLateral() - WaterDomain.OPEN_FACE_MARGIN;
        d.avoidance(l, 2.5f, 5f, margin, vertical, out);
        assertEquals(0f, out[0], 1e-6f, "no push this far from the face");
        assertEquals(1f, squaredFade(d, l, 2.5f, 5f), 1e-5f, "and the fade has only just begun");

        // Where the push does begin, the fish is already more than half gone; at half the band it
        // is under a third of its size; at the plane it is invisible.
        float atPush = d.maxLateral() - pushStarts;
        assertTrue(squaredFade(d, atPush, 2.5f, 5f) < 0.5f, "half faded where the push begins");
        assertTrue(squaredFade(d, d.maxLateral() - WaterDomain.OPEN_FACE_MARGIN * 0.5f, 2.5f, 5f) < 1f / 3f,
                "mostly gone halfway through the band");
        assertEquals(0f, squaredFade(d, d.maxLateral() - 1e-4f, 2.5f, 5f), 1e-4f,
                "at the face plane the fade has reached zero");
    }

    /** The client's position-driven fade: {@code (distance / OPEN_FACE_MARGIN)²}. */
    private static float squaredFade(WaterDomain d, float l, float y, float depth) {
        float open = d.openFaceDistance(l, y, depth);
        if (open == Float.MAX_VALUE) return 1f;
        float t = Math.max(0f, Math.min(1f, open / WaterDomain.OPEN_FACE_MARGIN));
        return t * t;
    }

    @Test
    void openFaceRetirementKeepsThePopulationOffTheBoundary() {
        // The client's boundary strategy, exercised as policy: a fish within OPEN_FACE_MARGIN of an
        // open face leaves the population (removed through the same carry rebuild the runtime
        // uses), and a reachable population target is refilled by a fresh arrival placed mid-water.
        // Over a long run the survivors are never inside the band, and the hard backstop — the
        // engine clamping a fish that has already left the water — never fires.
        WaterDomain d = build(pond(0, 19, 0, 19, 60, 6), 0, 59, 0,
                TOP_MARGIN, new boolean[]{false, true, false, false, false, false});
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        List<FishSpec> specs = new ArrayList<>(Arrays.asList(shoal(8, 5150L, Locomotion.FREE_SWIM)));
        engine.rebuild(specs.toArray(new FishSpec[0]), 5150L, 0f, 20f, d);
        // Spawned the way the runtime spawns: mid-water, clear of every face, never wherever the
        // scatter happened to drop them. Two are deliberately put inside the band — the case a
        // boundary hands the policy — and must be gone by the end of the first tick.
        for (int i = 0; i < engine.count(); i++) engine.placeFish(i, 4f + i, 3f, 4f + i);
        engine.placeFish(0, d.maxLateral() - 0.30f, 3f, 5f);
        engine.placeFish(1, d.maxLateral() - 0.10f, 3.5f, 9f);

        int retirements = 0;
        for (int tick = 0; tick < 4_000; tick++) {
            engine.step();
            int n = engine.count();
            int[] carry = new int[n];
            FishSpec[] next = new FishSpec[n];
            int kept = 0;
            for (int i = 0; i < n; i++) {
                if (d.openFaceDistance(engine.posL()[i], engine.posY()[i], engine.posD()[i])
                        <= WaterDomain.OPEN_FACE_MARGIN) {
                    retirements++;
                    continue;
                }
                carry[kept] = i;
                next[kept] = specs.get(i);
                kept++;
            }
            if (kept != n) {
                specs = new ArrayList<>(Arrays.asList(next).subList(0, kept));
                engine.rebuildPreserving(specs.toArray(new FishSpec[0]),
                        Arrays.copyOf(carry, kept), 5150L, 0f, 20f, d);
            }
            if (engine.count() < 6) {
                specs.add(new FishSpec(0.30f, Locomotion.FREE_SWIM, false, 1));
                FishSpec[] grown = specs.toArray(new FishSpec[0]);
                int[] grownCarry = new int[grown.length];
                for (int i = 0; i < grownCarry.length; i++) grownCarry[i] = i < engine.count() ? i : -1;
                engine.rebuildPreserving(grown, grownCarry, 5150L, 0f, 20f, d);
                engine.placeFish(engine.count() - 1, 10f, 3f, 10f);
            }
            for (int i = 0; i < engine.count(); i++) {
                assertTrue(d.openFaceDistance(engine.posL()[i], engine.posY()[i], engine.posD()[i])
                                > WaterDomain.OPEN_FACE_MARGIN,
                        "a survivor was inside the retirement band at tick " + tick);
            }
        }
        assertTrue(retirements >= 2, "the two fish placed in the band should have been retired");
        assertEquals(0, engine.backstopEngagements(),
                "no fish should ever be clamped — retirement replaces the hard edge");
        System.out.println("[waterdomain] open-face retirements over 4000 ticks: " + retirements);
    }
}

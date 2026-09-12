package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.FloorField;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor walk's own invariants (docs/fish-sim-locomotion.md §6) — the set that replaces
 * {@code BENTHIC}'s "never moves" row in {@link LocomotionTest} now that it has a motion model.
 *
 * <p>The load-bearing one is {@link #crawlersNeverStandOnAnObstacle}: a crawler must be
 * <i>structurally</i> unable to enter a blocked cell, not merely unlikely to, because a crab
 * clipping through a cosmetic at floor level is exactly the artifact folding cosmetics into the
 * floor field exists to prevent.
 */
class BenthicTest {

    private static final int TICKS = 2_000;
    /** The tank's sand surface, in the single-tank engine's local vertical (see the adapter). */
    private static final float SURFACE_Y = 0.125f - 0.5f;

    private static FloorField tank(boolean[] blockedCells) {
        return FloorField.flat(1, 1, -0.5f, -0.5f, SURFACE_Y, blockedCells);
    }

    /** Marks cosmetic cells (row-major over the 3×3 floor grid) as occupied. */
    private static boolean[] blocked(int... cells) {
        boolean[] mask = new boolean[FloorField.SUBCELLS * FloorField.SUBCELLS];
        for (int cell : cells) mask[cell] = true;
        return mask;
    }

    private static FlockEngine crawlers(FloorField floor, FishSpec... specs) {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, 4242L, 0f, 3, 0.35f, 0.3f, 20f, floor);
        return engine;
    }

    private static FishSpec crab(float length, int species) {
        return new FishSpec(length, Locomotion.BENTHIC, false, species);
    }

    /** A crawler's Y is the floor under it, every tick — it never floats and never sinks. */
    @Test
    void crawlersStayOnTheFloor() {
        FlockEngine engine = crawlers(tank(null), crab(0.12f, 0), crab(0.10f, 1), crab(0.14f, 2));
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                assertEquals(SURFACE_Y, engine.posY()[i], 1e-6f,
                        "crawler " + i + " left the floor at tick " + tick);
            }
        }
    }

    /**
     * The invariant folding cosmetics into the floor exists for: a blocked cell is never entered,
     * at any tick, by any crawler — including one being shoved by a neighbour.
     */
    @Test
    void crawlersNeverStandOnAnObstacle() {
        // A cross of cosmetics through the middle of the tank, leaving the four corner cells.
        FlockEngine engine = crawlers(tank(blocked(1, 3, 4, 5, 7)),
                crab(0.10f, 0), crab(0.10f, 1), crab(0.10f, 2));
        FloorField floor = engine.domain().floor();

        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                assertTrue(floor.walkable(engine.posL()[i], engine.posD()[i]),
                        "crawler " + i + " entered a blocked cell at tick " + tick);
            }
        }
    }

    /** Crawlers keep their footprints apart — the 2D separation the old 3D scatter could not give. */
    @Test
    void crawlersKeepTheirFootprintsApart() {
        FlockEngine engine = crawlers(tank(null), crab(0.10f, 0), crab(0.10f, 0), crab(0.10f, 0));
        // Warm up past the scatter before measuring, then hold to the placement's own guarantee.
        for (int tick = 0; tick < 200; tick++) engine.step();

        float worst = Float.MAX_VALUE;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                for (int j = i + 1; j < engine.count(); j++) {
                    float dl = engine.posL()[i] - engine.posL()[j];
                    float dd = engine.posD()[i] - engine.posD()[j];
                    worst = Math.min(worst, (float) Math.sqrt(dl * dl + dd * dd));
                }
            }
        }
        // Two 0.10-long crawlers want 2 × 0.6 × 0.10 = 0.12 apart; the push is soft, so assert
        // they never come closer than half of that rather than asserting the target exactly.
        assertTrue(worst > 0.06f, "crawlers overlapped: closest approach was " + worst);
    }

    /** A crawler actually goes somewhere — the counterpart to the frozen-hover assertions. */
    @Test
    void crawlersCoverGround() {
        FlockEngine engine = crawlers(tank(null), crab(0.10f, 0));
        float startL = engine.posL()[0], startD = engine.posD()[0];
        float travelled = 0f;
        float prevL = startL, prevD = startD;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            float dl = engine.posL()[0] - prevL, dd = engine.posD()[0] - prevD;
            travelled += (float) Math.sqrt(dl * dl + dd * dd);
            prevL = engine.posL()[0];
            prevD = engine.posD()[0];
        }
        // 100 s of scuttle-and-pause at 0.035 blocks/s and a 35% duty is ~1.2 blocks of path.
        assertTrue(travelled > 0.5f, "crawler barely moved: " + travelled + " blocks of path");
        assertNotEquals(startL, engine.posL()[0], "crawler ended exactly where it started");
    }

    /** It scuttles and pauses rather than gliding at a constant rate. */
    @Test
    void crawlersScuttleAndPause() {
        FlockEngine engine = crawlers(tank(null), crab(0.10f, 0));
        int moving = 0, stopped = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            if (engine.speed[0] > 0.02f) moving++;
            if (engine.speed[0] < 0.005f) stopped++;
        }
        assertTrue(moving > 100, "crawler never got up to speed (" + moving + " ticks moving)");
        assertTrue(stopped > 100, "crawler never rested (" + stopped + " ticks stopped)");
    }

    /** The benthic gate measures floor area, and obstacles take floor area away. */
    @Test
    void theBenthicGateMeasuresFloorArea() {
        // An open 1-block floor is 1.0 blocks²; the gate wants 4 × length².
        assertSame(Locomotion.BENTHIC, crawlers(tank(null), crab(0.45f, 0)).locomotion[0],
                "0.45 long needs 0.81 blocks² and the floor has 1.0");
        assertSame(Locomotion.STATIC, crawlers(tank(null), crab(0.55f, 0)).locomotion[0],
                "0.55 long needs 1.21 blocks² and the floor has only 1.0");
        // Fill five of the nine cells and the same creature no longer fits.
        assertSame(Locomotion.STATIC, crawlers(tank(blocked(0, 1, 2, 3, 4)), crab(0.45f, 0)).locomotion[0],
                "cosmetics took the floor away");
    }

    /**
     * A gate failure still sits on the sand. The demoted crawler is {@code STATIC} — it does not
     * walk — but its Y came from the floor at placement, so it does not hover in mid-water either,
     * which is what the renderer used to guarantee by pinning the pose's Y.
     */
    @Test
    void aGateFailedCrawlerStillSitsOnTheFloor() {
        FlockEngine engine = crawlers(tank(null), crab(0.55f, 0));
        assertSame(Locomotion.STATIC, engine.locomotion[0]);
        assertEquals(SURFACE_Y, engine.posY()[0], 1e-6f, "a demoted crawler floated");
    }

    /**
     * Adding a crawler to a tank must not shift the shared scatter stream — the swimmers around it
     * scatter to bit-identical positions. This is why placement draws from the fish's own seed.
     */
    @Test
    void addingACrawlerDoesNotDisturbTheSwimmers() {
        FishSpec[] withoutCrab = {
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0),
                new FishSpec(0.12f, Locomotion.FREE_SWIM, true, 0),
                new FishSpec(0.09f, Locomotion.FREE_SWIM, false, 0),
        };
        FishSpec[] withCrab = {
                withoutCrab[0],
                withoutCrab[1],
                withoutCrab[2],
                crab(0.10f, 1),
        };

        FlockEngine a = crawlers(tank(null), withoutCrab);
        FlockEngine b = crawlers(tank(null), withCrab);
        for (int tick = 0; tick < 200; tick++) {
            a.step();
            b.step();
        }
        for (int i = 0; i < withoutCrab.length; i++) {
            assertEquals(Float.floatToRawIntBits(a.posL()[i]), Float.floatToRawIntBits(b.posL()[i]),
                    "swimmer " + i + " moved when a crab was added");
            assertEquals(Float.floatToRawIntBits(a.posD()[i]), Float.floatToRawIntBits(b.posD()[i]),
                    "swimmer " + i + " moved in depth when a crab was added");
        }
    }

    /** In a group, a crawler walks the whole aquarium's sand and steps up between floor levels. */
    @Test
    void crawlersWalkAGroupFloor() {
        // Two tanks side by side, with a third stacked on the right one: the upper tank's own
        // floor is a level above, and its column's floor is the lower tank's (nothing to stand on
        // mid-air), so every walkable cell sits on the bottom row.
        boolean[][][] occupancy = new boolean[2][2][1];
        occupancy[0][0][0] = true;
        occupancy[1][0][0] = true;
        occupancy[1][1][0] = true;
        VoxelDomain domain = new VoxelDomain(occupancy, VoxelDomain.DEFAULT_INSET, 0.125f, null);

        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(new FishSpec[]{crab(0.10f, 0), crab(0.10f, 1)}, 99L, 0f, 20f, domain);

        float minL = Float.MAX_VALUE, maxL = -Float.MAX_VALUE;
        for (int tick = 0; tick < 4_000; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                assertTrue(domain.floor().walkable(engine.posL()[i], engine.posD()[i]),
                        "crawler " + i + " walked off the group floor at tick " + tick);
                assertEquals(domain.floor().heightAt(engine.posL()[i], engine.posD()[i]),
                        engine.posY()[i], 1e-6f, "crawler " + i + " left the floor");
                minL = Math.min(minL, engine.posL()[i]);
                maxL = Math.max(maxL, engine.posL()[i]);
            }
        }
        // The group is 2 blocks wide; a crawler confined to one tank could never span more than 1.
        assertTrue(maxL - minL > 1.0f,
                "crawlers stayed within one tank (spanned " + (maxL - minL) + " blocks)");
    }

    /** A crawler carried across a rebuild into a cell that just became blocked re-places onto sand. */
    @Test
    void aCrawlerBuriedByANewCosmeticRePlaces() {
        FishSpec[] specs = {crab(0.10f, 0)};
        FlockEngine engine = crawlers(tank(null), specs);
        for (int tick = 0; tick < 400; tick++) engine.step();

        // Block every cell the crawler could be standing in except one corner, then rebuild
        // carrying it over — its old spot is gone.
        engine.rebuildPreserving(specs, new int[]{0}, 4242L, 0f, 3, 0.35f, 0.3f, 20f,
                tank(blocked(0, 1, 2, 3, 4, 5, 6, 7)));

        assertTrue(engine.domain().floor().walkable(engine.posL()[0], engine.posD()[0]),
                "a buried crawler stayed buried");
        assertEquals(SURFACE_Y, engine.posY()[0], 1e-6f);
    }

    /** Sanity: the floor field's own bookkeeping. */
    @Test
    void floorFieldMeasuresWhatIsLeft() {
        assertEquals(1.0f, FloorField.flat(1, 1, -0.5f, -0.5f, 0f, null).area(), 1e-5f);
        assertEquals(1.0f - 4f / 9f, FloorField.flat(1, 1, -0.5f, -0.5f, 0f, blocked(0, 1, 2, 3)).area(), 1e-5f);

        // A column with no floor at all reads as NaN rather than as height 0.
        boolean[][][] overhang = new boolean[2][2][1];
        overhang[0][0][0] = true;
        overhang[1][1][0] = true; // floats above an empty column? no — it has no cell below it,
        VoxelDomain domain = new VoxelDomain(overhang, VoxelDomain.DEFAULT_INSET, 0.125f, null);
        // ...so it is itself a floor: the lowest occupied cell of its own column.
        assertTrue(domain.floor().walkable(0.8f, 0f), "the overhanging arm has its own floor");
        assertEquals(domain.floor().heightAt(-0.8f, 0f) + 1f, domain.floor().heightAt(0.8f, 0f), 1e-5f,
                "the upper arm's floor is one block above the lower one's");
    }

    /**
     * A tank records the placing player's yaw, so its local axes are rotated while its sand and
     * cosmetics are not. A crawler must respect the cosmetics where they actually are.
     */
    @Test
    void obstaclesHoldUnderTheTanksPlacementRotation() {
        for (float rotation : new float[]{0f, 37f, 90f, 180f, 254f}) {
            FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
            // Everything blocked but the north-west cell: wherever the crawler ends up, it can
            // only legally be in that one corner of the BLOCK, whatever the local frame is doing.
            engine.rebuild(new FishSpec[]{crab(0.10f, 0)}, 4242L, rotation, 3, 0.35f, 0.3f, 20f,
                    tank(blocked(1, 2, 3, 4, 5, 6, 7, 8)));
            float cos = (float) Math.cos(Math.toRadians(rotation));
            float sin = (float) Math.sin(Math.toRadians(rotation));
            FloorField floor = engine.domain().floor();

            for (int tick = 0; tick < 600; tick++) {
                engine.step();
                float l = engine.posL()[0], d = engine.posD()[0];
                assertTrue(floor.walkable(l * cos + d * sin, -l * sin + d * cos),
                        "crawler stood on an obstacle at rotation " + rotation);
            }
        }
    }

    /** Determinism: same specs, same seed, same tank → the same walk, tick for tick. */
    @Test
    void theWalkIsDeterministic() {
        Random r = new Random(7);
        FishSpec[] specs = new FishSpec[4];
        for (int i = 0; i < specs.length; i++) specs[i] = crab(0.08f + r.nextFloat() * 0.06f, i);

        FlockEngine a = crawlers(tank(blocked(4)), specs);
        FlockEngine b = crawlers(tank(blocked(4)), specs);
        for (int tick = 0; tick < 500; tick++) {
            a.step();
            b.step();
            for (int i = 0; i < specs.length; i++) {
                assertEquals(Float.floatToRawIntBits(a.posL()[i]), Float.floatToRawIntBits(b.posL()[i]),
                        "crawler " + i + " diverged at tick " + tick);
            }
        }
    }
}

package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.WaterDomain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the ambient adapter asks of a rebuild — one resident at a time.
 *
 * <p>The in-world population is not a tank: its water changes shape underneath fish that were
 * placed by a spawn test rather than by the scatter, and a rebuild carries them across at
 * positions the engine has never had a say in. These tests pin the three things a carried
 * resident must never do — drift back to a scatter depth plane it was not placed on, be silently
 * demoted into a frozen hover, and be re-placed onto a floor that has nothing to stand on — plus
 * the query the adapter uses to retire the ones it cannot host.
 */
class AmbientResidentTest {

    private static final float SURFACE = 8f / 9f;
    private static final float TOP_MARGIN = 0.35f;
    private static final int BED = 62;
    private static final int ORIGIN_Y = BED - 1;

    /** A flat pond of whole columns, water from a bed up to a fractional surface. */
    private static List<WaterDomain.Column> pond(int x0, int x1, int z0, int z1, int depth) {
        List<WaterDomain.Column> cols = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                cols.add(new WaterDomain.Column(x, z, new float[]{BED, BED + depth - 1 + SURFACE}, false));
            }
        }
        return cols;
    }

    private static WaterDomain water(List<WaterDomain.Column> cols) {
        WaterDomain d = WaterDomain.build(cols, 0, ORIGIN_Y, 0, TOP_MARGIN, null);
        assertNotNull(d, "domain should have been built");
        return d;
    }

    private static FishSpec[] one(FishSpec spec) {
        return new FishSpec[]{spec};
    }

    @Test
    void aPlacedFishKeepsTheDepthItWasPlacedAt() {
        // placeFish exists so the adapter can put a fish where its own water test accepted it
        // instead of where the scatter would: the scatter fills the whole domain, which for a bay
        // is nowhere near the player. The review flagged this as a bug — placeFish moves pos*
        // without moving homeDepth, the binary tank model's per-fish depth plane. Measured and
        // disproven: the planar model ambient domains run on has no depth plane at all ("no swim
        // axis, no home depth planes", FlockEngine.stepFishPlanar) and never reads homeDepth; over
        // 600 steps a placed fish wanders around its target (max 0.49 blocks) while a scatter-only
        // control never leaves its own plane. So this pins the observable, not a fix: placement
        // must not travel back across the pool, which any true home-plane pull would do (6 blocks
        // at GROUP speed in ~50 steps).
        FishSpec fish = new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 0);
        WaterDomain domain = water(pond(0, 9, 0, 9, 8));
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(one(fish), 7L, 0f, 20f, domain);
        for (int t = 0; t < 40; t++) engine.step();

        float mid = (domain.minDepth() + domain.maxDepth()) * 0.5f;
        float target = engine.posD()[0] < mid ? domain.maxDepth() - 1.5f : domain.minDepth() + 1.5f;
        assertTrue(Math.abs(target - engine.posD()[0]) > 3f, "the test needs a long way to travel");
        engine.placeFish(0, engine.posL()[0], 5f, target);

        for (int t = 0; t < 150; t++) engine.step();
        assertEquals(target, engine.posD()[0], 1.5f,
                "a placed fish drifted back to the depth plane it was scattered on");
    }

    @Test
    void aShrunkDomainLeavesAResidentUnhostableRatherThanFrozen() {
        // A rebuild re-runs the size gate for every carried fish, so a domain that shrank under a
        // resident demotes it to STATIC — the engine doing its job, and the reason the adapter has
        // to re-check its residents after a rebuild. What must not happen is a demoted fish being
        // kept as a frozen specimen hovering in water that cannot hold it.
        FishSpec glide = new FishSpec(1.2f, Locomotion.GLIDE, false, 0);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(one(glide), 7L, 0f, 20f, water(pond(0, 5, 0, 5, 8)));
        for (int t = 0; t < 40; t++) engine.step();
        assertEquals(Locomotion.GLIDE, engine.locomotion[0], "precondition: the pond is big enough");
        assertTrue(engine.residentFits(0, glide), "precondition: the resident passes the hostability check");

        engine.rebuildPreserving(one(glide), new int[]{0}, 7L, 0f, 20f, water(pond(0, 0, 0, 0, 8)));

        assertEquals(Locomotion.STATIC, engine.locomotion[0],
                "the gate demotes a carried resident that its new water cannot host");
        assertFalse(engine.passesGate(Locomotion.GLIDE, glide.length()),
                "the shrunken water no longer hosts a glider");
        assertFalse(engine.residentFits(0, glide),
                "and the adapter's own re-check agrees, which is how it retires the resident");
        assertFalse(engine.residentFits(1, glide), "an index past the population is not a resident");
        assertFalse(engine.residentFits(-1, glide), "nor is a negative one");
        assertTrue(Float.isFinite(engine.posL()[0]) && Float.isFinite(engine.posY()[0])
                        && Float.isFinite(engine.posD()[0]),
                "a demoted resident must still hold a finite position for its retirement fade");
    }

    @Test
    void aCarriedCrawlerIsNotPlacedOnAFloorWithNothingToStandOn() {
        // A crawler whose carried cell is no longer standable re-places — the one carry-over case
        // that cannot win. But the re-place search ends in an exhaustive fallback that returns the
        // centre of the domain when nothing is standable at all, and the bed under that centre is
        // unknown (NaN): the fish is teleported to the middle of the water holding a NaN height,
        // which separation then spreads across the shoal before anything can check. Keeping the
        // finite carried position until the adapter retires the fish is the only safe answer.
        FishSpec crab = new FishSpec(0.30f, Locomotion.BENTHIC, false, 0);
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(one(crab), 7L, 0f, 20f, water(pond(0, 3, 0, 3, 8)));
        for (int t = 0; t < 40; t++) engine.step();
        assertEquals(Locomotion.BENTHIC, engine.locomotion[0], "precondition: the pond hosts a crawler");
        float wasL = engine.posL()[0], wasY = engine.posY()[0], wasD = engine.posD()[0];

        // The same footprint of water, every column cut by the scan band: the bed is unknown
        // everywhere, so there is no floor to stand on at all.
        List<WaterDomain.Column> cut = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                cut.add(new WaterDomain.Column(x, z, new float[]{BED, BED + 7 + SURFACE}, true));
            }
        }
        engine.rebuildPreserving(one(crab), new int[]{0}, 7L, 0f, 20f, water(cut));

        assertFalse(engine.residentFits(0, crab),
                "with no floor there is nothing to stand on, so the adapter must retire the crawler");
        assertEquals(wasL, engine.posL()[0], 0f,
                "a crawler with nothing to stand on must keep its carried lateral position");
        assertEquals(wasY, engine.posY()[0], 0f,
                "a crawler with nothing to stand on must keep its carried height");
        assertEquals(wasD, engine.posD()[0], 0f,
                "a crawler with nothing to stand on must keep its carried depth");
        for (int t = 0; t < 40; t++) {
            engine.step();
            assertTrue(Float.isFinite(engine.posL()[0]) && Float.isFinite(engine.posY()[0])
                            && Float.isFinite(engine.posD()[0]),
                    "a carried crawler's position went non-finite on step " + t);
        }
    }
}

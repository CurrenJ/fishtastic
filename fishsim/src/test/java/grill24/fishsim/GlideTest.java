package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glide model's own invariants (docs/fish-sim-locomotion.md §3.3) — the set that replaces
 * {@code GLIDE}'s "never moves" row in {@link LocomotionTest} now that it has a motion model.
 *
 * <p>{@code GLIDE} is the planar swimmer model under {@link Tunables#GLIDE}, so what is worth
 * testing is what makes it a different creature, not what the planar model already guarantees:
 * that it turns like something with a wingspan, that it flies over the sand rather than through
 * the water column, and that it does not school.
 */
class GlideTest {

    private static final int TICKS = 4_000; // 200 s

    private static FishSpec ray(float length, int species) {
        return new FishSpec(length, Locomotion.GLIDE, false, species);
    }

    /** A lone tank: the Box domain, where the spatial index is inactive and the scan is brute force. */
    private static FlockEngine boxTank(FishSpec... specs) {
        FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
        engine.rebuild(specs, 4242L, 0f, 3, 0.35f, 0.3f, 20f);
        return engine;
    }

    /** A group aquarium with room to glide in. */
    private static FlockEngine groupTank(int lateral, int high, int deep, FishSpec... specs) {
        boolean[][][] occupancy = new boolean[lateral][high][deep];
        for (boolean[][] column : occupancy) {
            for (boolean[] cell : column) Arrays.fill(cell, true);
        }
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, 4242L, 0f, 20f, new VoxelDomain(occupancy));
        return engine;
    }

    /** Containment, in both domains — and never by way of the hard backstop. */
    @Test
    void glidersStayInsideTheDomain() {
        for (FlockEngine engine : new FlockEngine[]{
                boxTank(ray(0.10f, 0), ray(0.12f, 0), ray(0.09f, 1)),
                groupTank(4, 2, 2, ray(0.30f, 0), ray(0.36f, 0), ray(0.28f, 1))}) {
            for (int tick = 0; tick < TICKS; tick++) {
                engine.step();
                for (int i = 0; i < engine.count(); i++) {
                    assertTrue(engine.domain().contains(engine.posL()[i], engine.posY()[i], engine.posD()[i]),
                            "a glider left the domain at tick " + tick);
                }
            }
            assertEquals(0, engine.backstopEngagements(),
                    "soft containment let a glider reach the hard backstop");
        }
    }

    /**
     * The class's defining number: a ray cannot whip around. Sprite yaw and travel direction share
     * one rate by construction in the planar model, so bounding the sprite bounds both — and the
     * per-fish trait spread is the only thing allowed to exceed the nominal rate.
     */
    @Test
    void aGliderTurnsSlowly() {
        FlockEngine engine = groupTank(4, 2, 2, ray(0.30f, 0), ray(0.36f, 0));
        float cap = Tunables.GLIDE.turnRateDegPerTick() * (1f + Tunables.GLIDE.traitJitter()) + 1e-3f;
        float[] previous = engine.yawDeg.clone();
        float worst = 0f;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            for (int i = 0; i < engine.count(); i++) {
                worst = Math.max(worst, Math.abs(wrap(engine.yawDeg[i] - previous[i])));
                previous[i] = engine.yawDeg[i];
            }
        }
        assertTrue(worst <= cap, "a glider turned " + worst + " deg in one tick, over its " + cap + " cap");
        // The control: a cap nothing ever approaches would pass this test while proving nothing.
        assertTrue(worst > 0.5f * Tunables.GLIDE.turnRateDegPerTick(),
                "the turn cap was never actually reached (worst " + worst + ")");
    }

    /**
     * A ray flies over the terrain rather than through the water column: it holds a ride height off
     * the sand under it, measured as a fraction of the local headroom so the same creature works in
     * a lone tank's slab and in a group's several blocks.
     */
    @Test
    void aGliderRidesAboveTheFloor() {
        FlockEngine engine = groupTank(4, 3, 2, ray(0.30f, 0));
        float floor = engine.domain().minVertical();
        float headroom = engine.domain().maxVertical() - floor;
        double sum = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            sum += engine.posY()[0] - floor;
        }
        float mean = (float) (sum / TICKS);
        assertTrue(mean < 0.5f * headroom, "a glider averaged " + mean + " above the sand in "
                + headroom + " of headroom — it is using the water column, not hugging the floor");
        assertTrue(mean > 0.05f * headroom, "a glider averaged " + mean + " above the sand — it is dragging on it");
    }

    /**
     * Rays do not shoal. Same species, so a free swimmer would school with it; the glide set turns
     * alignment and cohesion off entirely and widens separation to about a body length.
     */
    @Test
    void glidersDoNotSchool() {
        FlockEngine engine = groupTank(5, 2, 3, ray(0.30f, 0), ray(0.30f, 0), ray(0.30f, 0));
        float closest = Float.MAX_VALUE;
        double sum = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            float nearest = Float.MAX_VALUE;
            for (int i = 0; i < engine.count(); i++) {
                for (int j = i + 1; j < engine.count(); j++) {
                    float dl = engine.posL()[i] - engine.posL()[j];
                    float dy = engine.posY()[i] - engine.posY()[j];
                    float dd = engine.posD()[i] - engine.posD()[j];
                    nearest = Math.min(nearest, (float) Math.sqrt(dl * dl + dy * dy + dd * dd));
                }
            }
            closest = Math.min(closest, nearest);
            sum += nearest;
        }
        float mean = (float) (sum / TICKS);
        assertTrue(mean > 0.5f * Tunables.GLIDE.separationRadius(),
                "gliders averaged " + mean + " apart — they are shoaling");
        assertTrue(closest > 0.1f, "two gliders came within " + closest + " blocks");
    }

    /** A glider travels, and travels slowly. Both halves matter: frozen and darting are both wrong. */
    @Test
    void aGliderCruises() {
        FlockEngine engine = groupTank(4, 2, 2, ray(0.30f, 0));
        float startL = engine.posL()[0], startD = engine.posD()[0];
        double sumSpeed = 0;
        float far = 0f;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            sumSpeed += engine.speed[0];
            float dl = engine.posL()[0] - startL, dd = engine.posD()[0] - startD;
            far = Math.max(far, (float) Math.sqrt(dl * dl + dd * dd));
        }
        assertTrue(far > 1.0f, "a glider got only " + far + " blocks from where it started");
        float meanSpeed = (float) (sumSpeed / TICKS);
        assertTrue(meanSpeed < Tunables.GLIDE.maxSpeed(),
                "a glider averaged " + meanSpeed + " blocks/s, at or above its own ceiling");
        assertTrue(meanSpeed < Tunables.GROUP.patrolSpeed(), "a glider averaged " + meanSpeed
                + " blocks/s — no slower than the shoal it is supposed to be gliding past");
    }

    /**
     * The bank the renderer reads is a fraction of this fish's own full lean, so a pose can author
     * its own amplitude without knowing which parameter set stepped the fish — and it is a lean,
     * not a vibration.
     *
     * <p>That second half is the one that came from a real bug: the raw {@code bank} is computed
     * from a single tick's yaw delta and therefore carries every bit of the steering noise, which
     * in game read as the ray buzzing. The bound below is on the per-tick <em>change</em>, because
     * amplitude cannot tell the two apart — a steady lean and a violent flutter have the same
     * mean. Measured before the low-pass: 0.102 per tick, reversing sign 1.7 times a second.
     */
    @Test
    void bankFractionIsNormalisedAndEarnedAndSteady() {
        FlockEngine engine = groupTank(4, 2, 2, ray(0.30f, 0), ray(0.36f, 0));
        float extreme = 0f;
        float worstChange = 0f;
        int reversals = 0;
        float[] previous = new float[engine.count()];
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            engine.interpolate(1f); // bankFraction is a render value, like renderYaw
            for (int i = 0; i < engine.count(); i++) {
                float f = engine.bankFraction(i);
                assertTrue(f >= -1f && f <= 1f, "bankFraction out of range: " + f);
                extreme = Math.max(extreme, Math.abs(f));
                if (tick > 0) {
                    worstChange = Math.max(worstChange, Math.abs(f - previous[i]));
                    if (f * previous[i] < 0f) reversals++;
                }
                previous[i] = f;
            }
        }
        assertTrue(extreme > 0.25f, "a glider never leaned into a turn (peak " + extreme + ")");
        assertTrue(worstChange < 0.05f,
                "the drawn lean jumped " + worstChange + " of full lean in one tick — that is a buzz");
        float reversalsPerSecond = reversals / (TICKS / 20f) / engine.count();
        assertTrue(reversalsPerSecond < 0.5f,
                "the drawn lean changed sign " + reversalsPerSecond + " times a second");
    }

    private static float wrap(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }
}

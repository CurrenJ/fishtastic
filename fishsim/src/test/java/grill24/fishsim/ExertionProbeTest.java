package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the read-only exertion probes the MC-side bubble emitter (docs/fish-tank-bubbles.md §3.2)
 * detects events with. Nothing inside the engine consumes these, so without this test a change
 * to how a class drives its envelope could silently stop a whole locomotion class bubbling —
 * which is exactly what happened the first time: drifters never fired because they have no
 * burst phase clock at all.
 */
class ExertionProbeTest {

    private static final int TICKS = 4_000; // 200 s

    private static FlockEngine groupTank(FishSpec... specs) {
        boolean[][][] occupancy = new boolean[3][2][1];
        for (boolean[][] column : occupancy) {
            column[0][0] = true;
            column[1][0] = true;
        }
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, 4242L, 0f, 20f, new VoxelDomain(occupancy));
        return engine;
    }

    /** Counts onsets exactly the way {@code TankBubbleEmitter} does. */
    private static int countOnsets(FlockEngine engine, int i) {
        float prevPhase = engine.burstPhase(i);
        float prevDrive = engine.burstDrive(i);
        boolean phaseClock = engine.locomotion[i] == Locomotion.FREE_SWIM || engine.locomotion[i] == Locomotion.GLIDE;
        int onsets = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            engine.step();
            boolean onset = phaseClock
                    ? engine.burstPhase(i) < prevPhase
                    : engine.burstDrive(i) >= 0.5f && prevDrive < 0.5f;
            if (onset) onsets++;
            prevPhase = engine.burstPhase(i);
            prevDrive = engine.burstDrive(i);
        }
        return onsets;
    }

    @Test
    void aSwimmerFiresOncePerBurstCycle() {
        FlockEngine engine = groupTank(new FishSpec(0.3f, Locomotion.FREE_SWIM, false, 0));
        int onsets = countOnsets(engine, 0);
        // GROUP burst period is 4 s ± jitter over 200 s.
        assertTrue(onsets >= 35 && onsets <= 70, "swimmer onsets: " + onsets);
    }

    @Test
    void aDrifterFiresOncePerPulse() {
        FlockEngine engine = groupTank(new FishSpec(0.3f, Locomotion.DRIFT, false, 0));
        float phaseBefore = engine.burstPhase(0);
        int onsets = countOnsets(engine, 0);
        assertEquals(phaseBefore, engine.burstPhase(0), "a drifter's burst phase never advances — the envelope is the signal");
        // Pulse period is 4.5 s ± jitter over 200 s.
        assertTrue(onsets >= 35 && onsets <= 55, "drifter onsets: " + onsets);
    }

    @Test
    void aCrawlerFiresOncePerScuttle() {
        FlockEngine engine = groupTank(new FishSpec(0.2f, Locomotion.BENTHIC, false, 0));
        float phaseBefore = engine.burstPhase(0);
        int onsets = countOnsets(engine, 0);
        assertEquals(phaseBefore, engine.burstPhase(0), "a crawler's burst phase never advances — the envelope is the signal");
        assertTrue(onsets > 0, "a crawler never started a scuttle in 200 s");
    }
}

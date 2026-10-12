package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground's in-view beats (docs/fish-companions-benthic-follow.md §5, §18): a racer overtakes
 * on a straight walk and is seen more for it, on equal seeds with the beats switched off, and no
 * beat costs a fade; a run with no beat in it is the run without them.
 */
class BenthicBeatTest {

    @AfterEach
    void restore() {
        GroundMind.beatsEnabled = true;
    }

    private static int[] walk(String script, long seed, Temperament tm, boolean beats) {
        GroundMind.beatsEnabled = beats;
        CompanionRun run = CompanionRun.benthic(script, seed, 0.3f, tm, GroundGait.CRAWL, CompanionTunables.defaults(), true);
        run.run(2400);
        int walking = 0, seen = 0;
        for (CompanionRun.Sample s : run.samples) {
            if (s.owner().horizontalSpeed() <= 1) continue;
            walking++;
            if (s.seen()) seen++;
        }
        return new int[] {walking, seen, run.agent.overtakes() + run.agent.roadsides(), (int) run.groundMetrics().fades()};
    }

    @Test
    void aRacerOvertakesAndIsSeenMoreWithoutFading() {
        int seenOff = 0, seenOn = 0, beats = 0;
        for (long seed = 1; seed <= 3; seed++) {
            int[] off = walk("walk_flat", seed, Temperament.SPRINTER, false);
            int[] on = walk("walk_flat", seed, Temperament.SPRINTER, true);
            seenOff += off[1];
            seenOn += on[1];
            beats += on[2];
            assertEquals(off[3], on[3], "seed " + seed + ": a beat cost a fade");
        }
        // Measured over twelve seeds: more seen while walking in all twelve (most of it the hand
        // ride, which the beats no longer crowd out), 3-9 beats a run; seeds 1-3 have 18 beats.
        assertTrue(beats >= 9, "beats on three straight walks: " + beats);
        assertTrue(seenOn > seenOff, "seen while walking, beats on " + seenOn + " vs off " + seenOff);
    }

    @Test
    void aRunWithNoBeatIsTheRunWithoutThem() {
        // The pillared cellar never offers an open straight way: no beat starts, and the beats'
        // own draws leave the rest of the run bitwise as it was.
        int[] off = walk("low_ceiling", 1, Temperament.BASELINE, false);
        int[] on = walk("low_ceiling", 1, Temperament.BASELINE, true);
        assertEquals(0, on[2]);
        assertEquals(off[1], on[1]);
        assertEquals(off[3], on[3]);
    }
}

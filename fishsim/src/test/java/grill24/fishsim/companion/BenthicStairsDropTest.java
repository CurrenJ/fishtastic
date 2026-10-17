package grill24.fishsim.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenthicStairsDropTest {
    @Test
    void refusingUnrequestedEdgeDropsNeverAddsStairFades() {
        try {
            for (BenthicGroup.Formation formation : BenthicGroup.Formation.values()) {
                float length = formation == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
                long oldFades = 0, guardedFades = 0;
                for (long seed = 1; seed <= 20; seed++) {
                    GroundMind.formationDropGuard = false;
                    GroupRun old = new GroupRun("stairs_up_down", seed, length, Temperament.BASELINE, formation).run(null);
                    GroundMind.formationDropGuard = true;
                    GroupRun guarded = new GroupRun("stairs_up_down", seed, length, Temperament.BASELINE, formation).run(null);
                    String context = formation + " seed=" + seed;
                    assertTrue(guarded.fades <= old.fades, context);
                    assertTrue(guarded.deepTicks <= 72, context + " stacked=" + guarded.deepTicks);
                    oldFades += old.fades;
                    guardedFades += guarded.fades;
                }
                assertTrue(guardedFades < oldFades, formation + " should recover some stair routes");
            }
        } finally {
            GroundMind.formationDropGuard = true;
        }
    }
}

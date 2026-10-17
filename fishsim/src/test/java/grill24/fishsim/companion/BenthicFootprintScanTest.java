package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact trajectory ablation for the beat planner's repeated column checks. */
class BenthicFootprintScanTest {
    @Test void repeatedColumnsPreserveTrajectories() {
        long before=0, after=0;
        try {
            GroundMind.countBeatFootprints=true;
            for(String script : new String[]{"walk_flat","walk_meander","stairs_up_down","low_ceiling","step_gaps","slab_path"})
                for(float length : new float[]{.3f,.6f}) for(long seed=1;seed<=12;seed++) {
                    CompanionRun[] runs=new CompanionRun[2];
                    for(int arm=0;arm<2;arm++) {
                        GroundMind.skipRepeatedBeatFootprints=arm==1;
                        GroundMind.beatFootprintChecks=0;
                        runs[arm]=CompanionRun.benthic(script,seed,length,Temperament.named("sprinter"),GroundGait.CRAWL,CompanionTunables.defaults(),true);
                        runs[arm].run(2400);
                        if(arm==0)before+=GroundMind.beatFootprintChecks; else after+=GroundMind.beatFootprintChecks;
                    }
                    assertEquals(runs[0].samples,runs[1].samples,script+" length="+length+" seed="+seed);
                    assertEquals(runs[0].metrics(),runs[1].metrics());
                    assertEquals(runs[0].groundMetrics(),runs[1].groundMetrics());
                }
            System.out.printf("FOOTPRINT checks before=%d after=%d reduction=%.1f%%%n",before,after,100.0*(before-after)/before);
            assertTrue(after<before/2,"Repeated-column reuse should remove more than half the checks");
        } finally {
            GroundMind.skipRepeatedBeatFootprints=true;
            GroundMind.countBeatFootprints=false;
            GroundMind.beatFootprintChecks=0;
        }
    }
}

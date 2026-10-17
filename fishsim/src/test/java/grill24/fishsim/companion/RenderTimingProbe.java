package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.render.CompanionRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayDeque;

/** Measurement: viewer-size frame cost and sim step cost. Run with -PsimStdout. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class RenderTimingProbe {
    @Test
    void time() {
        for (String script : new String[]{"walk", "forest", "corridor"}) {
            CompanionRun run = new CompanionRun(script, 1, CompanionTunables.defaults(), false);
            run.run(200);
            LocalVoxels cache = new LocalVoxels();
            long t0 = System.nanoTime();
            int frames = 120;
            for (int i = 0; i < frames; i++) {
                run.tick();
                run.agent.interpolate(0.5f);
                cache.update(run.scene, run.owner.eye());
                CompanionRenderer.firstPerson(960, 540, run.owner, cache, run.agent, true);
                CompanionRenderer.topView(400, 540, run.owner, cache, run.agent, run.tunables, new ArrayDeque<>(), 30);
            }
            double ms = (System.nanoTime() - t0) / 1e6 / frames;
            long s0 = System.nanoTime();
            run.run(2000);
            double stepUs = (System.nanoTime() - s0) / 1e3 / 2000;
            System.out.printf("%s: frame %.1f ms, sim step %.1f us%n", script, ms, stepUs);
        }
    }
}

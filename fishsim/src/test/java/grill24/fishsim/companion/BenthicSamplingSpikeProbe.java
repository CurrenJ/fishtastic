package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.HashMap;
import java.util.Map;

/** Which code asks for the blocks in a sampling spike (BenthicSamplingProbe's worst ticks). -PsimStdout. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BenthicSamplingSpikeProbe {

    @Test
    void spikeCallers() {
        OwnerScript owner = OwnerScript.named("walk_flat", 1);
        Map<String, Integer> callers = new HashMap<>();
        boolean[] recording = {false};
        long[] samples = {0};
        BlockQuery[] inner = {null};
        BlockQuery counting = new BlockQuery() {
            @Override public boolean solid(int x, int y, int z) {
                samples[0]++;
                if (recording[0]) {
                    StringBuilder key = new StringBuilder();
                    int depth = 0;
                    for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                        String c = e.getClassName();
                        if (!c.startsWith("grill24.fishsim.companion.") || c.contains("Probe")
                                || c.endsWith("LocalVoxels")) continue;
                        key.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName())
                                .append(':').append(e.getLineNumber()).append(" < ");
                        if (++depth == 4) break;
                    }
                    callers.merge(key.toString(), 1, Integer::sum);
                }
                return inner[0].solid(x, y, z);
            }
            @Override public boolean water(int x, int y, int z) { return inner[0].water(x, y, z); }
            @Override public float top(int x, int y, int z) { return inner[0].top(x, y, z); }
        };
        CompanionWorld w = owner.nextWorld();
        inner[0] = w.blocks();
        CompanionAgent agent = new CompanionAgent(CompanionTunables.defaults(), 1, Locomotion.BENTHIC, 0.6f,
                Temperament.BASELINE, GroundGait.CRAWL,
                new CompanionWorld(w.owner(), counting, w.points(), w.held(), w.hurt()));
        for (int tick = 1; tick < 2400; tick++) {
            w = owner.nextWorld();
            inner[0] = w.blocks();
            recording[0] = tick == 158 || tick == 1036;
            long before = samples[0];
            agent.step(new CompanionWorld(w.owner(), counting, w.points(), w.held(), w.hurt()));
            if (recording[0]) System.out.println("TICK " + tick + " samples=" + (samples[0] - before));
        }
        callers.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12)
                .forEach(e -> System.out.println("CALLER " + e.getValue() + "  " + e.getKey()));
    }
}

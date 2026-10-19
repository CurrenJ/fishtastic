package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who asks for the blocks on every sampling spike (ticks over {@code -PsweepTicks}, default 60)
 * across BenthicSamplingProbe's scripts, for the solo ground configs: a first pass finds the
 * spike ticks, a deterministic replay records the callers on them. -PsimStdout; -PsweepSeeds=3.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BenthicSpikeAttributionProbe {

    private record Cfg(String name, GroundGait gait, float length, Temperament tm) {}

    private static long run(Cfg c, String script, long seed, List<Integer> spikes, int threshold,
                            Map<String, Integer> callers, boolean record) {
        OwnerScript owner = OwnerScript.named(script, seed);
        long[] samples = {0};
        boolean[] recording = {false};
        BlockQuery[] inner = {null};
        BlockQuery counting = new BlockQuery() {
            @Override public boolean solid(int x, int y, int z) {
                samples[0]++;
                if (recording[0]) {
                    StringBuilder key = new StringBuilder();
                    int depth = 0;
                    for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                        String cl = e.getClassName();
                        if (!cl.startsWith("grill24.fishsim.companion.") || cl.contains("Probe")
                                || cl.endsWith("LocalVoxels")) continue;
                        key.append(cl.substring(cl.lastIndexOf('.') + 1)).append('.').append(e.getMethodName())
                                .append(':').append(e.getLineNumber()).append(" < ");
                        if (++depth == 3) break;
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
        CompanionAgent agent = new CompanionAgent(CompanionTunables.defaults(), seed, Locomotion.BENTHIC, c.length(),
                c.tm(), c.gait(), new CompanionWorld(w.owner(), counting, w.points(), w.held(), w.hurt()));
        long worst = 0;
        for (int tick = 1; tick < 2400; tick++) {
            w = owner.nextWorld();
            inner[0] = w.blocks();
            recording[0] = record && spikes.contains(tick);
            long before = samples[0];
            agent.step(new CompanionWorld(w.owner(), counting, w.points(), w.held(), w.hurt()));
            long s = samples[0] - before;
            worst = Math.max(worst, s);
            if (!record && s > threshold) spikes.add(tick);
        }
        return worst;
    }

    @Test
    void attribute() {
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "3"));
        int threshold = Integer.parseInt(System.getProperty("sweepTicks", "60"));
        List<Cfg> cfgs = List.of(new Cfg("crawl 0.30", GroundGait.CRAWL, 0.3f, Temperament.BASELINE),
                new Cfg("crawl 0.60", GroundGait.CRAWL, 0.6f, Temperament.BASELINE),
                new Cfg("ripple 0.35", GroundGait.RIPPLE, 0.35f, Temperament.BASELINE),
                new Cfg("sprinter 0.30", GroundGait.CRAWL, 0.3f, Temperament.SPRINTER));
        List<String> scripts = List.of("walk_flat", "walk_meander", "stairs_up_down", "step_gaps", "slab_path", "sprint_away");
        for (Cfg c : cfgs) {
            Map<String, Integer> callers = new HashMap<>();
            int spikeTicks = 0;
            long worst = 0;
            for (String script : scripts) {
                for (long seed = 1; seed <= seeds; seed++) {
                    List<Integer> spikes = new ArrayList<>();
                    worst = Math.max(worst, run(c, script, seed, spikes, threshold, callers, false));
                    spikeTicks += spikes.size();
                    if (!spikes.isEmpty()) run(c, script, seed, spikes, threshold, callers, true);
                }
            }
            long total = callers.values().stream().mapToLong(Integer::longValue).sum();
            System.out.printf("SPIKES %-13s ticks>%d: %d  worst %d  samples on them %d%n", c.name(), threshold, spikeTicks, worst, total);
            callers.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
                    .forEach(e -> System.out.printf("   %5d (%4.1f%%) %s%n", e.getValue(), 100.0 * e.getValue() / Math.max(1, total), e.getKey()));
        }
    }
}

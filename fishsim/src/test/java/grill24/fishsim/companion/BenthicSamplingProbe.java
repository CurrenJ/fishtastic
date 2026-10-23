package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Arrays;
import java.util.List;

/**
 * Block samples per tick for ground benthics, headless (docs/fish-companions.md):
 * the same count the game's overlay shows ({@code LevelBlocks.samples}: one per {@code solid} call,
 * {@code top} sharing that collision query), against the sim plan's 25 samples/tick budget per
 * companion. A swimmer and the no-gait percher (the game's path until now) are the references.
 * Run with -PsimStdout; {@code -PsweepSeeds=3}.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BenthicSamplingProbe {

    /** Counts what the game would sample: each solid query once; top reuses it there. */
    private static final class Counting implements BlockQuery {
        BlockQuery inner;
        long samples;

        @Override public boolean solid(int x, int y, int z) {
            samples++;
            return inner.solid(x, y, z);
        }

        @Override public boolean water(int x, int y, int z) { return inner.water(x, y, z); }

        @Override public float top(int x, int y, int z) { return inner.top(x, y, z); }
    }

    private record Cfg(String name, Locomotion loco, GroundGait gait, float length, int count, BenthicGroup.Formation formation) {}

    @Test
    void sampling() {
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "3"));
        List<Cfg> cfgs = List.of(
                new Cfg("swimmer 0.30", Locomotion.FREE_SWIM, null, 0.3f, 1, null),
                new Cfg("percher 0.30 (old game path)", Locomotion.BENTHIC, null, 0.3f, 1, null),
                new Cfg("crawl solo 0.30", Locomotion.BENTHIC, GroundGait.CRAWL, 0.3f, 1, null),
                new Cfg("crawl solo 0.60 (spider crab)", Locomotion.BENTHIC, GroundGait.CRAWL, 0.6f, 1, null),
                new Cfg("ripple solo 0.35 (plaice)", Locomotion.BENTHIC, GroundGait.RIPPLE, 0.35f, 1, null),
                new Cfg("line of 3, 0.25 (starfish)", Locomotion.BENTHIC, GroundGait.CRAWL, 0.25f, 3, BenthicGroup.Formation.LINE),
                new Cfg("crowd of 3, 0.25 (sea slugs)", Locomotion.BENTHIC, GroundGait.CRAWL, 0.25f, 3, BenthicGroup.Formation.CROWD));
        List<String> scripts = List.of("walk_flat", "walk_meander", "stairs_up_down", "step_gaps", "slab_path", "sprint_away");
        for (Cfg c : cfgs) {
            long total = 0;
            int ticks = 0;
            long worst = 0;
            long[] all = new long[scripts.size() * seeds * 2399];
            int n = 0;
            for (String script : scripts) {
                for (long seed = 1; seed <= seeds; seed++) {
                    OwnerScript owner = OwnerScript.named(script, seed);
                    Counting blocks = new Counting();
                    CompanionWorld w = owner.nextWorld();
                    blocks.inner = w.blocks();
                    CompanionWorld world = new CompanionWorld(w.owner(), blocks, w.points(), w.held(), w.hurt());
                    BenthicGroup group = null;
                    CompanionAgent agent = null;
                    if (c.count() > 1) group = new BenthicGroup(CompanionTunables.defaults(), seed, c.length(),
                            Temperament.BASELINE, c.gait(), c.count(), c.formation(), world);
                    else agent = new CompanionAgent(CompanionTunables.defaults(), seed, c.loco(), c.length(),
                            Temperament.BASELINE, c.gait(), world);
                    for (int tick = 1; tick < 2400; tick++) {
                        w = owner.nextWorld();
                        blocks.inner = w.blocks();
                        world = new CompanionWorld(w.owner(), blocks, w.points(), w.held(), w.hurt());
                        long before = blocks.samples;
                        if (group != null) group.step(world);
                        else agent.step(world);
                        long s = blocks.samples - before;
                        total += s;
                        ticks++;
                        if (s > 300) {
                            CompanionAgent lead = group != null ? group.leader() : agent;
                            System.out.printf("SPIKE %s %s#%d t=%d samples=%d land=%s perch=%s teleported=%s lag=%.1f reach=%s fade=%s%n",
                                    c.name(), script, seed, tick, s, lead.landMode(), lead.perchState(),
                                    w.owner().teleported(), lead.groundLag(), lead.groundReachable(), lead.fade());
                        }
                        worst = Math.max(worst, s);
                        all[n++] = s;
                    }
                }
            }
            long[] sorted = Arrays.copyOf(all, n);
            Arrays.sort(sorted);
            long p99 = sorted[(int) (n * 0.99)], p999 = sorted[(int) (n * 0.999)];
            int over = 0;
            for (int i = 0; i < n; i++) if (sorted[i] > 25L * c.count()) over++;
            System.out.printf("SAMPLES %-32s mean %6.2f/tick  p99 %4d  p99.9 %4d  worst %5d  ticks over %d (25 x %d): %.2f%%%n",
                    c.name(), total / (double) ticks, p99, p999, worst, 25 * c.count(), c.count(), 100.0 * over / n);
        }
    }
}

package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

/**
 * Diagnostic for the ground's in-view beats (docs/fish-companions.md): how often
 * each beat starts, and the seen share while walking against the no-gait percher. Run with
 * -PsimStdout; {@code -PsweepSeeds=3}.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BenthicBeatProbe {

    /** Named runs, beats off then on: their beats, when the first began, and the run's metrics. */
    @Test
    void cases() {
        record Case(String script, long seed, float length) {}
        List<Case> cases = List.of(new Case(System.getProperty("trace", "low_ceiling"),
                Long.parseLong(System.getProperty("tracePos", "1")), Float.parseFloat(System.getProperty("sweepLength", "0.3"))));
        for (Case c : cases) {
            for (boolean on : new boolean[] {false, true}) {
                GroundMind.beatsEnabled = on;
                CompanionRun run = CompanionRun.benthic(c.script(), c.seed(), c.length(), Temperament.BASELINE,
                        GroundGait.CRAWL, CompanionTunables.defaults(), true);
                int first = -1, beats = 0;
                String prev = null;
                for (int i = 0; i < 2400; i++) {
                    run.tick();
                    String now = run.agent.beat();
                    if (now != null && prev == null) {
                        beats++;
                        if (first < 0) first = i;
                        System.out.printf("  %s#%d %s at %d%n", c.script(), c.seed(), now, i);
                    }
                    if (on && first >= 0 && i < first + 90) {
                        Vec p = run.agent.position(), f = run.owner.feet(), ov = run.owner.velocity();
                        System.out.printf("    t=%4d %-8s %-12s pos %6.2f %6.2f %6.2f own %6.2f %6.2f %6.2f ov %5.2f %5.2f d %4.2f lag %4.2f %s%n",
                                i, now, run.agent.landMode(), p.x(), p.y(), p.z(), f.x(), f.y(), f.z(), ov.x(), ov.z(),
                                p.sub(f).horizontalLength(), run.agent.groundLag(), run.agent.groundReachable() ? "R" : "-");
                    }
                    prev = now;
                }
                System.out.printf("CASE %-3s %s#%d len=%.2f beats=%d first=%d | %s | %s%n", on ? "on" : "off", c.script(),
                        c.seed(), c.length(), beats, first, run.groundMetrics().summary(), run.metrics().summary());
            }
        }
        GroundMind.beatsEnabled = true;
    }

    /**
     * Beats off against on over many seeds, for the scripts a single run failed on: whether the
     * beats move the tails, or just resample them. -PsweepSeeds=20.
     */
    @Test
    void tails() {
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "20"));
        record Cfg(String script, float length) {}
        for (Cfg c : List.of(new Cfg("low_ceiling", 0.3f), new Cfg("jumping", 0.3f), new Cfg("stairs_up_down", 0.6f),
                new Cfg("stairs_up_down", 0.3f), new Cfg("walk_meander", 0.3f))) {
            String only = System.getProperty("sweep");
            if (only != null && !only.contains(c.script())) continue;
            for (boolean on : new boolean[] {false, true}) {
                GroundMind.beatsEnabled = on;
                int beats = 0, fades = 0, leash = 0, insideOver = 0, unseenOver = 0, backstops = 0;
                double inside = 0, unseen = 0, impact = 0;
                for (long seed = 1; seed <= seeds; seed++) {
                    CompanionRun run = CompanionRun.benthic(c.script(), seed, c.length(), Temperament.BASELINE,
                            GroundGait.CRAWL, CompanionTunables.defaults(), true);
                    run.run(2400);
                    CompanionRun.Metrics m = run.metrics();
                    beats += run.agent.overtakes() + run.agent.roadsides();
                    fades += (int) run.groundMetrics().fades();
                    leash += m.leashEvents() > 0 ? 1 : 0;
                    backstops += (int) m.backstops();
                    inside = Math.max(inside, m.insideOwnerTicks() / (double) m.ticks());
                    insideOver += m.insideOwnerTicks() / (double) m.ticks() > 0.01 ? 1 : 0;
                    unseen = Math.max(unseen, m.longestUnseenSeconds());
                    unseenOver += m.longestUnseenSeconds() > 30 ? 1 : 0;
                    impact = Math.max(impact, m.maxImpact());
                }
                System.out.printf("TAILS %-3s %-15s len=%.2f seeds=%d beats=%3d fades=%2d leashRuns=%d backstops=%3d "
                                + "inside max=%.3f runs>1%%=%d unseen max=%.1fs runs>30s=%d impact max=%.2f%n",
                        on ? "on" : "off", c.script(), c.length(), seeds, beats, fades, leash, backstops,
                        inside, insideOver, unseen, unseenOver, impact);
            }
        }
        GroundMind.beatsEnabled = true;
    }

    /** Seeds whose inside-owner share passes 1% with beats on: each inside tick, and the beat state around it. */
    @Test
    void inside() {
        String script = System.getProperty("trace", "low_ceiling");
        float length = Float.parseFloat(System.getProperty("sweepLength", "0.3"));
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "20"));
        for (long seed = 1; seed <= seeds; seed++) {
            CompanionRun run = CompanionRun.benthic(script, seed, length, Temperament.BASELINE, GroundGait.CRAWL,
                    CompanionTunables.defaults(), true);
            String[] beat = new String[2400];
            int[] since = new int[2400];
            int last = -100000;
            int from = Integer.parseInt(System.getProperty("sweepTicks", "-1"));
            for (int i = 0; i < 2400; i++) {
                run.tick();
                if (from >= 0 && i >= from && i < from + 40) {
                    CompanionAgent a = run.agent;
                    Vec p = a.position(), f = run.owner.feet();
                    System.out.printf("  t=%4d %-12s %-14s pos %6.2f %6.2f %6.2f own %6.2f %6.2f %6.2f yaw %6.1f d %4.2f close=%s in=%s target=%s%n",
                            i, a.landMode(), a.perchState(), p.x(), p.y(), p.z(), f.x(), f.y(), f.z(), run.owner.yawDeg(),
                            p.sub(f).horizontalLength(), a.comingClose(), CompanionRun.insideBody(run.owner, p), a.target());
                }
                beat[i] = run.agent.beat();
                if (beat[i] != null) last = i;
                since[i] = i - last;
            }
            int inside = 0;
            StringBuilder ticks = new StringBuilder();
            for (int i = 0; i < 2400; i++) {
                CompanionRun.Sample s = run.samples.get(i);
                if (!CompanionRun.insideBody(s.owner(), s.pos())) continue;
                inside++;
                ticks.append(String.format(" %d[%s %s +%d]", i, s.land(), beat[i], since[i]));
            }
            if (inside > 24) System.out.printf("INSIDE %s#%d %d:%s%n", script, seed, inside, ticks);
        }
    }

    /** Per seed, beats off against on: walking ticks seen, and the beats. -Ptrace=script -PsweepTemperament -PsweepLength. */
    @Test
    void perSeed() {
        String script = System.getProperty("trace", "walk_flat");
        Temperament tm = Temperament.named(System.getProperty("sweepTemperament", "sprinter"));
        float length = Float.parseFloat(System.getProperty("sweepLength", "0.3"));
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "12"));
        int up = 0, down = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            int[] r = new int[4];
            int[] hand = new int[2];
            for (int k = 0; k < 2; k++) {
                GroundMind.beatsEnabled = k == 1;
                CompanionRun run = CompanionRun.benthic(script, seed, length, tm, GroundGait.CRAWL, CompanionTunables.defaults(), true);
                run.run(2400);
                for (CompanionRun.Sample s : run.samples) {
                    if (s.owner().horizontalSpeed() > 1 && s.seen()) r[k]++;
                    if (s.owner().horizontalSpeed() > 1 && s.seen() && s.riding()) hand[k]++;
                }
                if (k == 1) r[2] = run.agent.overtakes() + run.agent.roadsides();
                if (k == 1) r[3] = run.agent.climbs();
            }
            if (r[1] > r[0]) up++; else if (r[1] < r[0]) down++;
            System.out.printf("SEED %2d off %4d (riding %4d) on %4d (riding %4d) beats %2d%n", seed, r[0], hand[0], r[1], hand[1], r[2]);
        }
        System.out.printf("SEEDS up %d down %d%n", up, down);
        GroundMind.beatsEnabled = true;
    }

    @Test
    void beats() {
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "3"));
        String trace = System.getProperty("trace");
        if (trace != null) {
            // -Ptrace=script -PsweepTemperament=name -PsweepLength=0.3 -PtracePos=seed: one line per beat.
            Temperament tm = Temperament.named(System.getProperty("sweepTemperament", "baseline"));
            CompanionRun run = CompanionRun.benthic(trace, Long.parseLong(System.getProperty("tracePos", "1")),
                    Float.parseFloat(System.getProperty("sweepLength", "0.3")), tm, GroundGait.CRAWL,
                    CompanionTunables.defaults(), true);
            String beat = null;
            int start = 0, seenIn = 0, seenOut = 0, onOwner = 0;
            for (int i = 0; i < 2400; i++) {
                run.tick();
                CompanionAgent a = run.agent;
                String now = a.beat();
                if (now != null && beat == null) { start = i; seenIn = 0; }
                if (now != null && a.seen()) seenIn++;
                if (now != null && i < 400) {
                    Vec rel = a.position().sub(run.owner.feet()), fw = run.owner.flatForward();
                    System.out.printf("  t=%4d %-8s ahead %5.2f side %5.2f v %4.2f own %4.2f seen=%s pitch %.0f%n", i, now,
                            rel.horizontal().dot(fw), rel.horizontal().dot(run.owner.flatRight()),
                            a.velocity().horizontalLength(), run.owner.horizontalSpeed(), a.seen(), run.owner.pitchDeg());
                }
                if (now == null && a.seen()) seenOut++;
                if (a.onOwner()) onOwner++;
                if (now == null && beat != null) {
                    System.out.printf("BEAT %-8s t=%4d..%4d (%.1fs) seen %.1fs%n", beat, start, i, (i - start) / 20.0, seenIn / 20.0);
                }
                beat = now;
            }
            System.out.printf("OUTSIDE seen %.1fs, on owner %.1fs, climbs %d%n", seenOut / 20.0, onOwner / 20.0, run.agent.climbs());
            return;
        }
        for (Temperament tm : List.of(Temperament.BASELINE, Temperament.SPRINTER)) {
            for (float length : new float[] {0.12f, 0.3f, 0.6f}) {
                for (String script : List.of("walk_flat", "walk_meander", "stairs_up_down", "sprint_away")) {
                  for (boolean on : new boolean[] {false, true}) {
                    GroundMind.beatsEnabled = on;
                    int over = 0, road = 0, walking = 0, seen = 0, perchSeen = 0, fades = 0;
                    for (long seed = 1; seed <= seeds; seed++) {
                        CompanionRun ground = CompanionRun.benthic(script, seed, length, tm, GroundGait.CRAWL,
                                CompanionTunables.defaults(), true);
                        CompanionRun perch = CompanionRun.benthic(script, seed, length, tm, null,
                                CompanionTunables.defaults(), true);
                        ground.run(2400);
                        perch.run(2400);
                        over += ground.agent.overtakes();
                        road += ground.agent.roadsides();
                                                fades += ground.groundMetrics().fades();
                        for (int tick = 0; tick < 2400; tick++) {
                            if (ground.samples.get(tick).owner().horizontalSpeed() <= 1) continue;
                            walking++;
                            if (ground.samples.get(tick).seen()) seen++;
                            if (perch.samples.get(tick).seen()) perchSeen++;
                        }
                    }
                    System.out.printf("BEATS %-3s %-9s len=%.2f %-15s overtake=%2d roadside=%2d fades=%d seen=%5.2f%% perch=%5.2f%%%n",
                            on ? "on" : "off", tm.name(), length, script, over, road, fades,
                            100.0 * seen / Math.max(1, walking), 100.0 * perchSeen / Math.max(1, walking));
                  }
                }
                GroundMind.beatsEnabled = true;
            }
        }
    }
}

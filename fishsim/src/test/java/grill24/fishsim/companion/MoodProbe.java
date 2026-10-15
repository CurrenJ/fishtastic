package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.MoodScenes;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic (plan §5 Phase 5): each mood preset against no mood on equal seeds, pooled over the
 * swimmer scripts, per class: seen %, fades, speed, how often its pose shows the mood, laps and
 * basks. Run with -PsimStdout; {@code -PsweepSeeds=n} for more seeds.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class MoodProbe {

    static final List<Locomotion> CLASSES = List.of(Locomotion.FREE_SWIM, Locomotion.GLIDE, Locomotion.BENTHIC,
            Locomotion.ANCHORED, Locomotion.DRIFT);

    record Pooled(double seen, long fades, long freeFades, double speed, double moodPose, long laps, long basks,
                  double rest, double meanHeight) {}

    /** Scripts that fade the fish on purpose (the owner outruns it); the rest are unforced fades. */
    static final java.util.Set<String> FORCED = java.util.Set.of("teleport", "elytra", "creative_flight");

    static Pooled pool(Locomotion c, MoodScenes.Preset preset, int seeds, List<String> scripts) {
        double seen = 0, speed = 0, pose = 0, rest = 0, height = 0;
        long fades = 0, freeFades = 0, laps = 0, basks = 0;
        int n = 0;
        for (String script : scripts) {
            for (long seed = 1; seed <= seeds; seed++) {
                CompanionRun run = new CompanionRun(script, seed, c, CompanionTunables.defaults(), true);
                if (preset != null) MoodScenes.apply(run, preset, run.owner.feet());
                int restTicks = 0, poseTicks = 0, baskTicks = 0;
                double h = 0;
                boolean basking = false;
                for (int i = 0; i < 2400; i++) {
                    run.run(1);
                    CompanionAgent a = run.agent;
                    if (a.resting()) restTicks++;
                    PoseHint p = a.poseHint();
                    if (p == PoseHint.SHIVER || p == PoseHint.MOON_GAZE) poseTicks++;
                    if (a.basking() && !basking) basks++;
                    basking = a.basking();
                    h += a.position().y() - run.owner.chest().y();
                }
                CompanionRun.Metrics m = run.metrics();
                seen += m.seenFraction();
                fades += m.leashEvents();
                if (!FORCED.contains(script)) freeFades += m.leashEvents();
                speed += m.meanSpeed();
                pose += poseTicks / 2400.0;
                rest += restTicks / 2400.0;
                height += h / 2400;
                laps += run.agent.laps();
                n++;
            }
        }
        return new Pooled(seen / n, fades, freeFades, speed / n, pose / n, laps, basks, rest / n, height / n);
    }

    @Test
    void moods() {
        int seeds = Integer.getInteger("sweepSeeds", 3);
        List<String> scripts = new ArrayList<>(OwnerScript.NAMES);
        for (Locomotion c : CLASSES) {
            Pooled base = pool(c, null, seeds, scripts);
            System.out.println(line(c, "none", base, base));
            for (MoodScenes.Preset p : MoodScenes.ALL) System.out.println(line(c, p.name(), pool(c, p, seeds, scripts), base));
        }
    }

    /** Unforced fades per script, mood against none, where they differ. -PsweepClass=BENTHIC -PtweakList=warm,glum */
    @Test
    void fadesByScript() {
        String cls = System.getProperty("sweepClass");
        if (cls == null) return;
        Locomotion c = Locomotion.valueOf(cls);
        int seeds = Integer.getInteger("sweepSeeds", 10);
        for (String name : System.getProperty("tweakList", "warm").split(",")) {
            MoodScenes.Preset p = MoodScenes.named(name);
            for (String script : OwnerScript.NAMES) {
                if (FORCED.contains(script)) continue;
                StringBuilder seedsHit = new StringBuilder();
                long base = 0, mood = 0;
                for (long seed = 1; seed <= seeds; seed++) {
                    CompanionRun a = new CompanionRun(script, seed, c, CompanionTunables.defaults(), true);
                    a.run(2400);
                    CompanionRun b = new CompanionRun(script, seed, c, CompanionTunables.defaults(), true);
                    MoodScenes.apply(b, p, b.owner.feet());
                    b.run(2400);
                    base += a.agent.leashEvents();
                    mood += b.agent.leashEvents();
                    if (a.agent.leashEvents() != b.agent.leashEvents()) seedsHit.append(seed).append(':')
                            .append(a.agent.leashEvents()).append("->").append(b.agent.leashEvents()).append(' ');
                }
                if (base != mood) System.out.printf("FADES %s %-6s %-16s %d -> %d  %s%n", c, name, script, base, mood, seedsHit);
            }
        }
    }

    /** Unforced fades per script over many seeds, for one class, no mood and each mood. -PsweepClass=BENTHIC -PsweepSeeds=40 */
    @Test
    void fadeRates() {
        String cls = System.getProperty("sweepClass");
        if (cls == null || System.getProperty("tweakList") != null) return;
        Locomotion c = Locomotion.valueOf(cls);
        int seeds = Integer.getInteger("sweepSeeds", 40);
        List<MoodScenes.Preset> presets = new ArrayList<>();
        presets.add(null);
        presets.addAll(MoodScenes.ALL);
        StringBuilder head = new StringBuilder(String.format("RATE %-16s", "script"));
        for (MoodScenes.Preset p : presets) head.append(String.format(" %7s", p == null ? "none" : p.name()));
        System.out.println(head);
        long[] totals = new long[presets.size()];
        for (String script : OwnerScript.NAMES) {
            if (FORCED.contains(script)) continue;
            StringBuilder row = new StringBuilder(String.format("RATE %-16s", script));
            boolean any = false;
            for (int k = 0; k < presets.size(); k++) {
                long fades = 0;
                for (long seed = 1; seed <= seeds; seed++) {
                    CompanionRun r = new CompanionRun(script, seed, c, CompanionTunables.defaults(), true);
                    if (presets.get(k) != null) MoodScenes.apply(r, presets.get(k), r.owner.feet());
                    r.run(2400);
                    fades += r.agent.leashEvents();
                }
                totals[k] += fades;
                any |= fades > 0;
                row.append(String.format(" %7d", fades));
            }
            if (any) System.out.println(row);
        }
        StringBuilder tot = new StringBuilder(String.format("RATE %-16s", "TOTAL"));
        for (long t : totals) tot.append(String.format(" %7d", t));
        System.out.println(tot);
    }

    static String line(Locomotion c, String name, Pooled p, Pooled base) {
        return String.format(Locale.ROOT,
                "MOOD %-9s %-6s seen=%.1f%% (%+.1f) fades=%d (%+d) unforced=%d (%+d) speed=%.2f (%+.0f%%) pose=%.1f%% laps=%d basks=%d rest=%.1f%% height=%+.2f",
                c, name, p.seen * 100, (p.seen - base.seen) * 100, p.fades, p.fades - base.fades, p.freeFades, p.freeFades - base.freeFades, p.speed,
                (p.speed / base.speed - 1) * 100, p.moodPose * 100, p.laps, p.basks, p.rest * 100, p.meanHeight);
    }
}

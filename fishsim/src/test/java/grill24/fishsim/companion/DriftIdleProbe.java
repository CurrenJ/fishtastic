package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Diagnostic: how busy a drifter is while its owner idles (docs/fish-companions-drift.md §13.7) —
 * moves (new spots) per minute, mean and p90 speed, path length per minute, strokes per minute and
 * the mean velocity change per stroke, all over the ticks its owner has stood still for a second or
 * more; the same over the rested ticks (SETTLE) alone; and a FREE_SWIM reference on the same seeds.
 * Run with -PsimStdout.
 *
 * <pre>
 *   -Psweep=idle_fixed,...         scripts (default: the idle set)
 *   -PtweakA / -PtweakB            tunables per side, as DriftSweepProbe
 *   -PsweepTemperament=ghost       temperament (default baseline)
 *   -PsweepSeeds=8 -PsweepTicks=2400 -PsweepPerSeed=1
 *   -PidleRef=0                    skip the FREE_SWIM reference
 * </pre>
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class DriftIdleProbe {

    static final String[] IDLE = {"idle_fixed", "idle_looking", "camera_sweep", "held_items", "cat_lounging",
            "item_drops", "companion_passes"};

    @Test
    void idle() {
        String only = System.getProperty("sweep");
        String[] scripts = only == null || only.equals("idle") ? IDLE : only.split(",");
        String a = System.getProperty("tweakA"), b = System.getProperty("tweakB");
        if (!"0".equals(System.getProperty("idleRef"))) report("FREE_SWIM reference", null, scripts, t -> {});
        report("DRIFT bell A " + (a == null ? "defaults" : a), DriftStyle.BELL, scripts, SeedSweepProbe.tweak(a));
        if (b != null) report("DRIFT bell B " + b, DriftStyle.BELL, scripts, SeedSweepProbe.tweak(b));
        // -PtweakList=spec1/spec2/...: one report per variant, each on top of tweakA.
        String list = System.getProperty("tweakList");
        if (list != null) {
            for (String v : list.split("/")) {
                String spec = a == null ? v : a + "," + v;
                report("DRIFT bell " + spec, DriftStyle.BELL, scripts, SeedSweepProbe.tweak(spec));
            }
        }
    }

    /** One run's idle numbers. */
    record Idle(double idleMin, double movesPerMin, double meanSpeed, double p90Speed, double pathPerMin,
                double strokesPerMin, double dvPerStroke, double settleMin, double settleMovesPerMin,
                double settleMeanSpeed, double seenPct, double baseShare, double baseSpeed, double baseP90,
                double baseStrokesPerMin, double baseTiltSwing, int inOwner) {}

    static Idle measure(CompanionRun run, int ticks) {
        double dt = CompanionAgent.DT;
        int stillFor = 0, idleTicks = 0, settleTicks = 0, moves = 0, settleMoves = 0, strokes = 0, base = 0, seen = 0;
        double path = 0, dvSum = 0, dvRun = 0, settleSpeed = 0;
        boolean inStroke = false;
        List<Double> speeds = new ArrayList<>(), baseSpeeds = new ArrayList<>();
        int baseStrokes = 0, inOwner = 0;
        double tiltTravel = 0;
        Vec lastTarget = null, lastPos = run.agent.position();
        for (int i = 0; i < ticks; i++) {
            run.tick();
            CompanionAgent ag = run.agent;
            stillFor = run.owner.horizontalSpeed() < 0.2 ? stillFor + 1 : 0;
            Vec pos = ag.position();
            Vec target = ag.target();
            String goal = ag.goal();
            boolean baseGoal = goal.equals("LOITER") || goal.equals("SETTLE");
            boolean idle = stillFor >= 20 && ag.appear() >= 1f;
            if (idle) {
                idleTicks++;
                double sp = ag.velocity().length();
                speeds.add(sp);
                path += pos.distanceTo(lastPos);
                if (CompanionRun.insideBody(run.owner, pos)) inOwner++;
                if (baseGoal) {
                    base++;
                    baseSpeeds.add(sp);
                    if (ag.driftStrokeStart()) baseStrokes++;
                    tiltTravel += ag.driftTiltRate();
                }
                if (run.samples.isEmpty() ? false : run.samples.get(run.samples.size() - 1).seen()) seen++;
                boolean moved = baseGoal && target != null && (lastTarget == null || target.distanceTo(lastTarget) > 0.3);
                if (moved) moves++;
                if (goal.equals("SETTLE")) {
                    settleTicks++;
                    settleSpeed += sp;
                    if (moved) settleMoves++;
                }
                if (ag.driftStrokeStart()) {
                    if (inStroke) { dvSum += dvRun; }
                    strokes++;
                    inStroke = true;
                    dvRun = 0;
                }
                if (inStroke) dvRun += ag.thrust() * dt;
            } else {
                inStroke = false;
            }
            lastTarget = target;
            lastPos = pos;
        }
        double min = idleTicks * dt / 60.0, smin = settleTicks * dt / 60.0;
        double[] s = speeds.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double p90 = s.length == 0 ? 0 : s[Math.min(s.length - 1, (int) (s.length * 0.9))];
        double mean = Arrays.stream(s).average().orElse(0);
        return new Idle(min, min > 0 ? moves / min : 0, mean, p90, min > 0 ? path / min : 0,
                min > 0 ? strokes / min : 0, strokes > 1 ? dvSum / (strokes - 1) : 0,
                smin, smin > 0 ? settleMoves / smin : 0, settleTicks > 0 ? settleSpeed / settleTicks : 0,
                idleTicks > 0 ? 100.0 * seen / idleTicks : 0, idleTicks > 0 ? 100.0 * base / idleTicks : 0,
                baseSpeeds.stream().mapToDouble(Double::doubleValue).average().orElse(0), p90(baseSpeeds),
                base > 0 ? baseStrokes / (base * dt / 60.0) : 0, base > 0 ? tiltTravel / (base * dt / 60.0) : 0, inOwner);
    }

    private static double p90(List<Double> v) {
        double[] s = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return s.length == 0 ? 0 : s[Math.min(s.length - 1, (int) (s.length * 0.9))];
    }

    static void report(String label, DriftStyle style, String[] scripts, Consumer<CompanionTunables> tweak) {
        boolean perSeed = System.getProperty("sweepPerSeed") != null;
        int seeds = DriftSweepProbe.seeds(), ticks = SeedSweepProbe.ticks();
        System.out.println("== " + label + " [" + SeedSweepProbe.temperament().name() + "] (" + seeds + " seeds x " + ticks
                + " ticks; mean over seeds, worst in brackets)");
        System.out.println("  script            idleMin  moves/min     speed       p90        path/min   strokes/min  dv/stroke  "
                + "settleMin  sMoves/min  sSpeed   seen%   base%  | base: speed  p90  strokes/min  tilt°/min");
        double[] tot = new double[16];
        int n = 0;
        for (String script : scripts) {
            double[] sum = new double[16];
            double worstMoves = 0, worstP90 = 0;
            for (int seed = 1; seed <= seeds; seed++) {
                CompanionTunables t = CompanionTunables.defaults();
                tweak.accept(t);
                CompanionRun run = style == null
                        ? new CompanionRun(script, seed, Locomotion.FREE_SWIM, SeedSweepProbe.temperament(), t, true)
                        : CompanionRun.drifter(script, seed, style, SeedSweepProbe.temperament(), t, true);
                Idle m = measure(run, ticks);
                double[] v = {m.idleMin(), m.movesPerMin(), m.meanSpeed(), m.p90Speed(), m.pathPerMin(), m.strokesPerMin(),
                        m.dvPerStroke(), m.settleMin(), m.settleMovesPerMin(), m.settleMeanSpeed(), m.seenPct(), m.baseShare(),
                        m.baseSpeed(), m.baseP90(), m.baseStrokesPerMin(), m.baseTiltSwing()};
                for (int k = 0; k < 16; k++) sum[k] += v[k];
                worstMoves = Math.max(worstMoves, m.movesPerMin());
                worstP90 = Math.max(worstP90, m.p90Speed());
                if (perSeed) System.out.printf("    %-15s #%d %s%n", script, seed, fmt(v));
            }
            for (int k = 0; k < 16; k++) sum[k] /= seeds;
            for (int k = 0; k < 16; k++) tot[k] += sum[k];
            n++;
            System.out.printf("  %-16s %6.2f  %5.1f[%4.1f]  %5.2f  %5.2f[%4.2f]  %6.1f   %6.1f      %5.2f      %5.2f   %5.1f       %5.2f   %5.1f   %5.1f  |  %5.2f  %5.2f  %5.1f  %6.0f%n",
                    script, sum[0], sum[1], worstMoves, sum[2], sum[3], worstP90, sum[4], sum[5], sum[6], sum[7], sum[8], sum[9],
                    sum[10], sum[11], sum[12], sum[13], sum[14], sum[15]);
        }
        for (int k = 0; k < 16; k++) tot[k] /= n;
        System.out.printf("  %-16s %6.2f  %5.1f        %5.2f  %5.2f        %6.1f   %6.1f      %5.2f      %5.2f   %5.1f       %5.2f   %5.1f   %5.1f  |  %5.2f  %5.2f  %5.1f  %6.0f%n",
                "MEAN", tot[0], tot[1], tot[2], tot[3], tot[4], tot[5], tot[6], tot[7], tot[8], tot[9], tot[10], tot[11],
                tot[12], tot[13], tot[14], tot[15]);
    }

    private static String fmt(double[] v) {
        StringBuilder b = new StringBuilder();
        for (double x : v) b.append(String.format("%7.2f", x));
        return b.toString();
    }
}

package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Diagnostic: how calm (or frenetic) a drifter reads, split by a still owner (still a second or
 * more) and a moving one. Bell (flapjack) and bob (man o' war) on the same seeds, plus one bob
 * report per {@code -PtweakList} variant. Run with -PsimStdout.
 *
 * <pre>
 *   -Psweep=idle_fixed,...      scripts (default: idle_fixed,idle_looking,camera_sweep,walk,walk_turns)
 *   -PtweakList=a=1,b=2/c=3     bob variants
 *   -PsweepTemperament=aggressive -PsweepSeeds=8 -PsweepTicks=2400
 * </pre>
 *
 * Columns: strokes/min, dv per stroke (b/s), speed mean and p90 (b/s), speed swing within a stroke
 * cycle (b/s), horizontal acceleration rms (b/s²), jerk rms (b/s³), height range within a cycle
 * (blocks), facing turn rate mean and p90 (°/s), facing reversals/min, bank swing within a cycle (°),
 * idle spot moves/min, velocity reversals/min (heading now vs a second ago more than 90° apart).
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BobCalmProbe {

    static final String[] SCRIPTS = {"idle_fixed", "idle_looking", "camera_sweep", "walk", "walk_turns"};
    static final String[] COLS = {"strk/m", "dv/strk", "spd", "spdP90", "swing", "accRms", "jerkRms", "yRange",
            "turn°/s", "turnP90", "rev/m", "bank°", "moves/m", "vRev/m", "cap%", "sweep°", "face°/s", "faceP90", "big/m"};

    @Test
    void calm() {
        String only = System.getProperty("sweep");
        String[] scripts = only == null ? SCRIPTS : only.split(",");
        report("bell (flapjack) defaults", DriftStyle.BELL, scripts, t -> {});
        report("bob (man o' war) defaults", DriftStyle.BOB, scripts, t -> {});
        String list = System.getProperty("tweakList");
        if (list != null) {
            for (String v : list.split("/")) report("bob " + v, DriftStyle.BOB, scripts, SeedSweepProbe.tweak(v));
        }
    }

    /** Accumulates one condition's (still or moving) numbers over a run. */
    static final class Acc {
        int ticks, strokes, moves, reversals, vRevs, cycles, capTicks, episodes;
        double epRun, epSum, faceSum, faceRun;
        int bigFace, faceSign;
        final List<Double> faceTurns = new ArrayList<>();
        double dvSum, speedSum, accSq, jerkSq, swingSum, yRangeSum, bankSum, turnSum;
        final List<Double> speeds = new ArrayList<>(), turns = new ArrayList<>();
        // the open cycle
        double cMinS = 1e9, cMaxS = -1e9, cMinY = 1e9, cMaxY = -1e9, cMinB = 1e9, cMaxB = -1e9, dvRun;
        boolean open;

        void closeCycle() {
            if (open) {
                cycles++;
                swingSum += cMaxS - cMinS;
                yRangeSum += cMaxY - cMinY;
                bankSum += cMaxB - cMinB;
                dvSum += dvRun;
            }
            cMinS = cMinY = cMinB = 1e9;
            cMaxS = cMaxY = cMaxB = -1e9;
            dvRun = 0;
        }

        double[] row() {
            double min = ticks * CompanionAgent.DT / 60.0;
            if (ticks == 0) return new double[COLS.length];
            return new double[]{strokes / min, cycles > 0 ? dvSum / cycles : 0, speedSum / ticks, p90(speeds),
                    cycles > 0 ? swingSum / cycles : 0, Math.sqrt(accSq / ticks), Math.sqrt(jerkSq / ticks),
                    cycles > 0 ? yRangeSum / cycles : 0, turnSum / ticks, p90(turns), reversals / min,
                    cycles > 0 ? bankSum / cycles : 0, moves / min, vRevs / min,
                    100.0 * capTicks / ticks, episodes > 0 ? epSum / episodes : 0,
                    faceSum / ticks, p90(faceTurns), bigFace / min};
        }
    }

    static double[][] measure(CompanionRun run, int ticks) {
        double dt = CompanionAgent.DT;
        Acc still = new Acc(), moving = new Acc();
        int stillFor = 0, lastSign = 0;
        float lastFace = run.agent.face();
        Vec lastAcc = null, lastTarget = null;
        Vec[] hv = new Vec[20];
        for (int i = 0; i < ticks; i++) {
            run.tick();
            CompanionAgent ag = run.agent;
            stillFor = run.owner.horizontalSpeed() < 0.2 ? stillFor + 1 : 0;
            Vec acc = ag.acceleration();
            Vec h = ag.velocity().horizontal();
            Vec ago = hv[i % 20];
            hv[i % 20] = h;
            double turn = ag.lastTurnDeg();
            float faceStep = Body.wrap(ag.face() - lastFace);
            lastFace = ag.face();
            if (ag.appear() < 1f) { lastAcc = acc; lastTarget = ag.target(); continue; }
            Acc a = stillFor >= 20 ? still : run.owner.horizontalSpeed() >= 0.2 ? moving : null;
            if (a != null) {
                a.ticks++;
                double sp = h.length();
                a.speedSum += sp;
                a.speeds.add(sp);
                Vec ha = acc.horizontal();
                a.accSq += ha.dot(ha);
                if (lastAcc != null) {
                    Vec j = acc.sub(lastAcc).scale(1 / dt);
                    a.jerkSq += j.dot(j);
                }
                a.faceSum += Math.abs(faceStep) / dt;
                a.faceTurns.add(Math.abs(faceStep) / dt);
                // drawn turns of 90° or more: one run of same-signed facing turn
                int fs = Math.abs(faceStep) > 0.2f ? (int) Math.signum(faceStep) : 0;
                if (fs != 0 && fs == a.faceSign) a.faceRun += Math.abs(faceStep);
                else { if (a.faceRun >= 90) a.bigFace++; a.faceRun = Math.abs(faceStep); a.faceSign = fs; }
                double tps = Math.abs(turn) / dt;
                a.turnSum += tps;
                a.turns.add(tps);
                if (Math.abs(turn) >= 0.9 * run.tunables.bobSailTurnDegPerTick) a.capTicks++;
                int sign = Math.abs(turn) > 0.5 ? (int) Math.signum(turn) : 0;
                // a turning episode: one run of same-signed turning above 0.5°/tick; its total sweep
                if (sign != 0 && (sign == lastSign || lastSign == 0)) a.epRun += Math.abs(turn);
                else if (a.epRun > 0) { if (a.epRun > 10) { a.episodes++; a.epSum += a.epRun; } a.epRun = sign != 0 ? Math.abs(turn) : 0; }
                if (sign != 0) {
                    if (lastSign != 0 && sign != lastSign) a.reversals++;
                    lastSign = sign;
                }
                if (ago != null && sp > 0.3 && ago.length() > 0.3 && h.dot(ago) < 0) a.vRevs++;
                Vec tg = ag.target();
                if (tg != null && lastTarget != null && tg.distanceTo(lastTarget) > 0.3 && stillFor >= 20) a.moves++;
                if (ag.driftStrokeStart()) {
                    a.strokes++;
                    a.closeCycle();
                    a.open = true;
                }
                a.dvRun += ag.thrust() * dt;
                a.cMinS = Math.min(a.cMinS, sp);
                a.cMaxS = Math.max(a.cMaxS, sp);
                double y = ag.position().y();
                a.cMinY = Math.min(a.cMinY, y);
                a.cMaxY = Math.max(a.cMaxY, y);
                a.cMinB = Math.min(a.cMinB, ag.bank());
                a.cMaxB = Math.max(a.cMaxB, ag.bank());
            } else {
                still.open = false;
                moving.open = false;
            }
            lastAcc = acc;
            lastTarget = ag.target();
        }
        return new double[][]{still.row(), moving.row()};
    }

    static double p90(List<Double> v) {
        double[] s = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return s.length == 0 ? 0 : s[Math.min(s.length - 1, (int) (s.length * 0.9))];
    }

    static void report(String label, DriftStyle style, String[] scripts, Consumer<CompanionTunables> tweak) {
        int seeds = DriftSweepProbe.seeds(), ticks = SeedSweepProbe.ticks();
        System.out.println("== " + label + " [" + SeedSweepProbe.temperament().name() + "] (" + seeds + " seeds x "
                + ticks + " ticks, mean over seeds)");
        StringBuilder hdr = new StringBuilder(String.format("  %-22s", "script / owner"));
        for (String c : COLS) hdr.append(String.format("%9s", c));
        System.out.println(hdr);
        for (String script : scripts) {
            double[][] sum = new double[2][COLS.length];
            int[] n = new int[2];
            for (int seed = 1; seed <= seeds; seed++) {
                CompanionTunables t = CompanionTunables.defaults();
                tweak.accept(t);
                CompanionRun run = CompanionRun.drifter(script, seed, style, SeedSweepProbe.temperament(), t, true);
                double[][] m = measure(run, ticks);
                for (int c = 0; c < 2; c++) {
                    if (m[c][2] == 0 && m[c][0] == 0) continue;
                    n[c]++;
                    for (int k = 0; k < COLS.length; k++) sum[c][k] += m[c][k];
                }
            }
            for (int c = 0; c < 2; c++) {
                if (n[c] == 0) continue;
                StringBuilder b = new StringBuilder(String.format("  %-22s", script + (c == 0 ? " / still" : " / moving")));
                for (int k = 0; k < COLS.length; k++) b.append(String.format("%9.2f", sum[c][k] / n[c]));
                System.out.println(b);
            }
        }
    }

    /** Why the idle sail swings: each big idle turn's onset, against what it was asked and how it moved. */
    @Test
    void idleTurnCauses() {
        int seeds = DriftSweepProbe.seeds(), ticks = SeedSweepProbe.ticks();
        int eps = 0, afterMove = 0, askedTiny = 0, overshoot = 0;
        double askSum = 0, spdSum = 0, sweepSum = 0;
        for (int seed = 1; seed <= seeds; seed++) {
            CompanionRun run = CompanionRun.drifter("idle_fixed", seed, DriftStyle.BOB, SeedSweepProbe.temperament(),
                    CompanionTunables.defaults(), true);
            int stillFor = 0, sinceMove = 999, sign = 0;
            double sweep = 0, onsetAsk = 0, onsetSpd = 0, onsetDot = 0;
            int onsetSinceMove = 0;
            Vec lastTarget = null;
            for (int i = 0; i < ticks; i++) {
                run.tick();
                CompanionAgent ag = run.agent;
                stillFor = run.owner.horizontalSpeed() < 0.2 ? stillFor + 1 : 0;
                Vec tg = ag.target();
                sinceMove = tg != null && lastTarget != null && tg.distanceTo(lastTarget) > 0.3 ? 0 : sinceMove + 1;
                lastTarget = tg;
                if (stillFor < 20 || ag.appear() < 1f) { sign = 0; sweep = 0; continue; }
                float turn = ag.lastTurnDeg();
                int sg = Math.abs(turn) > 0.5 ? (int) Math.signum(turn) : 0;
                if (sg != 0 && sg == sign) { sweep += Math.abs(turn); continue; }
                if (sweep >= 90) {
                    eps++;
                    sweepSum += sweep;
                    askSum += onsetAsk;
                    spdSum += onsetSpd;
                    if (onsetSinceMove <= 20) afterMove++;
                    if (onsetAsk < 0.3) askedTiny++;
                    if (onsetDot < 0) overshoot++;
                }
                sign = sg;
                sweep = Math.abs(turn);
                Vec ask = ag.driftAsked().horizontal(), v = ag.velocity().horizontal();
                onsetAsk = ask.length();
                onsetSpd = v.length();
                onsetDot = ask.length() > 1e-6 && v.length() > 1e-6 ? ask.normalize().dot(v.normalize()) : 1;
                onsetSinceMove = sinceMove;
            }
        }
        System.out.printf("idle big turns (>=90 deg): %d over %d seeds; mean sweep %.0f deg; asked at onset %.2f b/s, speed %.2f b/s;%n"
                        + "  %.0f%% within 1 s of a new idle spot, %.0f%% asked under 0.3 b/s, %.0f%% asked against its velocity (overshoot)%n",
                eps, seeds, sweepSum / eps, askSum / eps, spdSum / eps, 100.0 * afterMove / eps, 100.0 * askedTiny / eps,
                100.0 * overshoot / eps);
    }

    static DriftBody motor(CompanionAgent ag) {
        try {
            var f = CompanionAgent.class.getDeclaredField("body");
            f.setAccessible(true);
            return (DriftBody) f.get(ag);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Each contact in -Psweep scripts (default low_ceiling): calm, speed, sail, goal and the owner at that tick. */
    @Test
    void contacts() {
        String only = System.getProperty("sweep");
        String[] scripts = only == null ? new String[]{"low_ceiling"} : only.split(",");
        int seeds = DriftSweepProbe.seeds(), ticks = SeedSweepProbe.ticks();
        for (String script : scripts) {
            for (int seed = 1; seed <= seeds; seed++) {
                CompanionTunables t = CompanionTunables.defaults();
                SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
                CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.BOB, SeedSweepProbe.temperament(), t, true);
                DriftBody b = motor(run.agent);
                float maxCalm = 0;
                int lastCalm = -100000;
                for (int i = 0; i < ticks; i++) {
                    run.tick();
                    if (b.calm > 0.1f) lastCalm = i;
                    maxCalm = i % 40 == 0 ? b.calm : Math.max(maxCalm, b.calm);
                    if (run.agent.lastImpact() > 0.05) {
                        System.out.printf("  %s #%d t=%d impact=%.2f calm=%.2f (2s max %.2f) speed=%.2f hspeed=%.2f effort=%.2f pulse=%.2f "
                                        + "sail=%.0f velYaw=%.0f goal=%s owner=%.2f sinceCalm=%.1fs%n", script, seed, i, run.agent.lastImpact(), b.calm,
                                maxCalm, b.vel.length(), b.vel.horizontalLength(), b.effort, b.pulse, b.tiltYaw,
                                b.vel.horizontalLength() > 0.05 ? b.vel.yawDeg() : Double.NaN, run.agent.goal(),
                                run.owner.horizontalSpeed(), (i - lastCalm) * CompanionAgent.DT);
                    }
                }
            }
        }
    }
}

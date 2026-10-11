package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.function.Consumer;

/**
 * Diagnostic: multi-seed totals per script, for equal-grounds A/B of a change. Run with -PsimStdout.
 *
 * <pre>
 *   -Psweep=walk,forest            just these scripts (default: the spike's obstacle set)
 *   -PsweepClass=GLIDE             the locomotion class (default FREE_SWIM)
 *   -PtweakA=field=value,...       tunables for side A (default: defaults)
 *   -PtweakB=field=value,...       and side B; omit for a single report
 *   -PsweepLength=0.4              body length override, blocks
 * </pre>
 * With no properties it reproduces the spike's whiskers on/off ablation.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class SeedSweepProbe {
    static final String[] SCRIPTS = {"corridor", "doorway", "forest", "walk_turns", "teleport", "idle_looking", "walk"};
    static final int SEEDS = 8, TICKS = 2400;

    @Test
    void sweep() {
        String only = System.getProperty("sweep");
        if ("TRIO".equals(System.getProperty("sweepClass"))) {
            trio(only != null ? only.split(",") : SCRIPTS);
            return;
        }
        Locomotion c = Locomotion.valueOf(System.getProperty("sweepClass", "FREE_SWIM"));
        String a = System.getProperty("tweakA"), b = System.getProperty("tweakB");
        if (only == null && a == null && b == null && c == Locomotion.FREE_SWIM) {
            report("whiskers on ", c, SCRIPTS, t -> t.whiskers = true);
            report("whiskers off", c, SCRIPTS, t -> t.whiskers = false);
            return;
        }
        String[] scripts = only != null ? only.split(",") : SCRIPTS;
        report(c + " A " + (a == null ? "defaults" : a), c, scripts, tweak(a));
        if (b != null) report(c + " B " + b, c, scripts, tweak(b));
    }

    /** Trio totals: the leader's essentials, and how the followers kept together. */
    static void trio(String[] scripts) {
        System.out.println("== TRIO");
        for (String script : scripts) {
            long fBackstops = 0, fLeash = 0;
            double fImpact = 0, minSep = 9, overlap = 0, bodyOverlap = 0, straggle = 0, far = 0;
            int pen = 0;
            for (int seed = 1; seed <= SEEDS; seed++) {
                CompanionRun run = CompanionRun.trio(script, seed, CompanionTunables.defaults(), true);
                run.run(TICKS);
                var m = run.metrics();
                var f = run.followerMetrics();
                far = Math.max(far, m.longestFarSeconds());
                pen += m.solidPenetrations() + f.penetrations();
                fBackstops += f.backstops();
                fLeash += f.leashEvents();
                fImpact = Math.max(fImpact, f.maxImpact());
                if (f.minSeparation() >= 0) minSep = Math.min(minSep, f.minSeparation());
                overlap = Math.max(overlap, f.overlapFraction());
                bodyOverlap = Math.max(bodyOverlap, f.bodyOverlapFraction());
                straggle = Math.max(straggle, f.longestStraggleSeconds());
            }
            System.out.printf("  %-15s pen=%d leaderFar=%4.1fs | followers backstops=%4d impact=%.2f leash=%3d minSep=%.3f maxOverlap=%.2f%% maxBodyOverlap=%.2f%% maxStraggle=%4.1fs%n",
                    script, pen, far, fBackstops, fImpact, fLeash, minSep, overlap * 100, bodyOverlap * 100, straggle);
        }
    }

    /** Sets {@code field=value,...} on the tunables by reflection. */
    static Consumer<CompanionTunables> tweak(String spec) {
        return t -> {
            if (spec == null || spec.isBlank()) return;
            for (String kv : spec.split(",")) {
                String[] p = kv.split("=");
                try {
                    var f = CompanionTunables.class.getField(p[0].trim());
                    if (f.getType() == boolean.class) f.setBoolean(t, Boolean.parseBoolean(p[1].trim()));
                    else f.setFloat(t, Float.parseFloat(p[1].trim()));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalArgumentException("bad tweak " + kv, e);
                }
            }
        };
    }

    static void report(String label, Locomotion c, String[] scripts, Consumer<CompanionTunables> tweak) {
        System.out.println("== " + label);
        for (String script : scripts) {
            long backstops = 0, leash = 0; double impact = 0; int inOwner = 0, nearClip = 0;
            double unseen = 0, far = 0, catchShare = 0, face = 0, seen = 0;
            for (int seed = 1; seed <= SEEDS; seed++) {
                CompanionTunables t = CompanionTunables.defaults();
                tweak.accept(t);
                String len = System.getProperty("sweepLength");
                CompanionRun run = new CompanionRun(script, seed, c,
                        len != null ? Float.parseFloat(len) : CompanionRun.lengthOf(c), t, true);
                run.run(TICKS);
                var m = run.metrics();
                backstops += m.backstops();
                leash += m.leashEvents();
                impact = Math.max(impact, m.maxImpact());
                inOwner += m.insideOwnerTicks();
                nearClip += m.nearClipTicks();
                unseen = Math.max(unseen, m.longestUnseenSeconds());
                far = Math.max(far, m.longestFarSeconds());
                seen += m.seenFraction() / SEEDS;
                catchShare += m.goalShare().getOrDefault("CATCH_UP", 0.0) / SEEDS;
                face = Math.max(face, m.maxFacingErrorDeg());
            }
            System.out.printf("  %-15s backstops=%4d maxImpact=%.2f leash=%3d maxFar=%5.1fs inOwner=%4d nearClip=%4d seen=%3.0f%% maxUnseen=%.1fs catch=%.0f%% maxFace=%.0f%n",
                    script, backstops, impact, leash, far, inOwner, nearClip, seen * 100, unseen, catchShare * 100, face);
        }
    }
}

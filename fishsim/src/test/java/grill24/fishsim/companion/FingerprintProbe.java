package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

/**
 * Diagnostic: one hash per script × seed over every tick's position and heading, for proving a
 * refactor left a body's trajectories bitwise unchanged. Diff the output before and after.
 * Run with -PsimStdout. By default it hashes the spike's FREE_SWIM baseline (11 scripts × 3
 * seeds); {@code -PfpAll=1} adds every class in the table and the trio over all scripts.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class FingerprintProbe {

    static final List<String> SCRIPTS = List.of(
            "idle_fixed", "idle_looking", "walk", "walk_turns", "sprint_stop", "camera_sweep",
            "corridor", "doorway", "forest", "teleport", "elytra");

    @Test
    void fingerprints() {
        for (String script : SCRIPTS) {
            for (long seed = 1; seed <= 3; seed++) {
                CompanionRun run = new CompanionRun(script, seed, tunables(), true);
                run.run(2400);
                System.out.printf("FP %-13s #%d %016x%n", script, seed, hash(run));
            }
        }
        if (System.getProperty("fpAll") == null) return;
        for (Locomotion c : List.of(Locomotion.FREE_SWIM, Locomotion.GLIDE, Locomotion.BENTHIC, Locomotion.ANCHORED, Locomotion.DRIFT)) {
            for (String script : OwnerScript.NAMES) {
                for (long seed = 1; seed <= 3; seed++) {
                    CompanionRun run = new CompanionRun(script, seed, c, tunables(), true);
                    run.run(2400);
                    System.out.printf("FPA %-9s %-15s #%d %016x%n", c, script, seed, hash(run));
                }
            }
        }
        // A benthic with no ground gait: the game's path until Phase 5 wires the gait in
        // (docs/fish-companions.md). Must hash as the Phase 3 BENTHIC did.
        for (String script : OwnerScript.NAMES) {
            for (long seed = 1; seed <= 3; seed++) {
                CompanionRun run = CompanionRun.benthic(script, seed, CompanionRun.lengthOf(Locomotion.BENTHIC),
                        Temperament.BASELINE, null, tunables(), true);
                run.run(2400);
                System.out.printf("FPA %-9s %-15s #%d %016x%n", "PERCHER", script, seed, hash(run));
            }
        }
        for (String script : OwnerScript.NAMES) {
            for (long seed = 1; seed <= 3; seed++) {
                CompanionRun run = CompanionRun.trio(script, seed, tunables(), true);
                run.run(2400);
                long h = hash(run);
                for (CompanionRun.FollowerSample[] fs : run.followerSamples) {
                    for (CompanionRun.FollowerSample f : fs) {
                        h = 31 * h + Double.doubleToLongBits(f.pos().x());
                        h = 31 * h + Double.doubleToLongBits(f.pos().y());
                        h = 31 * h + Double.doubleToLongBits(f.pos().z());
                    }
                }
                System.out.printf("FPA %-9s %-15s #%d %016x%n", "TRIO", script, seed, h);
            }
        }
        // Drifters by style (docs/fish-companions-drift.md); the plain DRIFT lines above are the bell.
        for (DriftStyle style : List.of(DriftStyle.JET, DriftStyle.BOB)) {
            for (String script : OwnerScript.NAMES) {
                for (long seed = 1; seed <= 3; seed++) {
                    CompanionRun run = CompanionRun.drifter(script, seed, style, Temperament.BASELINE,
                            tunables(), true);
                    run.run(2400);
                    System.out.printf("FPA %-9s %-15s #%d %016x%n", "DRIFT_" + style, script, seed, hash(run));
                }
            }
        }
    }

    /** Optional ablation settings, applied to each fresh run before its agent is constructed. */
    private static CompanionTunables tunables() {
        CompanionTunables t = CompanionTunables.defaults();
        SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
        return t;
    }

    private static long hash(CompanionRun run) {
        long h = 1125899906842597L;
        for (CompanionRun.Sample s : run.samples) {
            h = 31 * h + Double.doubleToLongBits(s.pos().x());
            h = 31 * h + Double.doubleToLongBits(s.pos().y());
            h = 31 * h + Double.doubleToLongBits(s.pos().z());
            h = 31 * h + Float.floatToIntBits(s.yaw());
            h = 31 * h + Float.floatToIntBits(s.pitch());
        }
        return h;
    }
}

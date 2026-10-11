package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.function.Consumer;

/** Diagnostic: multi-seed totals per script, for equal-grounds A/B of a change. Run with -PsimStdout. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class SeedSweepProbe {
    static final String[] SCRIPTS = {"corridor", "doorway", "forest", "walk_turns", "teleport", "idle_looking", "walk"};
    static final int SEEDS = 8, TICKS = 2400;

    @Test
    void sweep() {
        report("whiskers on ", t -> t.whiskers = true);
        report("whiskers off", t -> t.whiskers = false);
    }

    static void report(String label, Consumer<CompanionTunables> tweak) {
        System.out.println("== " + label);
        for (String script : SCRIPTS) {
            long backstops = 0; double impact = 0; int inOwner = 0, nearClip = 0; double unseen = 0, catchShare = 0, face = 0;
            for (int seed = 1; seed <= SEEDS; seed++) {
                CompanionTunables t = CompanionTunables.defaults();
                tweak.accept(t);
                CompanionRun run = new CompanionRun(script, seed, t, true);
                run.run(TICKS);
                var m = run.metrics();
                backstops += m.backstops();
                impact = Math.max(impact, m.maxImpact());
                inOwner += m.insideOwnerTicks();
                nearClip += m.nearClipTicks();
                unseen = Math.max(unseen, m.longestUnseenSeconds());
                catchShare += m.goalShare().getOrDefault("CATCH_UP", 0.0) / SEEDS;
                face = Math.max(face, m.maxFacingErrorDeg());
            }
            System.out.printf("  %-13s backstops=%4d maxImpact=%.2f inOwner=%4d nearClip=%4d maxUnseen=%.1fs catch=%.0f%% maxFace=%.0f%n",
                    script, backstops, impact, inOwner, nearClip, unseen, catchShare * 100, face);
        }
    }
}

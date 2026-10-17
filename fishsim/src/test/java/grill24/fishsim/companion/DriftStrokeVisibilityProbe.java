package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Diagnostic: does every visible push come with a visible squeeze? Per tick, the speed gained
 * (|v| up) and the animator's squeeze ({@code pulse}). Reports speed gained while the squeeze is
 * small (< 0.2, under a fifth of the drawn stretch) against the total, and a per-stroke table:
 * squeeze length, speed before/at peak, and where the speed peaks relative to the squeeze.
 * Run with -PsimStdout; -Ptrace=idle_fixed -PsweepSeeds=4 -PsweepTicks=2400.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class DriftStrokeVisibilityProbe {

    @Test
    void strokes() {
        String script = System.getProperty("trace", "idle_fixed");
        int seeds = Integer.parseInt(System.getProperty("sweepSeeds", "4"));
        int ticks = Integer.parseInt(System.getProperty("sweepTicks", "2400"));
        boolean list = Boolean.parseBoolean(System.getProperty("sweepPerSeed", "false"));
        double gainAll = 0, gainHidden = 0, gainHiddenIdle = 0, gainIdle = 0;
        int strokes = 0, peakAfter = 0;
        double squeezeTicksSum = 0, peakLagSum = 0;
        // Drawn stretch at the squeeze's top, old (pulse × 0.10) and new (squeeze × 0.10 × 3), for strokes by push size.
        int bigPushes = 0, bigOldShown = 0, bigNewShown = 0, smallPushes = 0, smallNewQuiet = 0;
        double bigNewSum = 0, smallNewSum = 0;
        float strokeSqueezeMax = 0;
        System.out.println("== DRIFT stroke visibility " + script);
        for (long seed = 1; seed <= seeds; seed++) {
            CompanionTunables t = CompanionTunables.defaults();
            SeedSweepProbe.tweak(System.getProperty("tweakA")).accept(t);
            CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.BELL, SeedSweepProbe.temperament(), t, false);
            DriftBody b = (DriftBody) bodyOf(run.agent);
            double lastSpeed = 0;
            int strokeTick = -1, squeezeEnd = -1, peakTick = -1;
            double startSpeed = 0, peakSpeed = 0, strokePeak = 0;
            for (int k = 0; k < ticks; k++) {
                run.tick();
                double sp = b.vel.length();
                double gain = Math.max(0, sp - lastSpeed);
                boolean idle = b.idling;
                if (run.agent.appear() >= 1f && k > 40) {
                    gainAll += gain;
                    if (idle) gainIdle += gain;
                    if (b.pulse < 0.2f) {
                        gainHidden += gain;
                        if (idle) gainHiddenIdle += gain;
                    }
                }
                if (b.strokeStart) {
                    if (strokeTick >= 0 && k > 40) {
                        strokes++;
                        squeezeTicksSum += squeezeEnd - strokeTick;
                        peakLagSum += peakTick - squeezeEnd;
                        if (peakTick > squeezeEnd) peakAfter++;
                        double push = peakSpeed - startSpeed;
                        if (idle) {
                            float oldStretch = 0.10f, newStretch = 0.30f * strokeSqueezeMax;
                            if (push >= 0.25) { bigPushes++; if (oldStretch >= 0.15f) bigOldShown++; if (newStretch >= 0.15f) bigNewShown++; bigNewSum += newStretch; }
                            else if (push < 0.05) { smallPushes++; if (newStretch < 0.10f) smallNewQuiet++; smallNewSum += newStretch; }
                        }
                        if (list) System.out.printf("  seed %d t%5d %s squeeze %3d ticks  peakThrust %5.1f  v %.2f -> %.2f at %+d ticks from squeeze end%n",
                                seed, strokeTick, idle ? "IDLE" : "move", squeezeEnd - strokeTick, strokePeak, startSpeed, peakSpeed, peakTick - squeezeEnd);
                    }
                    strokeTick = k; squeezeEnd = k; startSpeed = sp; peakSpeed = sp; peakTick = k; strokePeak = 0; strokeSqueezeMax = 0;
                }
                if (b.pulse > 0.01f) squeezeEnd = k;
                if (sp > peakSpeed) { peakSpeed = sp; peakTick = k; }
                strokePeak = Math.max(strokePeak, b.lastThrust);
                strokeSqueezeMax = Math.max(strokeSqueezeMax, b.squeeze);
                lastSpeed = sp;
            }
        }
        System.out.printf("  speed gained: all %.1f, while squeeze<0.2 %.1f (%.0f%%); idle %.1f, idle while squeeze<0.2 %.1f (%.0f%%)%n",
                gainAll, gainHidden, 100 * gainHidden / gainAll, gainIdle, gainHiddenIdle, 100 * gainHiddenIdle / Math.max(1e-9, gainIdle));
        System.out.printf("  strokes %d, mean squeeze %.1f ticks, speed peaks after squeeze end in %d (mean lag %.1f ticks)%n",
                strokes, squeezeTicksSum / strokes, peakAfter, peakLagSum / strokes);
        System.out.printf("  pushes >= 0.25 b/s: %d; drawn stretch >= 15%%: old %d, new %d (new mean %.0f%%)%n",
                bigPushes, bigOldShown, bigNewShown, 100 * bigNewSum / Math.max(1, bigPushes));
        System.out.printf("  pushes < 0.05 b/s: %d; old always 10%%, new < 10%% in %d (new mean %.0f%%)%n",
                smallPushes, smallNewQuiet, 100 * smallNewSum / Math.max(1, smallPushes));
    }

    static Motor bodyOf(CompanionAgent a) {
        try {
            var f = CompanionAgent.class.getDeclaredField("body");
            f.setAccessible(true);
            return (Motor) f.get(a);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

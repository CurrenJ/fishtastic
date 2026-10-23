package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.PartyRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Locale;

/**
 * The predator scene (D21, G4; docs/fish-companions.md) on {@code predator + 2 prey}:
 * how often it comes at its real timing, and, with the timing cut short, how each scene plays: the
 * lap begun and finished, the prey hidden, the prey on the owner's far side from the predator while
 * it laps, body contact, and how much of the lap the owner sees.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class PredatorSceneProbe {

    private static final int SEEDS = Integer.parseInt(System.getProperty("sweepSeeds", "8"));

    private static PartyRun.Party party() {
        return PartyRun.PARTIES.stream().filter(p -> p.name().equals("predator + 2 prey")).findFirst().orElseThrow();
    }

    @Test
    void rate() {
        // The real timing on the five-minute idle scripts and the two-minute suite.
        int scenes = 0;
        double minutes = 0, idleMinutes = 0;
        int idleScenes = 0;
        for (String script : PartyRun.SCRIPTS) {
            int ticks = script.startsWith("idle") ? 6000 : 2400;
            for (long seed = 1; seed <= SEEDS; seed++) {
                Stats s = run(script, seed, ticks, CompanionTunables.defaults());
                scenes += s.started;
                minutes += ticks / 1200.0;
                if (script.startsWith("idle")) {
                    idleScenes += s.started;
                    idleMinutes += ticks / 1200.0;
                }
            }
        }
        System.out.printf(Locale.ROOT, "%nPREDATOR SCENE rate: %d scenes in %.0f min (%.2f/min); idle scripts %d in %.0f min (%.2f/min)%n",
                scenes, minutes, scenes / minutes, idleScenes, idleMinutes, idleScenes / idleMinutes);
    }

    @Test
    void quality() {
        CompanionTunables t = CompanionTunables.defaults();
        t.predatorSceneFirstMinSeconds = 5;
        t.predatorSceneFirstMaxSeconds = 10;
        t.predatorSceneGapMinSeconds = 20;
        t.predatorSceneGapMaxSeconds = 30;
        Stats all = new Stats();
        for (String script : PartyRun.SCRIPTS) {
            Stats sc = new Stats();
            for (long seed = 1; seed <= SEEDS; seed++) sc.add(run(script, seed, 2400, t));
            if (sc.started > 0) {
                System.out.printf(Locale.ROOT, "  %-20s %s%n", script, sc);
            }
            all.add(sc);
        }
        System.out.printf(Locale.ROOT, "PREDATOR SCENE quality (timing cut short): %s%n", all);
    }

    static Stats run(String script, long seed, int ticks, CompanionTunables t) {
        PartyRun.Party p = party();
        OwnerScript os = OwnerScript.named(script, seed);
        CompanionWorld base = os.nextWorld();
        PartyLayer layer = p.layer(t, Temperament.BASELINE, p.groups(seed), base);
        PartyLayer.Unit pred = null, prey = null;
        for (PartyLayer.Unit u : layer.units()) {
            if (u.group().leader().predator()) pred = u; else prey = u;
        }
        Stats s = new Stats();
        int lapsDone0 = pred.leader().lapsCompleted();
        for (int k = 0; k < ticks; k++) {
            base = os.nextWorld();
            layer.step(base);
            if (pred.leader().lapping()) {
                s.lapTicks++;
                OwnerState o = base.owner();
                Vec c = o.chest();
                Vec a = pred.leader().position().sub(c).horizontal(), b = prey.leader().position().sub(c).horizontal();
                if (a.length() > 1e-6 && b.length() > 1e-6 && a.dot(b) < 0) s.farSide++;
                if (Visibility.visible(o, pred.leader().position(), pred.leader().voxels())) s.predSeen++;
                if (prey.leader().hiding()) s.preyHidingDuringLap++;
                double contact = pred.leader().radius() + prey.leader().radius();
                if (pred.leader().position().distanceTo(prey.leader().position()) < contact) s.contact++;
                CompanionGroup school = prey.school();
                if (school != null) {
                    for (int f = 0; f < school.followerCount(); f++) {
                        if (pred.leader().position().distanceTo(school.follower(f).position())
                                < pred.leader().radius() + school.follower(f).radius()) s.contact++;
                    }
                }
            }
        }
        s.started = layer.scenesStarted();
        s.lapped = layer.scenesLapped();
        s.hid = layer.scenesPreyHid();
        s.completed = pred.leader().lapsCompleted() - lapsDone0;
        return s;
    }

    static final class Stats {
        int started, lapped, hid, completed;
        long lapTicks, farSide, predSeen, preyHidingDuringLap, contact;

        void add(Stats o) {
            started += o.started; lapped += o.lapped; hid += o.hid; completed += o.completed;
            lapTicks += o.lapTicks; farSide += o.farSide; predSeen += o.predSeen;
            preyHidingDuringLap += o.preyHidingDuringLap; contact += o.contact;
        }

        private double pct(long n) { return lapTicks == 0 ? 0 : 100.0 * n / lapTicks; }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "scenes %d, lapped %d, full turn %d, prey hid %d | per lap tick: prey far side %.0f%%, prey hiding %.0f%%, predator seen %.0f%%, contact ticks %d | mean lap %.1f s",
                    started, lapped, completed, hid, pct(farSide), pct(preyHidingDuringLap), pct(predSeen), contact,
                    lapped == 0 ? 0 : lapTicks / 20.0 / lapped);
        }
    }
}

package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Locale;

/**
 * How long a garden eel on its owner's head ducks (PoseHint.DUCK), solo: calm, while the owner holds
 * a predator fish, and with a hostile at several distances. Prints once a second.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class EelDuckProbe {
    private static final BlockQuery FLOOR = (x, y, z) -> y < 0;
    private static final OwnerState OWNER = new OwnerState(new Vec(0, 1.62, 0), Vec.ZERO,
            0, 0, false, false, true, 70, false);

    private static CompanionWorld world(PointOfInterest point, CompanionWorld.Held held) {
        return new CompanionWorld(OWNER, FLOOR, point == null ? List.of() : List.of(point), held, false);
    }

    @Test
    void probe() {
        for (float duckSeconds : new float[] {0f, 2f}) for (String temperament : List.of("metronome", "aggressive")) {
            CompanionTunables tun = CompanionTunables.defaults();
            tun.anchoredPredatorDuckSeconds = duckSeconds;
            System.out.println("EEL --- anchoredPredatorDuckSeconds " + duckSeconds);
            CompanionAgent a = new CompanionAgent(tun, 7, Locomotion.ANCHORED, 0.44f,
                    Temperament.named(temperament), null, world(null, CompanionWorld.Held.NONE));
            run(a, temperament + " calm", 20, null, CompanionWorld.Held.NONE);
            run(a, temperament + " predator held", 60, null, CompanionWorld.Held.PREDATOR);
            run(a, temperament + " hand empty", 10, null, CompanionWorld.Held.NONE);
            for (double d : new double[] {6, 3.5, 2}) {
                PointOfInterest zombie = new PointOfInterest(PointOfInterest.Kind.HOSTILE, 42,
                        new Vec(0, 1, d), Vec.ZERO, 0.3);
                run(a, temperament + " zombie " + d, 60, zombie, CompanionWorld.Held.NONE);
                run(a, temperament + " zombie gone", 10, null, CompanionWorld.Held.NONE);
            }
        }
    }

    private static void run(CompanionAgent a, String label, int seconds, PointOfInterest p, CompanionWorld.Held held) {
        int duckTotal = 0;
        StringBuilder trace = new StringBuilder();
        for (int s = 0; s < seconds; s++) {
            int duck = 0;
            for (int t = 0; t < 20; t++) {
                a.step(world(p, held));
                if (a.poseHint() == PoseHint.DUCK) duck++;
            }
            duckTotal += duck;
            if (s < 18) trace.append(String.format(Locale.ROOT, " %d:%d/%.2f/%s", s, duck, a.wariness(), a.poseHint()));
        }
        System.out.printf(Locale.ROOT, "EEL %-28s duck %5.1f s of %d  slot %s home %s  [sec:duckTicks/wariness]%s%n",
                label, duckTotal / 20.0, seconds, a.perchSlot(), a.perchHome(), trace);
    }
}

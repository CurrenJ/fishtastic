package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic: one hash per formation × script × seed over every member's position and heading, for
 * proving that groups built from explicit party members (docs/fish-companions-party.md §3) leave
 * today's benthic groups bitwise unchanged. {@link FingerprintProbe} covers the trio; this covers
 * the ground formations it never hashed. Run with -PsimStdout and diff the {@code FPG} lines.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class GroupFingerprintProbe {

    @Test
    void groupFingerprints() {
        List<String> scripts = new ArrayList<>(OwnerScript.GROUND_NAMES);
        for (String s : List.of("walk", "sprint_stop", "teleport")) if (!scripts.contains(s)) scripts.add(s);
        for (BenthicGroup.Formation formation : BenthicGroup.Formation.values()) {
            float length = formation == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
            for (int count = 2; count <= 3; count++) {
                for (String script : scripts) {
                    for (long seed = 1; seed <= 3; seed++) {
                        OwnerScript owner = OwnerScript.named(script, seed);
                        CompanionWorld world = owner.nextWorld();
                        BenthicGroup group = new BenthicGroup(CompanionTunables.defaults(), seed, length,
                                Temperament.BASELINE, GroundGait.CRAWL, count, formation, world);
                        long h = 1125899906842597L;
                        for (int tick = 0; tick < 2400; tick++) {
                            if (tick > 0) world = owner.nextWorld();
                            group.step(world);
                            for (int i = 0; i < group.count(); i++) {
                                CompanionAgent a = group.member(i);
                                h = 31 * h + Double.doubleToLongBits(a.position().x());
                                h = 31 * h + Double.doubleToLongBits(a.position().y());
                                h = 31 * h + Double.doubleToLongBits(a.position().z());
                                h = 31 * h + Float.floatToIntBits(a.yaw());
                                h = 31 * h + Float.floatToIntBits(a.pitch());
                            }
                        }
                        System.out.printf("FPG %-5s x%d %-22s #%d %016x%n", formation, count, script, seed, h);
                    }
                }
            }
        }
    }
}

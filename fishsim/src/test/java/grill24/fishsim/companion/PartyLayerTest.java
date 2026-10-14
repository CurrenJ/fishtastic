package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import grill24.fishsim.harness.companion.PartyRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PartyLayer's contract (docs/fish-companions-party.md §6): a one-unit party is bitwise the unit
 * stepped on its own, and the step order can't change results.
 */
class PartyLayerTest {

    static final int TICKS = 600;

    private static PartyRun.Party party(String name) {
        return PartyRun.PARTIES.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void oneMemberPartyIsBitwiseASolo() {
        for (SoloCase c : SoloCase.values()) {
            PartyRun.Spec spec = c.spec;
            PartyRun.Party p = new PartyRun.Party("solo", List.of(spec));
            CompanionTunables t = CompanionTunables.defaults();
            OwnerScript a = OwnerScript.named(c.script, 3), b = OwnerScript.named(c.script, 3);
            CompanionWorld wa = a.nextWorld(), wb = b.nextWorld();
            PartyLayer layer = p.build(t, Temperament.BASELINE, 3, wa);
            PartyGrouping.Group g = p.groups(3).get(0);
            CompanionAgent bare = new CompanionAgent(t, g.seed(), spec.loco(), spec.length(), Temperament.BASELINE,
                    spec.gait(), spec.drift(), wb);
            for (int k = 0; k < TICKS; k++) {
                layer.step(a.nextWorld());
                bare.step(b.nextWorld());
                assertBitwise(bare, layer.unit(0).leader(), c + " tick " + k);
            }
        }
    }

    @Test
    void identicalSchoolIsBitwiseItsGroup() {
        PartyRun.Party p = new PartyRun.Party("trio", List.of(
                PartyRun.Spec.of("blue", grill24.fishsim.core.Locomotion.FREE_SWIM, 0.16f),
                PartyRun.Spec.of("blue", grill24.fishsim.core.Locomotion.FREE_SWIM, 0.16f),
                PartyRun.Spec.of("blue", grill24.fishsim.core.Locomotion.FREE_SWIM, 0.16f)));
        CompanionTunables t = CompanionTunables.defaults();
        OwnerScript a = OwnerScript.named("walk_turns", 5), b = OwnerScript.named("walk_turns", 5);
        PartyLayer layer = p.build(t, Temperament.BASELINE, 5, a.nextWorld());
        assertEquals(1, layer.size());
        CompanionGroup bare = p.groups(5).get(0).school(t, Temperament.BASELINE, b.nextWorld());
        for (int k = 0; k < TICKS; k++) {
            layer.step(a.nextWorld());
            bare.step(b.nextWorld());
            CompanionGroup s = layer.unit(0).school();
            assertBitwise(bare.leader(), s.leader(), "leader tick " + k);
            for (int i = 0; i < bare.followerCount(); i++) {
                assertEquals(bare.follower(i).position(), s.follower(i).position(), "follower " + i + " tick " + k);
            }
        }
    }

    @Test
    void stepOrderDoesNotChangeResults() {
        for (String name : List.of("3 free swimmers (40/30/25)", "crab + drifter + glider", "starfish pair + crab")) {
            PartyRun.Party p = party(name);
            CompanionTunables t = CompanionTunables.defaults();
            OwnerScript a = OwnerScript.named("walk_turns", 2), b = OwnerScript.named("walk_turns", 2);
            List<PartyGrouping.Group> groups = p.groups(2);
            List<PartyGrouping.Group> reversed = new ArrayList<>(groups);
            java.util.Collections.reverse(reversed);
            PartyLayer fwd = layer(p, t, groups, a.nextWorld()), back = layer(p, t, reversed, b.nextWorld());
            int n = groups.size();
            for (int k = 0; k < TICKS; k++) {
                fwd.step(a.nextWorld());
                back.step(b.nextWorld());
                for (int i = 0; i < n; i++) {
                    assertBitwise(fwd.unit(i).leader(), back.unit(n - 1 - i).leader(), name + " unit " + i + " tick " + k);
                }
            }
        }
    }

    @Test
    void partyMatesAreSeen() {
        PartyRun.Party p = party("3 free swimmers (40/30/25)");
        CompanionTunables t = CompanionTunables.defaults();
        OwnerScript a = OwnerScript.named("idle_looking", 1), b = OwnerScript.named("idle_looking", 1);
        List<PartyGrouping.Group> groups = p.groups(1);
        PartyLayer party = layer(p, t, groups, a.nextWorld());
        PartyLayer alone = layer(p, t, List.of(groups.get(0)), b.nextWorld());
        boolean differs = false;
        for (int k = 0; k < TICKS && !differs; k++) {
            party.step(a.nextWorld());
            alone.step(b.nextWorld());
            differs = !party.unit(0).leader().position().equals(alone.unit(0).leader().position());
        }
        assertTrue(differs, "a party-mate should change what the lead does");
    }

    private static PartyLayer layer(PartyRun.Party p, CompanionTunables t, List<PartyGrouping.Group> groups,
                                    CompanionWorld w) {
        return new PartyLayer(t, groups, m -> Temperament.BASELINE, m -> p.members().get(m.slot()).drift(), w);
    }

    private static void assertBitwise(CompanionAgent want, CompanionAgent got, String where) {
        assertEquals(want.position(), got.position(), where);
        assertEquals(Float.floatToIntBits(want.yaw()), Float.floatToIntBits(got.yaw()), where);
        assertEquals(Float.floatToIntBits(want.appear()), Float.floatToIntBits(got.appear()), where);
        assertEquals(want.goal(), got.goal(), where);
    }

    /** One solo per class, on a script that exercises it. */
    private enum SoloCase {
        SWIM(PartyRun.Spec.of("a", grill24.fishsim.core.Locomotion.FREE_SWIM, 0.40f), "walk_turns"),
        GLIDE(PartyRun.Spec.of("ray", grill24.fishsim.core.Locomotion.GLIDE, 0.70f), "walk"),
        DRIFT(PartyRun.Spec.of("j", grill24.fishsim.core.Locomotion.DRIFT, 0.35f)
                .withDrift(DriftStyle.JET), "walk"),
        CRAB(PartyRun.Spec.of("crab", grill24.fishsim.core.Locomotion.BENTHIC, 0.30f), "walk_flat"),
        ANCHORED(PartyRun.Spec.of("eel", grill24.fishsim.core.Locomotion.ANCHORED, 0.45f), "walk");

        final PartyRun.Spec spec;
        final String script;

        SoloCase(PartyRun.Spec spec, String script) {
            this.spec = spec;
            this.script = script;
        }
    }
}

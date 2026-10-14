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
 * stepped on its own, and the step order can't change results. Phase 2d (§6.1): every percher has
 * its own home, anchored fish first on the head (D5), and with leases no two units ride one perch.
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
        for (String name : List.of("crab + octopus + slug", "crab + drifter + glider", "starfish pair + crab")) {
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
        List<PartyGrouping.Group> groups = p.groups(1, false); // three separate swimmers (pre-D13)
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

    @Test
    void anchoredFishTakeHeadThenShoulders() {
        PartyLayer layer = party("3 anchored").build(CompanionTunables.defaults(), Temperament.BASELINE, 1,
                OwnerScript.named("walk", 1).nextWorld());
        List<PerchBody.Slot> homes = new ArrayList<>();
        for (PartyLayer.Unit u : layer.units()) homes.add(u.leader().perchHome());
        assertEquals(List.of(PerchBody.Slot.HEAD, PerchBody.Slot.RIGHT_SHOULDER, PerchBody.Slot.LEFT_SHOULDER), homes);
    }

    @Test
    void loneAnchoredHomesOnTheHeadUnlessSwitchedOff() {
        PartyRun.Party p = new PartyRun.Party("eel", List.of(
                PartyRun.Spec.of("eel", grill24.fishsim.core.Locomotion.ANCHORED, 0.45f)));
        CompanionTunables t = CompanionTunables.defaults();
        assertEquals(PerchBody.Slot.HEAD, p.build(t, Temperament.BASELINE, 1,
                OwnerScript.named("walk", 1).nextWorld()).unit(0).leader().perchHome());
        t.anchoredHead = false;
        assertEquals(PerchBody.Slot.BACK, p.build(t, Temperament.BASELINE, 1,
                OwnerScript.named("walk", 1).nextWorld()).unit(0).leader().perchHome());
    }

    @Test
    void perchersKeepTheirOwnHomeWhenFree() {
        // The school pair has no perches, so the percher keeps the home it has alone, on every seed.
        PartyRun.Party p = party("school pair + percher");
        CompanionTunables t = CompanionTunables.defaults();
        for (long seed = 1; seed <= 6; seed++) {
            List<PartyGrouping.Group> groups = p.groups(seed);
            PartyLayer party = layer(p, t, groups, OwnerScript.named("walk", seed).nextWorld());
            PartyLayer alone = layer(p, t, List.of(groups.get(1)), OwnerScript.named("walk", seed).nextWorld());
            assertEquals(alone.unit(0).leader().perchHome(), party.unit(1).leader().perchHome(), "seed " + seed);
        }
    }

    @Test
    void homesAreDistinctAndLeasesStopClashes() {
        for (String name : List.of("crab + octopus + slug", "starfish pair + crab", "3 anchored")) {
            for (String script : List.of("walk_turns", "held_items", "hostile_approach", "cat_lounging")) {
                for (long seed = 1; seed <= 2; seed++) {
                    PartyRun.Party p = party(name);
                    PartyRun.Result r = PartyRun.measure(p, script, seed, TICKS, CompanionTunables.defaults(),
                            Temperament.BASELINE, null);
                    assertEquals(0, r.perchClashTicks(), name + " " + script + "#" + seed);
                    PartyLayer layer = p.build(CompanionTunables.defaults(), Temperament.BASELINE, seed,
                            OwnerScript.named(script, seed).nextWorld());
                    java.util.Set<PerchBody.Slot> homes = new java.util.HashSet<>();
                    int perchers = 0;
                    for (PartyLayer.Unit u : layer.units()) {
                        for (CompanionAgent a : u.agents()) {
                            if (a.perchHome() == null) continue;
                            perchers++;
                            homes.add(a.perchHome());
                        }
                    }
                    assertEquals(perchers, homes.size(), name + " homes " + homes);
                }
            }
        }
    }

    /**
     * HandClock (owner feedback 2026-10-14): a party's perchers share one clock, so the unseen
     * timer never calls a fish to the hand within handGapMinSeconds of any landing there, and the
     * turn goes round: every member that can visit the hand gets there.
     */
    @Test
    void partySharesOneHandClock() {
        CompanionTunables t = CompanionTunables.defaults();
        int minGap = Math.round(t.handGapMinSeconds * 20);
        int unseen = PerchBody.HandWhy.UNSEEN.ordinal();
        for (String name : List.of("crab + octopus + slug", "starfish pair + crab")) {
            for (String script : List.of("walk_turns", "walk_flat", "idle_fixed", "forest")) {
                for (long seed = 1; seed <= 2; seed++) {
                    PartyRun.Party p = party(name);
                    OwnerScript os = OwnerScript.named(script, seed);
                    PartyLayer layer = p.build(t, Temperament.BASELINE, seed, os.nextWorld());
                    List<CompanionAgent> agents = new ArrayList<>();
                    for (PartyLayer.Unit u : layer.units()) agents.add(u.leader());
                    int[] total = new int[agents.size()], byUnseen = new int[agents.size()];
                    int lastLanding = Integer.MIN_VALUE / 2;
                    for (int k = 0; k < 4800; k++) {
                        layer.step(os.nextWorld());
                        for (int i = 0; i < agents.size(); i++) {
                            int[] v = agents.get(i).handVisits();
                            int all = java.util.Arrays.stream(v).sum();
                            if (v[unseen] > byUnseen[i]) {
                                assertTrue(k - lastLanding >= minGap, name + " " + script + "#" + seed
                                        + ": unseen hand call " + (k - lastLanding) + " ticks after the last landing");
                            }
                            if (all > total[i]) lastLanding = k;
                            total[i] = all;
                            byUnseen[i] = v[unseen];
                        }
                    }
                    if (script.equals("walk_turns")) {
                        for (int i = 0; i < agents.size(); i++) {
                            assertTrue(total[i] > 0, name + " " + script + "#" + seed + ": unit " + i + " never on the hand");
                        }
                    }
                }
            }
        }
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

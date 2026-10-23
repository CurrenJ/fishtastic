package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Party-built ground formations of mixed sizes (docs/fish-companions.md, D8), held to
 * {@link BenthicGroupTest}'s formation floor over three seeds: members never in a block, finite,
 * never sharing a perch, rarely stacked or underfoot, a line keeping its order, and nothing fading
 * where the group can follow.
 *
 * <p>Lines allow no fades on any formation script, {@code sprint_away} included. The named gap
 * MIXED_SPRINT_FADE (a big leader outwalking and fading its smaller followers) closed with D9: the
 * party sorts a group smallest first, so the leader is the slowest walker. A line built from a
 * big-first party ("line 50/40/30 by slot") is grouped through {@link PartyGrouping} like the game's,
 * so it walks 30/40/50. Crowds keep their existing sprint exemption (tiny slugs can't keep up with a
 * sprint whatever the order; docs/fish-companions.md).
 */
class MixedGroundInvariantTest {

    private record Config(String name, float[] lengths, BenthicGroup.Formation formation) {}

    static final List<Config> CONFIGS = List.of(
            new Config("line 30/40/50", new float[]{0.30f, 0.40f, 0.50f}, BenthicGroup.Formation.LINE),
            new Config("line 50/40/30 by slot", partyOrder(0.50f, 0.40f, 0.30f), BenthicGroup.Formation.LINE),
            new Config("starfish pair", CompanionRun.STARFISH_PAIR, BenthicGroup.Formation.LINE),
            new Config("crowd 8/12/18", CompanionRun.SLUG_CROWD_MIXED, BenthicGroup.Formation.CROWD));

    /** The formation order {@link PartyGrouping} gives a ground party whose slots hold these lengths. */
    static float[] partyOrder(float... bySlot) {
        List<PartyGrouping.Member> members = new ArrayList<>();
        for (int slot = 0; slot < bySlot.length; slot++) {
            members.add(new PartyGrouping.Member("starfish", grill24.fishsim.core.Locomotion.BENTHIC,
                    GroundGait.CRAWL, BenthicGroup.Formation.LINE, bySlot[slot], 1, slot));
        }
        return PartyGrouping.group(members).get(0).lengths();
    }

    @Test
    void mixedFormationsKeepTheGroupFloor() {
        List<String> failures = new ArrayList<>();
        for (Config c : CONFIGS) {
            for (String script : List.of("walk_flat", "walk_meander", "stairs_up_down", "long_idle_then_walk",
                    "sprint_away", "group_passers", "walk_backward")) {
                for (long seed = 1; seed <= 3; seed++) {
                    LocalVoxels truth = new LocalVoxels();
                    List<String> runFailures = new ArrayList<>();
                    GroupRun r = new GroupRun(script, seed, c.lengths(), Temperament.BASELINE, c.formation())
                            .run((tick, world, group) -> {
                                truth.update(world.blocks(), world.owner().eye());
                                for (int i = 0; i < group.count(); i++) {
                                    CompanionAgent a = group.member(i);
                                    if (runFailures.size() < 2 && !truth.sphereClear(a.position(), a.radius() * 0.95))
                                        runFailures.add("penetration member=" + i + " t=" + tick);
                                    if (runFailures.size() < 2 && (!Double.isFinite(a.position().x())
                                            || !Double.isFinite(a.position().y()) || !Double.isFinite(a.position().z())))
                                        runFailures.add("non-finite member=" + i + " t=" + tick);
                                }
                            });
                    String why = c.name() + " " + script + "#" + seed + " " + r.summary();
                    for (String f : runFailures) failures.add(f + " " + why);
                    if (!r.slotErrors.isEmpty()) failures.add("shared slot " + r.slotErrors.get(0) + " " + why);
                    boolean formationScript = List.of("walk_flat", "walk_meander", "sprint_away", "group_passers",
                            "walk_backward").contains(script);
                    if (formationScript) {
                        if (r.deepTicks > 72) failures.add("stacked " + why);
                        if (r.underfootTicks > 36) failures.add("underfoot " + why);
                        boolean jostled = script.equals("walk_backward") || script.equals("group_passers");
                        if (c.formation() == BenthicGroup.Formation.LINE && r.orderShare() > (jostled ? 0.18 : 0.08))
                            failures.add("line order " + why);
                        boolean slowForSprint = c.formation() == BenthicGroup.Formation.CROWD && script.equals("sprint_away");
                        if (!slowForSprint && r.fades > 0) failures.add("fade " + why);
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }
}

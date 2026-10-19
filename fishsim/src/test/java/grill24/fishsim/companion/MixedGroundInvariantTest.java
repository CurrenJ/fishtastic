package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Party-built ground formations of mixed sizes (docs/fish-companions-party.md §3, D8), held to
 * {@link BenthicGroupTest}'s formation floor over three seeds: members never in a block, finite,
 * never sharing a perch, rarely stacked or underfoot, a line keeping its order, and nothing fading
 * where the group can follow.
 *
 * <p>Named gap MIXED_SPRINT_FADE: on {@code sprint_away} a line led by a bigger fish can fade its
 * smaller followers (the leader sets the pace; a follower's catch-up speed scales with its own
 * body). Identical lines of 0.3 and 0.5 fade there too (seeds 7 and 1 of 8); only the 0.4 line
 * {@link BenthicGroupTest} is tuned on never does. Up to {@link #SPRINT_FADES} fades a run are
 * allowed there; every other formation script still allows none.
 */
class MixedGroundInvariantTest {

    private record Config(String name, float[] lengths, BenthicGroup.Formation formation) {}

    /** Measured worst over 8 seeds: 4 (line 50/40/30, seed 1; the identical 0.5 line fades 2 there). */
    static final int SPRINT_FADES = 4;

    static final List<Config> CONFIGS = List.of(
            new Config("line 30/40/50", new float[]{0.30f, 0.40f, 0.50f}, BenthicGroup.Formation.LINE),
            new Config("line 50/40/30", new float[]{0.50f, 0.40f, 0.30f}, BenthicGroup.Formation.LINE),
            new Config("starfish pair", CompanionRun.STARFISH_PAIR, BenthicGroup.Formation.LINE),
            new Config("crowd 8/12/18", CompanionRun.SLUG_CROWD_MIXED, BenthicGroup.Formation.CROWD));

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
                        int allowed = script.equals("sprint_away") ? SPRINT_FADES : 0;
                        if (!slowForSprint && r.fades > allowed) failures.add("fade " + why);
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }
}

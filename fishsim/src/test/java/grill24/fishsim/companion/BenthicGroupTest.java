package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenthicGroupTest {
    @Test
    void formationsWalkWithoutPenetratingTerrain() {
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (BenthicGroup.Formation formation : BenthicGroup.Formation.values()) {
            float length = formation == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
            for (String script : List.of("walk_flat", "walk_meander", "stairs_up_down", "long_idle_then_walk")) {
                for (long seed = 1; seed <= 3; seed++) {
                    OwnerScript owner = OwnerScript.named(script, seed);
                    CompanionWorld world = owner.nextWorld();
                    BenthicGroup group = new BenthicGroup(CompanionTunables.defaults(), seed, length,
                            Temperament.BASELINE, GroundGait.CRAWL, 3, formation, world);
                    LocalVoxels truth = new LocalVoxels();
                    int[] overlaps = new int[3];
                    for (int tick = 0; tick < 2400; tick++) {
                        if (tick > 0) world = owner.nextWorld();
                        group.step(world);
                        truth.update(world.blocks(), world.owner().eye());
                        for (int i = 0; i < group.count(); i++) {
                            CompanionAgent a = group.member(i);
                            String why = formation + " " + script + "#" + seed + " member=" + i + " t=" + tick;
                            if (Boolean.getBoolean("simStdout") && a.groundLag() > 12 && tick % 5 == 0)
                                System.out.println("LAG " + why + " lag=" + a.groundLag() + " mode=" + a.landMode()
                                        + " p=" + a.position() + " v=" + a.velocity().horizontalLength()
                                        + " target=" + a.target() + " ahead=" + (i > 0 ? group.member(i - 1).position() : "owner"));
                            assertTrue(truth.sphereClear(a.position(), a.radius() * 0.95), "penetration " + why);
                            if (a.onGround() && a.feetDown()) {
                                if (a.position().sub(world.owner().feet()).horizontalLength() < 0.4) {
                                    overlaps[i]++;
                                    if (Boolean.getBoolean("simStdout")) System.out.println("OVERLAP " + why
                                            + " pos=" + a.position() + " owner=" + world.owner().feet()
                                            + " yaw=" + a.yaw() + " v=" + a.velocity() + " target=" + a.target());
                                }
                                double floor = Double.NEGATIVE_INFINITY;
                                for (int x = (int) Math.floor(a.position().x() - a.radius());
                                     x <= (int) Math.floor(a.position().x() + a.radius()); x++) {
                                    for (int z = (int) Math.floor(a.position().z() - a.radius());
                                         z <= (int) Math.floor(a.position().z() + a.radius()); z++) {
                                        double f = truth.columnFloor(x, z, a.position().y() - a.radius() + 0.02, 24);
                                        if (!Double.isNaN(f)) floor = Math.max(floor, f);
                                    }
                                }
                                assertEquals(floor, a.position().y() - a.radius() - LocalVoxels.GROUND_LIFT, 0.01, why);
                            }
                        }
                    }
                    for (int i = 0; i < group.count(); i++) {
                        CompanionAgent a = group.member(i);
                        String why = formation + " " + script + "#" + seed + " member=" + i;
                        if (overlaps[i] > 12) failures.add("underfoot " + why + " ticks=" + overlaps[i]);
                        if (script.equals("walk_flat") && a.groundFades() != 0) failures.add("fade " + why
                                + " count=" + a.groundFades());
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void allocateSlotsAndStaggerClimbs() {
        OwnerScript script = OwnerScript.named("mine_in_place", 1);
        CompanionWorld world = script.nextWorld();
        BenthicGroup group = new BenthicGroup(CompanionTunables.defaults(), 1, 0.12f,
                Temperament.DOCILE, GroundGait.CRAWL, 3, BenthicGroup.Formation.CROWD, world);
        assertEquals(PerchBody.Slot.LEFT_SHOULDER, group.member(0).perchSlot());
        assertEquals(PerchBody.Slot.RIGHT_SHOULDER, group.member(1).perchSlot());
        assertEquals(PerchBody.Slot.HEAD, group.member(2).perchSlot());
        int[] starts = {-1, -1, -1};
        for (int tick = 0; tick < 1500; tick++) {
            if (tick > 0) world = script.nextWorld();
            group.step(world);
            for (int i = 0; i < 3; i++) {
                if (starts[i] < 0 && group.member(i).landMode().equals("CLIMB")) starts[i] = tick;
            }
        }
        assertTrue(starts[0] >= 0 && starts[1] > starts[0] && starts[2] > starts[1],
                "climbs should start in order: " + java.util.Arrays.toString(starts));
        for (int i = 0; i < 3; i++) assertEquals(1, group.member(i).climbs(), "member " + i);
    }

    /**
     * Formation invariants (spec §20), line (0.4) and crowd (0.12), three seeds: perched members
     * never share a slot; members are rarely stacked (centres closer than half their contact
     * distance); a line keeps its order along the way the leader went; players walking through
     * the group (passers, the owner backing up) rarely have a member underfoot, and nothing fades
     * where the group can follow. Bounds are a margin over the measured worst (§20.2).
     */
    @Test
    void formationsKeepOrderRoomAndTheirFeetClear() {
        List<String> failures = new java.util.ArrayList<>();
        for (BenthicGroup.Formation formation : BenthicGroup.Formation.values()) {
            float length = formation == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
            for (String script : List.of("walk_flat", "walk_meander", "sprint_away", "group_passers", "walk_backward")) {
                for (long seed = 1; seed <= 3; seed++) {
                    GroupRun r = new GroupRun(script, seed, length, Temperament.BASELINE, formation).run(null);
                    String why = formation + " " + script + "#" + seed + " " + r.summary();
                    if (!r.slotErrors.isEmpty()) failures.add("shared slot " + r.slotErrors.get(0) + " " + why);
                    // Measured worst 51 of 2400 (crowd, owner backing into it).
                    if (r.deepTicks > 72) failures.add("stacked " + why);
                    // Measured worst 13 member-ticks of 7200.
                    if (r.underfootTicks > 36) failures.add("underfoot " + why);
                    // Measured worst: 4.5% on walks and sprints, 13.2% backing up, 7.3% with passers.
                    boolean jostled = script.equals("walk_backward") || script.equals("group_passers");
                    if (formation == BenthicGroup.Formation.LINE && r.orderShare() > (jostled ? 0.18 : 0.08))
                        failures.add("line order " + why);
                    // Slugs can't keep up with a sprint (they fade by design); everything else follows.
                    boolean slowForSprint = formation == BenthicGroup.Formation.CROWD && script.equals("sprint_away");
                    if (!slowForSprint && r.fades != 0) failures.add("fade " + why);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /**
     * Settling on and stepping off, cycle after cycle: every member climbs onto its own perch and
     * hops down again at least twice in three minutes, and no two ever share a slot.
     */
    @Test
    void climbCyclesHandOutUniqueSlotsEachTime() {
        for (BenthicGroup.Formation formation : BenthicGroup.Formation.values()) {
            float length = formation == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
            for (long seed = 1; seed <= 3; seed++) {
                GroupRun r = new GroupRun("climb_cycles", seed, length, Temperament.BASELINE, formation).ticks(3600).run(null);
                String why = formation + " climb_cycles#" + seed + " " + r.summary();
                assertTrue(r.slotErrors.isEmpty(), "shared slot " + why);
                for (int i = 0; i < 3; i++) {
                    assertTrue(r.climbs[i] >= 2 && r.dismounts[i] >= 2, "member " + i + " " + why);
                }
                assertEquals(0, r.fades, why);
            }
        }
    }

    /**
     * Cover in a group: each member's cover is its own perch. Before, a cat sent all three to the
     * head (287-579 shared-slot ticks, members coincident, in 5 of 6 runs); a hostile sent all to
     * one shoulder. They still go up: on average more than one member is on the owner while a cat
     * is about (measured 1.2-1.6 with and without the change).
     */
    @Test
    void coverFromCatsAndHostilesKeepsSlotsUnique() {
        for (BenthicGroup.Formation formation : BenthicGroup.Formation.values()) {
            float length = formation == BenthicGroup.Formation.LINE ? 0.4f : 0.12f;
            for (String script : List.of("cat_lounging", "hostile_approach")) {
                for (long seed = 1; seed <= 3; seed++) {
                    GroupRun r = new GroupRun(script, seed, length, Temperament.BASELINE, formation).run(null);
                    String why = formation + " " + script + "#" + seed + " " + r.summary();
                    assertTrue(r.slotErrors.isEmpty(), "shared slot " + why);
                    if (script.equals("cat_lounging"))
                        assertTrue(r.threatRiding > r.threatTicks, "too few up on the owner with a cat about: " + why);
                }
            }
        }
    }

    @Test
    void rejectsMoreMembersThanPerches() {
        CompanionWorld world = OwnerScript.named("walk_flat", 1).nextWorld();
        assertThrows(IllegalArgumentException.class, () -> new BenthicGroup(CompanionTunables.defaults(),
                1, 0.4f, Temperament.BASELINE, GroundGait.CRAWL, 4, BenthicGroup.Formation.LINE, world));
    }
}

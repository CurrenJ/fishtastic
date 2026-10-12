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

    @Test
    void rejectsMoreMembersThanPerches() {
        CompanionWorld world = OwnerScript.named("walk_flat", 1).nextWorld();
        assertThrows(IllegalArgumentException.class, () -> new BenthicGroup(CompanionTunables.defaults(),
                1, 0.4f, Temperament.BASELINE, GroundGait.CRAWL, 4, BenthicGroup.Formation.LINE, world));
    }
}

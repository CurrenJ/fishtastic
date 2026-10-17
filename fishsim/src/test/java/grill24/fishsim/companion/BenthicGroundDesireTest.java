package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BenthicGroundDesireTest {
    private static final BlockQuery FLOOR = (x, y, z) -> y < 0;
    private static final OwnerState OWNER = new OwnerState(new Vec(0, 1.62, 0), Vec.ZERO,
            0, 0, false, false, true, 70, false);
    private static CompanionWorld world(PointOfInterest point) {
        return new CompanionWorld(OWNER, FLOOR, point == null ? List.of() : List.of(point),
                CompanionWorld.Held.NONE, false);
    }
    private static CompanionAgent agent(Temperament temperament) {
        return new CompanionAgent(CompanionTunables.defaults(), 1, Locomotion.BENTHIC, 0.3f,
                temperament, GroundGait.CRAWL, world(null));
    }

    @Test
    void inspectsWorldItemOnTheFloorAndReturnsWhenItDisappears() {
        CompanionAgent a = agent(Temperament.BASELINE);
        PointOfInterest item = new PointOfInterest(PointOfInterest.Kind.DROPPED_ITEM, 42,
                new Vec(2.5, 0.15, 1), Vec.ZERO, 0.125);
        double closest = Double.POSITIVE_INFINITY;
        int inspectionTicks = 0;
        for (int tick = 0; tick < 400; tick++) {
            a.step(world(tick < 200 ? item : null));
            trace("inspect", tick, a);
            if (a.onGround() && a.poseHint() == PoseHint.NOSE) {
                inspectionTicks++;
                closest = Math.min(closest, a.position().sub(item.pos()).horizontalLength());
                assertEquals(0, a.target().y(), 0.001);
            }
        }
        assertTrue(inspectionTicks > 20);
        assertTrue(closest < 1.2, "must actually approach, closest=" + closest);
        assertNotEquals("INSPECT", a.goal(), "gone items release the detour");
    }

    @Test
    void hostileHidesBehindAnklesButAggressiveGuardsInFront() {
        PointOfInterest hostile = new PointOfInterest(PointOfInterest.Kind.HOSTILE, 42,
                new Vec(0, 1, 2), Vec.ZERO, 0.3);
        for (Temperament temperament : List.of(Temperament.BASELINE, Temperament.AGGRESSIVE)) {
            CompanionAgent a = agent(temperament);
            int responses = 0;
            for (int tick = 0; tick < 200; tick++) {
                a.step(world(hostile));
                if (a.onGround() && (a.goal().equals("HIDE") || a.goal().equals("GUARD"))) {
                    responses++;
                    assertEquals(0, a.target().y(), 0.001);
                    assertTrue(temperament.guard() ? a.target().z() > 0 : a.target().z() < 0);
                }
                assertFalse(a.riding(), "hostiles should elicit a ground response");
            }
            assertTrue(responses > 100, temperament.name());
        }
    }

    @Test
    void docileNuzzleBrushesAnAnkleThenEntersCooldown() {
        CompanionAgent a = agent(Temperament.DOCILE);
        int nuzzleTicks = 0;
        double closest = Double.POSITIVE_INFINITY;
        for (int tick = 0; tick < 400; tick++) {
            a.step(world(null));
            trace("nuzzle", tick, a);
            if (a.onGround() && a.poseHint() == PoseHint.NUZZLE) {
                nuzzleTicks++;
                closest = Math.min(closest, a.position().sub(OWNER.feet()).horizontalLength());
            }
        }
        assertTrue(nuzzleTicks > 20);
        assertTrue(closest < 1.2, "ankle approach=" + closest);
        assertEquals(1, a.nuzzles());
        assertNotEquals("NUZZLE", a.goal());
    }

    @Test
    void formationFollowsItsInspectingLeader() {
        PointOfInterest item = new PointOfInterest(PointOfInterest.Kind.DROPPED_ITEM, 42,
                new Vec(2.7, 0.15, 2), Vec.ZERO, 0.125);
        for (BenthicGroup.Formation formation : BenthicGroup.Formation.values()) {
            BenthicGroup group = new BenthicGroup(CompanionTunables.defaults(), 1, 0.3f,
                    Temperament.BASELINE, GroundGait.CRAWL, 3, formation, world(null));
            double[] closest = {100, 100, 100};
            for (int tick = 0; tick < 260; tick++) {
                group.step(world(item));
                if (group.leader().onGround() && group.leader().poseHint() == PoseHint.NOSE) {
                    for (int i = 0; i < group.count(); i++) {
                        CompanionAgent a = group.member(i);
                        closest[i] = Math.min(closest[i], a.position().sub(item.pos()).horizontalLength());
                    }
                }
            }
            for (int i = 0; i < 3; i++) assertTrue(closest[i] < 2.5,
                    formation + " member=" + i + " distance=" + closest[i]);
        }
    }

    private static void trace(String name, int tick, CompanionAgent a) {
        if (Boolean.getBoolean("simStdout") && tick % 20 == 0)
            System.out.println(name + " t=" + tick + " goal=" + a.goal() + " pose=" + a.poseHint()
                    + " land=" + a.landMode() + " pos=" + a.position() + " target=" + a.target()
                    + " curious=" + a.curiosity() + " inspections=" + a.inspections() + " nuzzles=" + a.nuzzles());
    }
}

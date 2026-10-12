package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BenthicPlayerScatterTest {
    private static final BlockQuery FLOOR = (x, y, z) -> y < 0;

    @Test
    void movingPassersStayOutFromUnderSoloFeet() {
        for (float length : new float[]{0.12f, 0.3f, 0.6f}) {
            for (int direction : new int[]{-1, 1}) {
                for (long seed = 1; seed <= 3; seed++) {
                    OwnerState owner = new OwnerState(new Vec(0, 1.62, 0), Vec.ZERO, 0, 0,
                            false, false, true, 70, false);
                    CompanionAgent agent = new CompanionAgent(CompanionTunables.defaults(), seed,
                            Locomotion.BENTHIC, length, Temperament.BASELINE, GroundGait.CRAWL,
                            CompanionWorld.of(owner, FLOOR));
                    Vec crossing = agent.position();
                    int overlaps = 0;
                    for (int tick = 0; tick < 200; tick++) {
                        Vec feet = new Vec(crossing.x(), 0, crossing.z() + direction * (-6 + tick * 0.2));
                        PointOfInterest passer = new PointOfInterest(PointOfInterest.Kind.PLAYER, 42,
                                feet, new Vec(0, 0, direction * 4), 0.3);
                        agent.step(new CompanionWorld(owner, FLOOR, List.of(passer), CompanionWorld.Held.NONE, false));
                        if (agent.onGround() && agent.feetDown() && agent.position().sub(feet).horizontalLength() < 0.4) overlaps++;
                    }
                    assertTrue(overlaps <= 1, "underfoot length=" + length + " direction=" + direction
                            + " seed=" + seed + " ticks=" + overlaps);
                }
            }
        }
    }

    @Test
    void soloScattersFromCrossingPlayerWithoutChangingSharedDrives() {
        OwnerState owner = new OwnerState(new Vec(0, 1.62, 0), Vec.ZERO, 0, 0,
                false, false, true, 70, false);
        LocalVoxels voxels = new LocalVoxels();
        voxels.update(FLOOR, owner.eye());
        GroundBody body = new GroundBody(Locomotion.BENTHIC, GroundGait.CRAWL,
                new Vec(2, 0.12, 0), 90, 0.1, 0.3f, 1);
        GroundMind mind = new GroundMind(new Breadcrumbs(), body, 1);
        Drives drives = new Drives(Temperament.BASELINE);
        PointOfInterest player = new PointOfInterest(PointOfInterest.Kind.PLAYER, 42,
                new Vec(2, 0, -2), new Vec(0, 0, 4), 0.3);
        mind.playerPoints = List.of(player);
        mind.step(owner, voxels, drives, 0.3f);
        assertTrue(body.urgent, "a solo must react before the passer reaches its feet");
        assertTrue(Math.abs(mind.target.x() - 2) > 1, "escape should go sideways out of the path");
        drives.update(new CompanionWorld(owner, FLOOR, List.of(player), CompanionWorld.Held.NONE, false));
        assertEquals(0, drives.curiosity);
        assertEquals(0, drives.wariness);
    }

    @Test
    void playersOnAnotherFloorDoNotTriggerScatter() {
        OwnerState owner = new OwnerState(new Vec(0, 1.62, 0), Vec.ZERO, 0, 0,
                false, false, true, 70, false);
        LocalVoxels voxels = new LocalVoxels();
        voxels.update(FLOOR, owner.eye());
        GroundBody body = new GroundBody(Locomotion.BENTHIC, GroundGait.CRAWL,
                new Vec(2, 0.12, 0), 90, 0.1, 0.3f, 1);
        GroundMind mind = new GroundMind(new Breadcrumbs(), body, 1);
        mind.playerPoints = List.of(new PointOfInterest(PointOfInterest.Kind.PLAYER, 42,
                new Vec(2, 3, -2), new Vec(0, 0, 4), 0.3));
        mind.step(owner, voxels, new Drives(Temperament.BASELINE), 0.3f);
        assertFalse(body.urgent);
    }
}

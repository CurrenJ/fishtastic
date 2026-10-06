package grill24.fishtastic.client.util;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.ShelterGeometry;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic joining a placed structure's hollow to the frame the simulation steers in — the
 * shelter counterpart of {@link TankFloorsTest}, and worth a test for the same reason: a turned
 * mouth or a dropped offset still yields a shelter of the right size with the right number of
 * mouths, and shows up only as fish nosing into a log's closed end.
 *
 * <p>Every check goes through an independent route rather than restating the formula: block-frame
 * placement against the renderer's own part rotation ({@link CosmeticStructures#rotateOffset}),
 * engine frame against {@link FlockEngine#toRender}, group frame against a real
 * {@link VoxelDomain}'s floor.
 */
class TankSheltersTest {

    private static final float EPS = 1e-4f;
    private static final float SCALE = 0.09f;

    /** A 3-long tube along build x, closed at x = 0 and open at x = 4: one mouth, facing +x. */
    private static ShelterGeometry.Shape tube() {
        Set<ShelterGeometry.Cell> hollow = Set.of(cell(1, 1, 0), cell(2, 1, 0), cell(3, 1, 0));
        Set<ShelterGeometry.Cell> parts = new HashSet<>();
        for (int x = 0; x <= 4; x++)
            for (int y = 0; y <= 2; y++)
                for (int z = -1; z <= 1; z++) {
                    ShelterGeometry.Cell c = cell(x, y, z);
                    if (!hollow.contains(c) && !c.equals(cell(4, 1, 0))) parts.add(c);
                }
        ShelterGeometry.Result result = ShelterGeometry.derive(hollow, parts);
        assertTrue(result.ok(), result.error());
        return result.shape();
    }

    private static ShelterGeometry.Cell cell(int x, int y, int z) {
        return new ShelterGeometry.Cell(x, y, z);
    }

    /**
     * At each of the four structure rotations the shelter turns with the parts: the open end's
     * part position (where the renderer draws build cell (4,1,0)) is where the mouth is, the mouth
     * points in from it, and the closed end's part is outside the interior but inside the hull.
     */
    @Test
    void theShelterTurnsWithTheParts() {
        float anchorX = 0.5f, anchorZ = 0.5f;
        for (Rotation rotation : Rotation.values()) {
            Shelter s = TankShelters.inBlockFrame(tube(), 2, SCALE, anchorX, anchorZ, rotation);
            assertEquals(1, s.mouths().size());
            Shelter.Mouth mouth = s.mouths().get(0);

            // The renderer's own placement of a part at build (bx, by, bz): rotated horizontal
            // offset times scale from the anchor, standing on FLOOR_Y + by × scale.
            float[] openEnd = partCentre(4, 1, 0, rotation, anchorX, anchorZ);
            float[] lastHollow = partCentre(3, 1, 0, rotation, anchorX, anchorZ);
            float[] closedEnd = partCentre(0, 1, 0, rotation, anchorX, anchorZ);

            // The mouth sits halfway between the last hollow cell and the open cell beyond it,
            // and its normal points from the open cell into the hollow.
            assertEquals((openEnd[0] + lastHollow[0]) / 2f, mouth.centerL(), EPS, rotation + " mouth L");
            assertEquals((openEnd[1] + lastHollow[1]) / 2f, mouth.centerY(), EPS, rotation + " mouth Y");
            assertEquals((openEnd[2] + lastHollow[2]) / 2f, mouth.centerD(), EPS, rotation + " mouth D");
            assertEquals((lastHollow[0] - openEnd[0]) / SCALE, mouth.normalL(), EPS, rotation + " normal L");
            assertEquals(0f, mouth.normalY(), EPS);
            assertEquals((lastHollow[2] - openEnd[2]) / SCALE, mouth.normalD(), EPS, rotation + " normal D");
            assertEquals(SCALE / 2f, mouth.halfSize(), EPS);

            assertTrue(s.interior().contains(lastHollow[0], lastHollow[1], lastHollow[2]), rotation + " hollow");
            assertFalse(s.interior().contains(closedEnd[0], closedEnd[1], closedEnd[2]), rotation + " closed end in interior");
            assertTrue(s.hull().contains(closedEnd[0], closedEnd[1], closedEnd[2]), rotation + " closed end outside hull");
            assertEquals(3 * SCALE, s.interiorRun(), EPS);
        }
    }

    private static float[] partCentre(int bx, int by, int bz, Rotation rotation, float anchorX, float anchorZ) {
        float[] r = CosmeticStructures.rotateOffset(rotation, bx, bz);
        return new float[]{anchorX + r[0] * SCALE, CosmeticGridCell.FLOOR_Y + (by + 0.5f) * SCALE, anchorZ + r[1] * SCALE};
    }

    /**
     * Six tank yaws, the quarter turns and two arbitrary ones: a shelter mapped into a lone tank's
     * engine frame and brought back through the engine's own {@code toRender} lands where it was
     * in the block. Checks the hull's corners, so a box turned the wrong way round fails even when
     * its centre survives.
     */
    @Test
    void aLoneTanksFrameRoundTrips() {
        Shelter block = TankShelters.inBlockFrame(tube(), 2, SCALE, 0.3f, 0.6f, Rotation.CLOCKWISE_90);
        float originX = 0.5f, originY = FishTankBlockEntityRenderer.ITEM_BASELINE_Y, originZ = 0.5f;
        float[] p = new float[3];
        float[] back = new float[3];
        float[] q = new float[3];

        for (float yaw : new float[]{0f, 90f, 180f, 270f, 37.5f, -122f}) {
            FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
            engine.rebuild(new FishSpec[]{new FishSpec(0.1f, Locomotion.FREE_SWIM, false, 0)},
                    1L, yaw, 3, 0.35f, 0.3f, 0f);
            Shelter local = TankShelters.toEngine(block, originX, originY, originZ, yaw);

            Shelter.Mouth m = local.mouths().get(0), bm = block.mouths().get(0);
            engine.toRender(m.centerL(), m.centerY(), m.centerD(), back);
            assertEquals(bm.centerL(), back[0] + originX, EPS, yaw + "° mouth L");
            assertEquals(bm.centerY(), back[1] + originY, EPS, yaw + "° mouth Y");
            assertEquals(bm.centerD(), back[2] + originZ, EPS, yaw + "° mouth D");
            engine.toRender(m.normalL(), m.normalY(), m.normalD(), back);
            assertEquals(bm.normalL(), back[0], EPS, yaw + "° normal L");
            assertEquals(bm.normalD(), back[2], EPS, yaw + "° normal D");

            for (int corner = 0; corner < 8; corner++) {
                local.hull().corner(corner, p);
                block.hull().corner(corner, q);
                engine.toRender(p[0], p[1], p[2], back);
                assertEquals(q[0], back[0] + originX, EPS, yaw + "° hull corner " + corner + " L");
                assertEquals(q[1], back[1] + originY, EPS, yaw + "° hull corner " + corner + " Y");
                assertEquals(q[2], back[2] + originZ, EPS, yaw + "° hull corner " + corner + " D");
            }
            assertEquals(block.interiorRun(), local.interiorRun(), EPS);
        }
    }

    /**
     * In a group, a shelter in one member lands in that member, on that member's sand — checked
     * against a real {@link VoxelDomain}'s floor so a dropped member offset or a wrong vertical
     * origin cannot pass by matching itself.
     */
    @Test
    void aGroupShelterLandsInItsMember() {
        // Three tanks in a row along lateral; the shelter is in the middle one. In the group
        // frame the middle block spans lateral [-0.5, 0.5]; the low one [-1.5, -0.5].
        boolean[][][] occupancy = new boolean[3][1][1];
        for (boolean[][] column : occupancy) column[0][0] = true;
        VoxelDomain domain = new VoxelDomain(occupancy, VoxelDomain.DEFAULT_INSET,
                TankFloors.GROUP_SURFACE_OFFSET, null);

        Shelter block = TankShelters.inBlockFrame(tube(), 2, SCALE, 0.3f, 0.5f, Rotation.NONE);
        for (int member = 0; member < 3; member++) {
            // TankShelters.group's origin for member ix of a 3×1×1 group.
            Shelter s = TankShelters.toEngine(block, 3 / 2f - member, 1 / 2f, 1 / 2f, 0f);
            float low = -1.5f + member;
            assertTrue(s.hull().centerL() > low && s.hull().centerL() < low + 1f,
                    "member " + member + "'s shelter landed at " + s.hull().centerL());
            float sand = domain.floor().heightAt(s.hull().centerL(), s.hull().centerD());
            assertEquals(sand, s.hull().centerY() - s.hull().halfY(), EPS, "the hull floats off the sand");
        }
    }
}

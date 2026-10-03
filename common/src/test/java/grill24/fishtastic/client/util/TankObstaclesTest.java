package grill24.fishtastic.client.util;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticObstacles;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.ShippedStructures;
import grill24.fishtastic.fishtank.SpanStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic placing a structure's obstacle boxes where the structure is drawn — the obstacle
 * counterpart of {@link TankSheltersTest}, checked the same way, through independent routes:
 * block-frame placement against the renderer's own part placement at every rotation (the chunk
 * mesh's for floor structures, {@link SpanStructures#layout} for spans), and the lone-tank engine
 * frame against {@link FlockEngine#toRender}.
 */
class TankObstaclesTest {

    private static final float EPS = 1e-4f;
    private static final Predicate<BlockState> SOFT = ShippedStructures.softTag();

    private static boolean inAny(List<Shelter.OrientedBox> boxes, float x, float y, float z) {
        for (Shelter.OrientedBox b : boxes) {
            float[] g = new float[3];
            if (b.signedDistance(x, y, z, g) <= EPS) return true;
        }
        return false;
    }

    private static boolean fullCube(BlockState state) {
        return Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    /** The centre and the six face-centres (pulled in 5%) of a full-cube part of size {@code s} centred at {@code c}. */
    private static void assertPartCovered(List<Shelter.OrientedBox> boxes, float[] c, float s, String what) {
        float in = 0.45f * s;
        float[][] points = {{0, 0, 0}, {in, 0, 0}, {-in, 0, 0}, {0, in, 0}, {0, -in, 0}, {0, 0, in}, {0, 0, -in}};
        for (float[] p : points) {
            assertTrue(inAny(boxes, c[0] + p[0], c[1] + p[1], c[2] + p[2]),
                    what + " at " + (c[0] + p[0]) + "," + (c[1] + p[1]) + "," + (c[2] + p[2]));
        }
    }

    /**
     * Every full-cube solid part of every floor structure, at each rotation, is inside the boxes
     * where the chunk mesh draws it: {@code anchor + rotateOffset(offset) × CELL_WIDTH}
     * horizontally, {@code FLOOR_Y + offsetY × scale} up (TankCosmeticMesh.addStructures).
     * Parts inside a shelter's hull are the shelter's, and skipped — but a gate has no hull, so its
     * posts are checked like any part.
     */
    @Test
    void floorStructuresLandWhereTheyAreDrawn() {
        CosmeticGridCell anchor = new CosmeticGridCell(1, 1);
        int checked = 0;
        for (String name : ShippedStructures.floorNames()) {
            CosmeticStructure structure = ShippedStructures.all().get(name);
            for (Rotation rotation : Rotation.values()) {
                List<Shelter.OrientedBox> boxes = TankObstacles.inBlockFrame(CosmeticObstacles.derive(structure, SOFT),
                        structure.scale(), (float) anchor.localX(), (float) anchor.localZ(), rotation);
                List<Shelter> hull = TankShelters.inBlockFrame(structure, (float) anchor.localX(), (float) anchor.localZ(), rotation)
                        .filter(shelter -> shelter.kind() != Shelter.Kind.GATE).map(List::of).orElse(List.of());
                float s = structure.scale();
                for (CosmeticStructure.StructurePart part : structure.parts()) {
                    if (SOFT.test(part.state()) || !fullCube(part.state())) continue;
                    float[] r = CosmeticStructures.rotateOffset(rotation, part.offsetX(), part.offsetZ());
                    float[] c = {(float) (anchor.localX() + r[0] * CosmeticGridCell.CELL_WIDTH),
                            CosmeticGridCell.FLOOR_Y + part.offsetY() * s + s / 2f,
                            (float) (anchor.localZ() + r[1] * CosmeticGridCell.CELL_WIDTH)};
                    if (!hull.isEmpty() && hull.get(0).hull().contains(c[0], c[1], c[2])) continue;
                    assertPartCovered(boxes, c, s, name + " " + rotation + " " + part.state());
                    checked++;
                }
            }
        }
        assertTrue(checked > 100, "only " + checked + " parts checked");
    }

    /** A spanning structure's parts, at each rotation, where {@link SpanStructures#layout} draws them. */
    @Test
    void spanningStructuresLandWhereTheyAreDrawn() {
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure structure = e.getValue();
            if (structure.span().isEmpty()) continue;
            float s = structure.scale();
            for (Rotation rotation : Rotation.values()) {
                float[] origin = SpanStructures.buildOrigin(structure, rotation);
                List<Shelter.OrientedBox> boxes = TankObstacles.inBlockFrame(CosmeticObstacles.derive(structure, SOFT),
                        s, origin[0], origin[1], rotation);
                List<Shelter> hull = TankShelters.inBlockFrame(structure, origin[0], origin[1], rotation)
                        .filter(shelter -> shelter.kind() != Shelter.Kind.GATE).map(List::of).orElse(List.of());
                List<SpanStructures.Placed> placed = SpanStructures.layout(structure, rotation);
                for (int i = 0; i < placed.size(); i++) {
                    BlockState authored = structure.parts().get(i).state();
                    if (SOFT.test(authored) || !fullCube(authored)) continue;
                    SpanStructures.Placed p = placed.get(i);
                    float[] c = {p.x() + s / 2f, p.y() + s / 2f, p.z() + s / 2f};
                    if (!hull.isEmpty() && hull.get(0).hull().contains(c[0], c[1], c[2])) continue;
                    assertPartCovered(boxes, c, s, e.getKey() + " " + rotation + " part " + i);
                }
            }
        }
    }

    /**
     * Obstacles mapped into a lone tank's engine frame and brought back through the engine's own
     * {@code toRender} land where they were in the block, corner for corner, at six yaws.
     */
    @Test
    void aLoneTanksFrameRoundTrips() {
        CosmeticStructure arch = ShippedStructures.all().get("cosmetic_fence_arch_oak");
        List<Shelter.OrientedBox> block = TankObstacles.inBlockFrame(CosmeticObstacles.derive(arch, SOFT),
                arch.scale(), 0.5f, 0.5f, Rotation.CLOCKWISE_90);
        float originX = 0.5f, originY = FishTankBlockEntityRenderer.ITEM_BASELINE_Y, originZ = 0.5f;
        float[] p = new float[3], q = new float[3], back = new float[3];
        for (float yaw : new float[]{0f, 90f, 180f, 270f, 37.5f, -122f}) {
            FlockEngine engine = new FlockEngine(Tunables.DEFAULT);
            engine.rebuild(new FishSpec[]{new FishSpec(0.1f, Locomotion.FREE_SWIM, false, 0)},
                    1L, yaw, 3, 0.35f, 0.3f, 0f);
            List<Shelter.OrientedBox> local = TankObstacles.toEngine(block, originX, originY, originZ, yaw);
            assertEquals(block.size(), local.size());
            for (int k = 0; k < block.size(); k++) {
                for (int corner = 0; corner < 8; corner++) {
                    local.get(k).corner(corner, p);
                    block.get(k).corner(corner, q);
                    engine.toRender(p[0], p[1], p[2], back);
                    assertEquals(q[0], back[0] + originX, EPS, yaw + "° box " + k + " corner " + corner + " L");
                    assertEquals(q[1], back[1] + originY, EPS, yaw + "° box " + k + " corner " + corner + " Y");
                    assertEquals(q[2], back[2] + originZ, EPS, yaw + "° box " + k + " corner " + corner + " D");
                }
            }
        }
    }
}

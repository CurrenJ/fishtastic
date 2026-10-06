package grill24.fishtastic.client.util;

import grill24.FishtasticRegistries;
import grill24.fishsim.domain.Shelter;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.ShelterGeometry;
import grill24.fishtastic.fishtank.SpanStructures;
import grill24.fishtastic.fishtank.TankGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the shelter structures placed in tanks into the {@link Shelter}s the simulation steers
 * against (docs/fish-shelters.md §3), the way {@link TankFloors} does for the floor. The other of
 * the three classes that know both worlds' frames.
 *
 * <p>Two steps, kept apart so each can be tested on its own:
 * <ol>
 *   <li><b>Build grid → block frame</b> ({@link #inBlockFrame}). A structure's shelter is derived
 *       in its own build grid ({@link ShelterGeometry}) and placed exactly as the renderer places
 *       its parts: a build cell {@code (bx, by, bz)} is a block of size {@code scale} centred at
 *       {@code anchor + rotateOffset(rotation, bx, bz) × scale} horizontally, standing on
 *       {@code FLOOR_Y + by × scale}.</li>
 *   <li><b>Block frame → engine frame</b> ({@link #toEngine}). Subtract the engine's origin, then
 *       apply the same rotation {@code FlockEngine.toLocal} does. A lone tank's engine sits at the
 *       block's centre on the item baseline and turns with the tank's first item; a group's sits at
 *       the group's bounding-box centre, unturned.</li>
 * </ol>
 *
 * <p>A spanning structure's shelter (the Whale Fall's skull) comes from its anchor tank alone, the
 * one that stores it, in the anchor's block frame: it reaches into the box's other tanks, which
 * {@link #group} carries over like any member's. The tanks holding a link to it add nothing, so
 * it is counted once.
 */
public final class TankShelters {

    private TankShelters() {}

    /**
     * Every shelter in one tank, in a lone tank's engine frame: origin at the block's centre on the
     * item baseline, turned by {@code yawDeg} — the frame {@code TankFlockAdapter} rebuilds its
     * engine in.
     */
    public static List<Shelter> single(FishTankBlockEntity be, Level level, float yawDeg) {
        List<Shelter> out = new ArrayList<>();
        for (Shelter shelter : blockFrame(be, level)) {
            out.add(toEngine(shelter, 0.5f, FishTankBlockEntityRenderer.ITEM_BASELINE_Y, 0.5f, yawDeg));
        }
        return out;
    }

    /**
     * Every shelter in a connected group, in the group engine's frame: each member's block frame
     * moved to that member's place in the group's bounding box, whose centre is the origin. The
     * same frame {@link grill24.fishsim.domain.VoxelDomain} lays its grid out in.
     */
    public static List<Shelter> group(TankGroups.Group group, Level level) {
        boolean[][][] occupancy = group.occupancy();
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
        BlockPos min = group.min();
        List<Shelter> out = new ArrayList<>();
        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) continue;
            List<Shelter> local = blockFrame(member, level);
            if (local.isEmpty()) continue;
            // The engine's origin, in this member's block frame.
            float ox = sx / 2f - (memberPos.getX() - min.getX());
            float oy = sy / 2f - (memberPos.getY() - min.getY());
            float oz = sz / 2f - (memberPos.getZ() - min.getZ());
            for (Shelter shelter : local) out.add(toEngine(shelter, ox, oy, oz, 0f));
        }
        return out;
    }

    /** Whether any structure placed in this tank is a shelter. */
    public static boolean hasShelter(FishTankBlockEntity be, Level level) {
        if (be.getStructureCosmetics().isEmpty()) return false;
        return !blockFrame(be, level).isEmpty();
    }

    /** Every shelter structure in one tank, in that tank's block frame (block-local, unturned). */
    static List<Shelter> blockFrame(FishTankBlockEntity be, Level level) {
        Map<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> placed = be.getStructureCosmetics();
        if (placed.isEmpty()) return List.of();
        Registry<CosmeticStructure> registry = level.registryAccess()
                .registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
        List<Shelter> out = new ArrayList<>();
        for (Map.Entry<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> entry : placed.entrySet()) {
            Optional<CosmeticStructure> structure = registry.getOptional(entry.getValue().structureId());
            if (structure.isEmpty()) continue;
            Optional<ShelterGeometry.Shape> shape = structure.get().shelterShape();
            if (shape.isEmpty()) continue;
            Rotation rotation = entry.getValue().rotation();
            float anchorX, anchorZ;
            if (structure.get().span().isPresent()) {
                float[] origin = SpanStructures.buildOrigin(structure.get(), rotation);
                anchorX = origin[0];
                anchorZ = origin[1];
            } else {
                CosmeticGridCell anchor = entry.getKey();
                anchorX = (float) anchor.localX();
                anchorZ = (float) anchor.localZ();
            }
            out.add(inBlockFrame(shape.get(), structure.get().shelter().get().capacityOrDefault(),
                    structure.get().scale(), anchorX, anchorZ, rotation));
        }
        return out;
    }

    /**
     * Places a derived shelter in a tank's block frame, exactly where the renderer draws the
     * structure's parts (see the class doc). The result is axis-aligned, because the four
     * structure rotations are quarter turns.
     *
     * @param anchorX,anchorZ block-local centre of the structure's anchor cell
     */
    static Shelter inBlockFrame(ShelterGeometry.Shape shape, int capacity, float scale,
                                float anchorX, float anchorZ, Rotation rotation) {
        Shelter.OrientedBox hull = box(shape.hullMin(), shape.hullMax(), scale, anchorX, anchorZ, rotation);
        Shelter.OrientedBox interior = box(shape.interiorMin(), shape.interiorMax(), scale, anchorX, anchorZ, rotation);

        List<Shelter.Mouth> mouths = new ArrayList<>();
        for (ShelterGeometry.Mouth mouth : shape.mouths()) {
            int[] out = mouth.outward();
            int normalAxis = mouth.normalAxis();
            int axisA = normalAxis == 0 ? 1 : 0;
            // Build-grid face centre: cell centres are (bx, by + 0.5, bz) in build units.
            float cx = (mouth.min().x() + mouth.max().x()) * 0.5f + out[0] * 0.5f;
            float cy = (mouth.min().y() + mouth.max().y()) * 0.5f + 0.5f + out[1] * 0.5f;
            float cz = (mouth.min().z() + mouth.max().z()) * 0.5f + out[2] * 0.5f;
            float[] c = CosmeticStructures.rotateOffset(rotation, cx, cz);
            float[] n = CosmeticStructures.rotateOffset(rotation, -out[0], -out[2]);
            float[] t = CosmeticStructures.rotateOffset(rotation, axisA == 0 ? 1f : 0f, 0f);
            float tangentY = axisA == 1 ? 1f : 0f;
            int axisB = normalAxis == 2 ? 1 : 2;
            mouths.add(new Shelter.Mouth(
                    anchorX + c[0] * scale, CosmeticGridCell.FLOOR_Y + cy * scale, anchorZ + c[1] * scale,
                    n[0], -out[1], n[1],
                    t[0], tangentY, t[1],
                    mouth.span(axisA) * 0.5f * scale, mouth.span(axisB) * 0.5f * scale));
        }
        return new Shelter(hull, interior, mouths, capacity, shape.interiorRun() * scale);
    }

    /** The block-frame box covering build cells {@code min..max}, turned with the structure. */
    private static Shelter.OrientedBox box(ShelterGeometry.Cell min, ShelterGeometry.Cell max, float scale,
                                           float anchorX, float anchorZ, Rotation rotation) {
        // Build-unit extents: a cell spans ±0.5 about its centre horizontally, [by, by + 1] vertically.
        float[] a = CosmeticStructures.rotateOffset(rotation, min.x() - 0.5f, min.z() - 0.5f);
        float[] b = CosmeticStructures.rotateOffset(rotation, max.x() + 0.5f, max.z() + 0.5f);
        return Shelter.OrientedBox.ofBounds(
                anchorX + Math.min(a[0], b[0]) * scale, CosmeticGridCell.FLOOR_Y + min.y() * scale,
                anchorZ + Math.min(a[1], b[1]) * scale,
                anchorX + Math.max(a[0], b[0]) * scale, CosmeticGridCell.FLOOR_Y + (max.y() + 1) * scale,
                anchorZ + Math.max(a[1], b[1]) * scale);
    }

    /**
     * Moves a block-frame shelter into an engine frame whose origin sits at {@code (ox, oy, oz)}
     * in the block frame and which is turned by {@code yawDeg} — the arithmetic of
     * {@code FlockEngine.toLocal}, applied to points, directions and box orientations alike.
     */
    static Shelter toEngine(Shelter s, float ox, float oy, float oz, float yawDeg) {
        float rad = (float) Math.toRadians(yawDeg);
        float cosR = (float) Math.cos(rad), sinR = (float) Math.sin(rad);
        List<Shelter.Mouth> mouths = new ArrayList<>(s.mouths().size());
        for (Shelter.Mouth m : s.mouths()) {
            float x = m.centerL() - ox, z = m.centerD() - oz;
            mouths.add(new Shelter.Mouth(
                    x * cosR - z * sinR, m.centerY() - oy, x * sinR + z * cosR,
                    m.normalL() * cosR - m.normalD() * sinR, m.normalY(), m.normalL() * sinR + m.normalD() * cosR,
                    m.tangentL() * cosR - m.tangentD() * sinR, m.tangentY(), m.tangentL() * sinR + m.tangentD() * cosR,
                    m.halfTangent(), m.halfBitangent()));
        }
        return new Shelter(toEngine(s.hull(), ox, oy, oz, cosR, sinR), toEngine(s.interior(), ox, oy, oz, cosR, sinR),
                mouths, s.capacity(), s.interiorRun());
    }

    private static Shelter.OrientedBox toEngine(Shelter.OrientedBox b, float ox, float oy, float oz,
                                                float cosR, float sinR) {
        float x = b.centerL() - ox, z = b.centerD() - oz;
        return new Shelter.OrientedBox(
                x * cosR - z * sinR, b.centerY() - oy, x * sinR + z * cosR,
                b.halfL(), b.halfY(), b.halfD(),
                b.cos() * cosR - b.sin() * sinR, b.cos() * sinR + b.sin() * cosR);
    }
}

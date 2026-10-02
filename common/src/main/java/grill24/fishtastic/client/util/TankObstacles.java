package grill24.fishtastic.client.util;

import grill24.FishtasticRegistries;
import grill24.fishsim.domain.Shelter;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.FishTankBlockEntityRenderer;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticObstacles;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.ObstacleGeometry;
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
 * Turns the structures placed in tanks into the obstacle boxes the simulation steers round
 * (docs/fish-shelters.md §12.3), through exactly the frames {@link TankShelters} places a shelter
 * in: build grid → block frame ({@link TankShelters#box}), then block frame → engine frame
 * ({@link TankShelters#toEngine}). A spanning structure comes from its anchor tank alone, as its
 * shelter does.
 */
public final class TankObstacles {

    private TankObstacles() {}

    /** Every obstacle in one tank, in a lone tank's engine frame (see {@link TankShelters#single}). */
    public static List<Shelter.OrientedBox> single(FishTankBlockEntity be, Level level, float yawDeg) {
        return toEngine(blockFrame(be, level), 0.5f, FishTankBlockEntityRenderer.ITEM_BASELINE_Y, 0.5f, yawDeg);
    }

    /** Every obstacle in a connected group, in the group engine's frame (see {@link TankShelters#group}). */
    public static List<Shelter.OrientedBox> group(TankGroups.Group group, Level level) {
        boolean[][][] occupancy = group.occupancy();
        int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
        BlockPos min = group.min();
        List<Shelter.OrientedBox> out = new ArrayList<>();
        for (BlockPos memberPos : group.members()) {
            if (!(level.getBlockEntity(memberPos) instanceof FishTankBlockEntity member)) continue;
            List<Shelter.OrientedBox> local = blockFrame(member, level);
            if (local.isEmpty()) continue;
            float ox = sx / 2f - (memberPos.getX() - min.getX());
            float oy = sy / 2f - (memberPos.getY() - min.getY());
            float oz = sz / 2f - (memberPos.getZ() - min.getZ());
            out.addAll(toEngine(local, ox, oy, oz, 0f));
        }
        return out;
    }

    /** Whether any structure placed in this tank has a part solid to fish. */
    public static boolean hasSolid(FishTankBlockEntity be, Level level) {
        if (be.getStructureCosmetics().isEmpty()) return false;
        return !blockFrame(be, level).isEmpty();
    }

    /** Every obstacle of the structures anchored in one tank, in that tank's block frame. */
    static List<Shelter.OrientedBox> blockFrame(FishTankBlockEntity be, Level level) {
        Map<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> placed = be.getStructureCosmetics();
        if (placed.isEmpty()) return List.of();
        Registry<CosmeticStructure> registry = level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
        List<Shelter.OrientedBox> out = new ArrayList<>();
        for (Map.Entry<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> entry : placed.entrySet()) {
            Optional<CosmeticStructure> structure = registry.getOptional(entry.getValue().structureId());
            if (structure.isEmpty()) continue;
            Rotation rotation = entry.getValue().rotation();
            float anchorX, anchorZ;
            if (structure.get().span().isPresent()) {
                float[] origin = SpanStructures.buildOrigin(structure.get(), rotation);
                anchorX = origin[0];
                anchorZ = origin[1];
            } else {
                anchorX = (float) entry.getKey().localX();
                anchorZ = (float) entry.getKey().localZ();
            }
            out.addAll(inBlockFrame(CosmeticObstacles.of(structure.get()), structure.get().scale(),
                    anchorX, anchorZ, rotation));
        }
        return out;
    }

    /** Places a structure's derived boxes in a tank's block frame, where the renderer draws its parts. */
    static List<Shelter.OrientedBox> inBlockFrame(List<ObstacleGeometry.Box> boxes, float scale,
                                                  float anchorX, float anchorZ, Rotation rotation) {
        List<Shelter.OrientedBox> out = new ArrayList<>(boxes.size());
        for (ObstacleGeometry.Box b : boxes) {
            float[] f = b.buildBounds();
            out.add(TankShelters.box(f[0], f[1], f[2], f[3], f[4], f[5], scale, anchorX, anchorZ, rotation));
        }
        return out;
    }

    /** Block frame → an engine frame with its origin at {@code (ox, oy, oz)}, turned by {@code yawDeg}. */
    static List<Shelter.OrientedBox> toEngine(List<Shelter.OrientedBox> boxes, float ox, float oy, float oz, float yawDeg) {
        float rad = (float) Math.toRadians(yawDeg);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        List<Shelter.OrientedBox> out = new ArrayList<>(boxes.size());
        for (Shelter.OrientedBox box : boxes) out.add(TankShelters.toEngine(box, ox, oy, oz, cos, sin));
        return out;
    }
}

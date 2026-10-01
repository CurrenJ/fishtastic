package grill24.fishtastic.fishtank;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockTags;
import grill24.fishtastic.block.FishTankBlock;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a held cosmetic would go in a tank, and whether it can go there. One set of rules, used
 * twice: the server applies the resulting {@link Plan} when the player clicks, and the client
 * draws the same plan as a preview of the targeted cell(s) every tick (see
 * {@code CosmeticPlacementPreview}), so the highlight can never disagree with the click.
 *
 * <p>Targeting ray-casts the player's look across the clicked tank's whole connected group, not
 * just the tank that was clicked: aiming through one tank's glass at the sand (or a cosmetic, or
 * the lid) of a tank behind or below it targets that tank. The nearest hit along the ray wins, so
 * a cosmetic standing in front of a floor still takes priority over the floor behind it.
 */
public final class CosmeticPlacement {

    /**
     * PORT-ONLY: {@code Player#blockInteractionRange()} is a 1.20.5+ attribute-backed method; MC20
     * has no reach on the entity at all. Mirrors vanilla's pre-attribute hardcoded reach distances,
     * like {@code FishPileBlock}'s and {@code CosmeticCommand}'s helpers of the same name.
     */
    private static double interactionRange(Player player) {
        return player.isCreative() ? 5.0 : 4.5;
    }

    private CosmeticPlacement() {}

    /** One targeted grid cell, in the tank it belongs to. */
    public record TankCell(FishTankBlockEntity tank, CosmeticGridCell cell) {}

    /**
     * The outcome of aiming a held cosmetic at a tank group: the cells it targets, possibly across
     * several tanks (empty when the ray found nothing), whether they're on the lid, the box of tanks
     * a spanning structure would fill ({@code region}, else null), and — when the placement is
     * allowed — the mutation that performs it. {@code failMessage} is shown to the player when a
     * click is refused for a reason worth explaining.
     */
    public record Plan(List<TankCell> cells, boolean ceiling, @Nullable AABB region,
                       @Nullable Runnable apply, @Nullable String failMessage) {
        public boolean valid() {
            return apply != null;
        }

        static Plan none(boolean ceiling) {
            return new Plan(List.of(), ceiling, null, null, null);
        }

        static Plan refused(Target target, boolean ceiling) {
            return new Plan(List.of(new TankCell(target.tank(), target.cell())), ceiling, null, null, null);
        }

        static Plan of(Target target, boolean ceiling, Runnable apply) {
            return new Plan(List.of(new TankCell(target.tank(), target.cell())), ceiling, null, apply, null);
        }
    }

    /** True if {@code stack} is anything that places as a tank cosmetic. */
    public static boolean isCosmetic(ItemStack stack) {
        return HangingCosmetics.blockOf(stack) != null || cosmeticBlockOf(stack) != null || structureIdOf(stack) != null;
    }

    /** The plan for placing {@code held} where {@code player} is aiming, or null if it isn't a cosmetic. */
    @Nullable
    public static Plan plan(Player player, FishTankBlockEntity clicked, ItemStack held) {
        Block hanging = HangingCosmetics.blockOf(held);
        if (hanging != null) return planHanging(player, clicked, hanging);
        Block cosmetic = cosmeticBlockOf(held);
        if (cosmetic != null) return planFloor(player, clicked, cosmetic);
        ResourceKey<CosmeticStructure> structureId = structureIdOf(held);
        if (structureId != null) return planStructure(player, clicked, structureId);
        return null;
    }

    /**
     * The block to use as a floor cosmetic from the held stack, or null. Accepts custom
     * {@link FishTankCosmeticItem}s and any vanilla {@link BlockItem} whose block is in the
     * {@code #fishtastic:tank_cosmetics} tag.
     */
    @Nullable
    public static Block cosmeticBlockOf(ItemStack stack) {
        if (stack.getItem() instanceof FishTankCosmeticItem custom) return custom.getRenderBlock();
        if (stack.getItem() instanceof BlockItem bi
                && bi.getBlock().defaultBlockState().is(FishtasticBlockTags.TANK_COSMETICS)) return bi.getBlock();
        return null;
    }

    /** The structure to place from the held stack, or null if it isn't a structure cosmetic item. */
    @Nullable
    public static ResourceKey<CosmeticStructure> structureIdOf(ItemStack stack) {
        if (stack.getItem() instanceof FishTankStructureCosmeticItem custom) return custom.getStructureId();
        return null;
    }

    // ── Rules ───────────────────────────────────────────────────────────────────

    /**
     * A single-cell floor cosmetic: an empty cell takes it; a sea pickle takes another pickle; kelp
     * takes another segment while its column has room ({@link TankColumns#maxSegments}) — kelp
     * roots only on sand, so the target is always a column's bottom tank, and in a stack of open
     * tanks a strand carries on up through every storey.
     */
    private static Plan planFloor(Player player, FishTankBlockEntity clicked, Block block) {
        Target target = findFloorTarget(player, clicked);
        if (target == null) return Plan.none(false);
        FishTankBlockEntity tank = target.tank();
        CosmeticGridCell cell = target.cell();
        if (tank.getStructureAnchor(cell) != null) return Plan.refused(target, false);

        PlacedCosmetic existing = tank.getCosmetics().get(cell);
        PlacedCosmetic placed = null;
        if (existing == null) {
            BlockState state = block.defaultBlockState();
            // Any cosmetic with a horizontal-facing property (e.g. the treasure chest) orients
            // toward the placing player, mirroring firstItemRotation for fish.
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                float rotation = FishTankBlock.calculateRotationTowardPlayer(player, tank.getBlockPos());
                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.fromYRot(rotation));
            }
            placed = new PlacedCosmetic(state);
        } else if (existing.block() instanceof SeaPickleBlock && block instanceof SeaPickleBlock) {
            int current = existing.blockState().getValue(BlockStateProperties.PICKLES);
            if (current < SeaPickleBlock.MAX_PICKLES) {
                placed = new PlacedCosmetic(existing.blockState().setValue(BlockStateProperties.PICKLES, current + 1));
            }
        } else if (existing.block() == Blocks.KELP && block == Blocks.KELP) {
            Level level = tank.getLevel();
            if (level != null && existing.height() < TankColumns.maxSegments(TankColumns.storeys(level, tank.getBlockPos()))) {
                placed = new PlacedCosmetic(existing.blockState(), existing.height() + 1);
            }
        }
        if (placed == null) return Plan.refused(target, false);
        PlacedCosmetic result = placed;
        return Plan.of(target, false, () -> tank.setCosmetic(cell, result));
    }

    /**
     * A hanging cosmetic: an empty lid cell takes it, or the strand already hanging there grows
     * (see {@link HangingCosmetics#combine}) while its column has room below the lid.
     */
    private static Plan planHanging(Player player, FishTankBlockEntity clicked, Block block) {
        Target target = findCeilingTarget(player, clicked);
        if (target == null) return Plan.none(true);
        FishTankBlockEntity tank = target.tank();
        CosmeticGridCell cell = target.cell();
        Level level = tank.getLevel();
        if (level == null) return Plan.refused(target, true);

        PlacedCosmetic existing = tank.getCeilingCosmetics().get(cell);
        PlacedCosmetic placed = existing == null
                ? new PlacedCosmetic(block.defaultBlockState())
                : HangingCosmetics.combine(existing, block);
        int room = TankColumns.maxSegments(TankColumns.storeys(level, TankColumns.floorOf(level, tank.getBlockPos())));
        if (placed == null || placed.height() > room) return Plan.refused(target, true);
        return Plan.of(target, true, () -> tank.setCeilingCosmetic(cell, placed));
    }

    /**
     * A multi-cell structure: its footprint is rotated by the placing player's 4-way facing before
     * any validation runs, so the shape checked (and previewed) is always the shape that gets
     * rendered; every cell must be in bounds and free of both single-cell and structure cosmetics.
     */
    private static Plan planStructure(Player player, FishTankBlockEntity clicked, ResourceKey<CosmeticStructure> structureId) {
        Level level = clicked.getLevel();
        if (level == null) return Plan.none(false);
        Optional<CosmeticStructure> structure = level.registryAccess()
                .registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY)
                .getOptional(structureId);
        if (structure.isEmpty()) {
            return new Plan(List.of(), false, null, null, "That cosmetic is no longer available");
        }
        if (structure.get().span().isPresent()) return planSpan(player, clicked, level, structureId, structure.get());

        Target target = findFloorTarget(player, clicked);
        if (target == null) return Plan.none(false);
        FishTankBlockEntity tank = target.tank();
        CosmeticGridCell anchor = target.cell();
        Rotation rotation = rotationFromPlayerFacing(player);

        List<CosmeticGridCell> footprint = new ArrayList<>(structure.get().footprintCells().size());
        List<TankCell> shown = new ArrayList<>();
        String failMessage = null;
        for (CosmeticStructure.GridOffset offset : structure.get().footprintCells()) {
            CosmeticStructure.GridOffset rotated = CosmeticStructures.rotateFootprintCell(rotation, offset);
            int gx = anchor.gridX() + rotated.dx();
            int gz = anchor.gridZ() + rotated.dz();
            if (!CosmeticGridCell.isValid(gx, gz)) {
                failMessage = "Not enough room to place that here";
                continue;
            }
            CosmeticGridCell cell = new CosmeticGridCell(gx, gz);
            if (failMessage == null && (tank.getCosmetics().containsKey(cell) || tank.getStructureAnchor(cell) != null)) {
                failMessage = "That space is already occupied";
            }
            footprint.add(cell);
            shown.add(new TankCell(tank, cell));
        }
        if (failMessage != null) return new Plan(shown, false, null, null, failMessage);
        return new Plan(shown, false, null, () -> tank.setStructureCosmetic(anchor,
                new FishTankBlockEntity.PlacedStructureCosmetic(structureId, rotation), footprint), null);
    }

    /**
     * A spanning structure ({@link SpanStructures}): the aimed floor tank picks the box — the
     * nearest one of the right size that contains it and fits — and the plan shows that box plus
     * every floor cell the structure would stand on.
     */
    private static Plan planSpan(Player player, FishTankBlockEntity clicked, Level level,
                                 ResourceKey<CosmeticStructure> structureId, CosmeticStructure structure) {
        Target target = findFloorTarget(player, clicked);
        if (target == null) return Plan.none(false);
        Rotation rotation = rotationFromPlayerFacing(player);
        Set<BlockPos> group = new HashSet<>();
        for (FishTankBlockEntity tank : groupOf(clicked)) group.add(tank.getBlockPos());

        BlockPos tp = target.tank().getBlockPos();
        SpanStructures.Fit fit = SpanStructures.fit(level, group, target.tank(),
                tp.getX() + target.cell().localX(), tp.getZ() + target.cell().localZ(), structure, rotation);

        List<TankCell> shown = new ArrayList<>();
        for (Map.Entry<BlockPos, List<CosmeticGridCell>> entry : SpanStructures.footprint(structure, rotation).entrySet()) {
            if (level.getBlockEntity(fit.min().offset(entry.getKey())) instanceof FishTankBlockEntity tank) {
                for (CosmeticGridCell cell : entry.getValue()) shown.add(new TankCell(tank, cell));
            }
        }
        if (fit.problem() != null) return new Plan(shown, false, fit.bounds(), null, fit.problem());
        FishTankBlockEntity.PlacedStructureCosmetic placed = new FishTankBlockEntity.PlacedStructureCosmetic(structureId, rotation);
        return new Plan(shown, false, fit.bounds(), () -> SpanStructures.place(level, fit, placed, structure), null);
    }

    /** Maps the placing player's 4-way horizontal facing to the {@link Rotation} that turns a
     * structure authored facing south to face the player — its front toward whoever placed it,
     * the way single cosmetics with a facing (the chest) already turn toward the player. */
    private static Rotation rotationFromPlayerFacing(Player player) {
        Direction facing = player.getDirection().getOpposite();
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(Direction.SOUTH) == facing) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    // ── Targeting ───────────────────────────────────────────────────────────────

    /** A tank in the clicked group and the grid cell targeted in it. */
    private record Target(FishTankBlockEntity tank, CosmeticGridCell cell) {}

    /** Fixed hit-box height for structure footprint cells — independent of the actual part model,
     * since a footprint cell may host parts of any height; generous enough to be easy to target. */
    private static final float STRUCTURE_HIT_HEIGHT = 0.5f;

    /** Accumulates the nearest hit along one look ray. */
    private static final class RayHits {
        final Vec3 eye;
        final Vec3 look;
        final Vec3 end;
        Target best;
        double bestDist = Double.MAX_VALUE;

        RayHits(Player player) {
            eye = player.getEyePosition();
            look = player.getLookAngle();
            end = eye.add(look.scale(interactionRange(player)));
        }

        void box(FishTankBlockEntity tank, CosmeticGridCell cell, AABB box) {
            box.clip(eye, end).ifPresent(hit -> offer(tank, cell, hit));
        }

        /** The horizontal plane at {@code localY} inside {@code tank}'s block. */
        void plane(FishTankBlockEntity tank, float localY) {
            if (Math.abs(look.y) < 0.001) return;
            BlockPos pos = tank.getBlockPos();
            double t = (pos.getY() + localY - eye.y) / look.y;
            if (t < 0) return;
            Vec3 hit = eye.add(look.scale(t));
            if (hit.distanceToSqr(eye) > end.distanceToSqr(eye)) return;
            CosmeticGridCell cell = cellAt(hit.x - pos.getX(), hit.z - pos.getZ());
            if (cell != null) offer(tank, cell, hit);
        }

        private void offer(FishTankBlockEntity tank, CosmeticGridCell cell, Vec3 hit) {
            double dist = hit.distanceToSqr(eye);
            if (dist < bestDist) {
                bestDist = dist;
                best = new Target(tank, cell);
            }
        }
    }

    private static List<FishTankBlockEntity> groupOf(FishTankBlockEntity clicked) {
        Level level = clicked.getLevel();
        List<FishTankBlockEntity> tanks = new ArrayList<>();
        if (level == null) return tanks;
        for (BlockPos pos : TankGroups.of(clicked, level, TankGroups.GAMEPLAY_MAX_GROUP_SIZE).members()) {
            if (level.getBlockEntity(pos) instanceof FishTankBlockEntity tank) tanks.add(tank);
        }
        return tanks;
    }

    /**
     * The floor cell the player is aiming at anywhere in the clicked tank's group: an existing
     * cosmetic or structure (a kelp strand's box spans its full height, up through any storeys),
     * else the sand of a tank that has some — a tank whose floor is open onto the one below has
     * none, so the ray carries on down to the real sand.
     */
    @Nullable
    private static Target findFloorTarget(Player player, FishTankBlockEntity clicked) {
        RayHits hits = new RayHits(player);
        for (FishTankBlockEntity tank : groupOf(clicked)) {
            BlockPos pos = tank.getBlockPos();
            for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : tank.getCosmetics().entrySet()) {
                CosmeticGridCell cell = entry.getKey();
                CosmeticTransforms.Transform t = CosmeticTransforms.get(entry.getValue().block());
                double wx = pos.getX() + cell.localX() + t.offsetX();
                double wy = pos.getY() + CosmeticGridCell.FLOOR_Y + t.offsetY();
                double wz = pos.getZ() + cell.localZ() + t.offsetZ();
                float half = t.scale() / 2f;
                float tall = t.scale() * Math.max(1, entry.getValue().height());
                hits.box(tank, cell, new AABB(wx - half, wy, wz - half, wx + half, wy + tall, wz + half));
            }
            // Structures: the union of every occupied footprint cell (not per-part), so aiming
            // anywhere within a placed structure's footprint hits it.
            for (CosmeticGridCell cell : tank.getStructureCellIndex().keySet()) {
                double wx = pos.getX() + cell.localX();
                double wy = pos.getY() + CosmeticGridCell.FLOOR_Y;
                double wz = pos.getZ() + cell.localZ();
                double half = CosmeticGridCell.CELL_WIDTH / 2.0;
                hits.box(tank, cell, new AABB(wx - half, wy, wz - half, wx + half, wy + STRUCTURE_HIT_HEIGHT, wz + half));
            }
            if (!tank.isFaceOpen(Direction.DOWN)) hits.plane(tank, CosmeticGridCell.FLOOR_Y);
        }
        return hits.best;
    }

    /**
     * The ceiling cell the player is aiming at anywhere in the clicked tank's group: an existing
     * hanging strand (its box spans its full length), else the lid of a tank that has one.
     */
    @Nullable
    private static Target findCeilingTarget(Player player, FishTankBlockEntity clicked) {
        RayHits hits = new RayHits(player);
        for (FishTankBlockEntity tank : groupOf(clicked)) {
            BlockPos pos = tank.getBlockPos();
            for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : tank.getCeilingCosmetics().entrySet()) {
                CosmeticGridCell cell = entry.getKey();
                CosmeticTransforms.Transform t = CosmeticTransforms.get(entry.getValue().block());
                double length = entry.getValue().height() * t.scale();
                double wx = pos.getX() + cell.localX() + t.offsetX();
                double wz = pos.getZ() + cell.localZ() + t.offsetZ();
                double top = pos.getY() + CosmeticGridCell.CEILING_Y;
                double half = t.scale() / 2.0;
                hits.box(tank, cell, new AABB(wx - half, top - length, wz - half, wx + half, top, wz + half));
            }
            if (!tank.isFaceOpen(Direction.UP)) hits.plane(tank, CosmeticGridCell.CEILING_Y);
        }
        return hits.best;
    }

    @Nullable
    private static CosmeticGridCell cellAt(double localX, double localZ) {
        if (localX < 0 || localX >= 1 || localZ < 0 || localZ >= 1) return null;
        int gx = Math.min(CosmeticGridCell.GRID_SIZE - 1, (int) (localX * CosmeticGridCell.GRID_SIZE));
        int gz = Math.min(CosmeticGridCell.GRID_SIZE - 1, (int) (localZ * CosmeticGridCell.GRID_SIZE));
        return new CosmeticGridCell(gx, gz);
    }
}

package grill24.fishtastic.fishtank;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockTags;
import grill24.fishtastic.block.FishTankBlock;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.network.RemoveTankEntryPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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
import java.util.LinkedHashSet;
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
 *
 * <p>A <i>forced</i> plan ({@link #tryForcePlace}, the shift-click placement) is the same rule set
 * with the occupancy refusals turned into removals: any decoration the placement overlaps —
 * single-cell cosmetics and whole structures alike — is cleared away and handed back to the
 * player. Geometry is never forced: a footprint that doesn't fit the grid or a span whose box of
 * tanks doesn't exist still refuses.
 */
public final class CosmeticPlacement {

    private CosmeticPlacement() {}

    /** One targeted grid cell, in the tank it belongs to. */
    public record TankCell(FishTankBlockEntity tank, CosmeticGridCell cell) {}

    /**
     * The outcome of aiming a held cosmetic at a tank group: the cells it targets, possibly across
     * several tanks (empty when the ray found nothing), whether they're on the lid, the box of tanks
     * a spanning structure would fill ({@code region}, else null), and — when the placement is
     * allowed — the mutation that performs it. {@code failMessage} is shown to the player when a
     * click is refused for a reason worth explaining.
     *
     * <p>{@code cleared} is the forced plan's debris list: every cell that will lose its occupant
     * — floor cosmetics and every cell of any structure removed whole, which can reach well past
     * the new footprint. Empty for ordinary plans; the preview draws these cells amber so the
     * player can see what a shift-click will remove before making it.
     */
    public record Plan(List<TankCell> cells, boolean ceiling, @Nullable AABB region,
                       @Nullable Runnable apply, @Nullable String failMessage, List<TankCell> cleared) {
        public boolean valid() {
            return apply != null;
        }

        static Plan none(boolean ceiling) {
            return new Plan(List.of(), ceiling, null, null, null, List.of());
        }

        static Plan refused(Target target, boolean ceiling) {
            return new Plan(List.of(new TankCell(target.tank(), target.cell())), ceiling, null, null, null, List.of());
        }

        static Plan of(Target target, boolean ceiling, Runnable apply) {
            return new Plan(List.of(new TankCell(target.tank(), target.cell())), ceiling, null, apply, null, List.of());
        }
    }

    /** A structure cosmetic a forced placement removes: the tank holding its anchor, and that anchor cell. */
    private record StructureConflict(BlockPos ownerPos, CosmeticGridCell anchor) {}

    /** The cell a spanning structure always anchors at in its own tank (see {@link SpanStructures}). */
    private static final CosmeticGridCell SPAN_ANCHOR_CELL = new CosmeticGridCell(0, 0);

    /**
     * Appended to the refusals a forced placement could clear, so a player who only ever plain-clicks
     * learns the shift-click override exists. Geometry refusals (no room, no such tank box) don't
     * get it — shift-clicking can't fix those.
     */
    public static final String FORCE_HINT = " — shift-click to clear the way";

    /** True if {@code stack} is anything that places as a tank cosmetic. */
    public static boolean isCosmetic(ItemStack stack) {
        return HangingCosmetics.blockOf(stack) != null || cosmeticBlockOf(stack) != null || structureIdOf(stack) != null;
    }

    /** The plan for placing {@code held} where {@code player} is aiming, or null if it isn't a cosmetic. */
    @Nullable
    public static Plan plan(Player player, FishTankBlockEntity clicked, ItemStack held) {
        return plan(player, clicked, held, false);
    }

    /**
     * {@link #plan(Player, FishTankBlockEntity, ItemStack)} with {@code force} for the shift-click
     * placement: anything the placement overlaps is cleared away (and refunded) instead of
     * refusing the plan. Geometry failures — no floor target, footprint outside the grid, a span
     * box that doesn't fit — are never forced.
     */
    @Nullable
    public static Plan plan(Player player, FishTankBlockEntity clicked, ItemStack held, boolean force) {
        Block hanging = HangingCosmetics.blockOf(held);
        if (hanging != null) return planHanging(player, clicked, hanging, force);
        Block cosmetic = cosmeticBlockOf(held);
        if (cosmetic != null) return planFloor(player, clicked, cosmetic, force);
        ResourceKey<CosmeticStructure> structureId = structureIdOf(held);
        if (structureId != null) return planStructure(player, clicked, structureId, force);
        return null;
    }

    /**
     * Entry point for the shift-click force placement, called from the {@code ItemStack#useOn}
     * mixin. Vanilla skips {@code Block#useItemOn} entirely while sneaking with anything in hand
     * ({@code ServerPlayerGameMode#useItemOn}'s {@code suppressUsingBlock}) and calls
     * {@code ItemStack#useOn} with the player's real {@code BlockHitResult} instead, so the block
     * never sees the click. Hooking there covers every cosmetic item — including the plain vanilla
     * {@link BlockItem}s tagged {@code #fishtastic:tank_cosmetics}, which can't override anything
     * themselves.
     *
     * @return {@code null} when this isn't a force placement — not sneaking, not the main hand, or
     *         not aiming at a tank with a cosmetic — so the mixin can fall through to vanilla.
     */
    @Nullable
    public static InteractionResult tryForcePlace(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive() || context.getHand() != InteractionHand.MAIN_HAND) {
            return null;
        }
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FishTankBlockEntity clicked)) {
            return null;
        }
        ItemStack held = context.getItemInHand();
        Plan plan = plan(player, clicked, held, true);
        if (plan == null) {
            return null;
        }
        // The client always lets the server decide, exactly like FishTankBlock#useItemOn's client
        // branch: predicting a refusal from possibly-stale tank state would swallow the swing.
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!plan.valid()) {
            if (plan.failMessage() != null) {
                player.sendSystemMessage(Component.literal(plan.failMessage()));
            }
            return InteractionResult.FAIL;
        }
        plan.apply().run();
        // Vanilla's suppressed-item path wraps useOn in a creative count save/restore, so this
        // shrinks the held stack in survival and leaves a creative player's stack alone.
        held.shrink(1);
        return InteractionResult.SUCCESS;
    }

    // ── Forced-placement plumbing ───────────────────────────────────────────────

    /**
     * The whole structure covering {@code cell} — anchored in this very tank, or, for a spanning
     * structure, anchored in another tank this one links to — or null when the cell is free. A
     * link is only trusted once {@code resolve} checks it out, so stale links (an anchor broken
     * and carried off) are never treated as conflicts.
     */
    @Nullable
    private static StructureConflict structureConflictAt(Level level, FishTankBlockEntity tank, CosmeticGridCell cell) {
        CosmeticGridCell anchor = tank.getStructureAnchor(cell);
        if (anchor == null) {
            return null;
        }
        if (tank.getStructureCosmetics().containsKey(anchor)) {
            return new StructureConflict(tank.getBlockPos(), anchor);
        }
        SpanStructures.Ref ref = SpanStructures.resolve(level, tank);
        return ref == null ? null : new StructureConflict(ref.anchor().getBlockPos(), SPAN_ANCHOR_CELL);
    }

    /** Every cell a conflicting structure occupies, so a forced preview can show the whole thing going. */
    private static List<TankCell> structureCells(Level level, StructureConflict conflict) {
        if (!(level.getBlockEntity(conflict.ownerPos()) instanceof FishTankBlockEntity owner)) {
            return List.of();
        }
        FishTankBlockEntity.PlacedStructureCosmetic placed = owner.getStructureCosmetics().get(conflict.anchor());
        if (placed == null) {
            return List.of();
        }
        Optional<CosmeticStructure> structure = lookupStructure(level, placed.structureId());
        if (structure.isPresent() && structure.get().span().isPresent()) {
            return spanCells(level, owner.getBlockPos(), structure.get(), placed.rotation());
        }
        List<TankCell> cells = new ArrayList<>();
        for (Map.Entry<CosmeticGridCell, CosmeticGridCell> entry : owner.getStructureCellIndex().entrySet()) {
            if (entry.getValue().equals(conflict.anchor())) {
                cells.add(new TankCell(owner, entry.getKey()));
            }
        }
        return cells;
    }

    /** The floor cells a span covers, per tank of its box, anchored at {@code min}. */
    private static List<TankCell> spanCells(Level level, BlockPos min, CosmeticStructure structure, Rotation rotation) {
        List<TankCell> cells = new ArrayList<>();
        for (Map.Entry<BlockPos, List<CosmeticGridCell>> entry : SpanStructures.footprint(structure, rotation).entrySet()) {
            if (level.getBlockEntity(min.offset(entry.getKey())) instanceof FishTankBlockEntity tank) {
                for (CosmeticGridCell cell : entry.getValue()) {
                    cells.add(new TankCell(tank, cell));
                }
            }
        }
        return cells;
    }

    /** Distinct conflicts, in encounter order — the same structure can be hit from several cells. */
    private static List<StructureConflict> distinct(List<StructureConflict> conflicts) {
        return List.copyOf(new LinkedHashSet<>(conflicts));
    }

    /** Empties a floor cell of its single-cell cosmetic, refunding every unit (all kelp segments, every pickle). */
    private static List<ItemStack> clearFloorCell(FishTankBlockEntity tank, CosmeticGridCell cell) {
        List<ItemStack> refunds = new ArrayList<>();
        ItemStack refund;
        while (!(refund = tank.removeCosmeticEntry(cell)).isEmpty()) {
            refunds.add(refund);
        }
        return refunds;
    }

    /**
     * The mutation a forced placement performs: remove the conflicting structures, empty the
     * conflicting floor cells, place, then hand every cleared decoration back to the player and
     * report the count. Server-only — it's the {@link Plan#apply()} the click runs.
     */
    private static Runnable forcedApply(Level level, Player player, List<StructureConflict> conflicts,
                                        List<TankCell> floorCells, Runnable place) {
        return () -> {
            List<ItemStack> refunds = new ArrayList<>();
            int decorations = 0;
            for (StructureConflict conflict : conflicts) {
                if (level.getBlockEntity(conflict.ownerPos()) instanceof FishTankBlockEntity owner
                        && owner.getStructureCosmetics().containsKey(conflict.anchor())) {
                    refunds.add(owner.removeStructureCosmeticEntry(conflict.anchor()));
                    decorations++;
                }
            }
            for (TankCell cell : floorCells) {
                List<ItemStack> cleared = clearFloorCell(cell.tank(), cell.cell());
                if (!cleared.isEmpty()) {
                    decorations++;
                }
                refunds.addAll(cleared);
            }
            place.run();
            if (player instanceof ServerPlayer serverPlayer) {
                for (ItemStack refund : refunds) {
                    RemoveTankEntryPacket.giveOrDrop(serverPlayer, refund);
                }
            }
            reportRemovals(player, decorations);
        };
    }

    /** Tells the player what a forced placement cleared away, once it has cleared anything. */
    private static void reportRemovals(Player player, int decorations) {
        if (decorations > 0) {
            player.sendSystemMessage(Component.literal(
                    "Removed " + decorations + (decorations == 1 ? " decoration" : " decorations") + " to make room"));
        }
    }

    private static Optional<CosmeticStructure> lookupStructure(Level level, ResourceKey<CosmeticStructure> id) {
        return level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY).getOptional(id);
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
     *
     * <p>Forced, an occupied cell — a covering structure, or a cosmetic that can't combine (a
     * different block, a full pickle cluster, a kelp column that has hit the ceiling) — is cleared
     * away and the new cosmetic put down fresh.
     */
    private static Plan planFloor(Player player, FishTankBlockEntity clicked, Block block, boolean force) {
        Target target = findFloorTarget(player, clicked);
        if (target == null) return Plan.none(false);
        FishTankBlockEntity tank = target.tank();
        CosmeticGridCell cell = target.cell();
        Level level = tank.getLevel();

        BlockState fresh = block.defaultBlockState();
        // Any cosmetic with a horizontal-facing property (e.g. the treasure chest) orients
        // toward the placing player, mirroring firstItemRotation for fish.
        if (fresh.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            float rotation = FishTankBlock.calculateRotationTowardPlayer(player, tank.getBlockPos());
            fresh = fresh.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.fromYRot(rotation));
        }

        PlacedCosmetic existing = tank.getCosmetics().get(cell);
        StructureConflict conflict = level != null ? structureConflictAt(level, tank, cell) : null;
        boolean covering = tank.getStructureAnchor(cell) != null;
        PlacedCosmetic placed = null;
        if (!covering) {
            if (existing == null) {
                placed = new PlacedCosmetic(fresh);
            } else if (existing.block() instanceof SeaPickleBlock && block instanceof SeaPickleBlock) {
                int current = existing.blockState().getValue(BlockStateProperties.PICKLES);
                if (current < SeaPickleBlock.MAX_PICKLES) {
                    placed = new PlacedCosmetic(existing.blockState().setValue(BlockStateProperties.PICKLES, current + 1));
                }
            } else if (existing.block() == Blocks.KELP && block == Blocks.KELP) {
                if (level != null && existing.height() < TankColumns.maxSegments(TankColumns.storeys(level, tank.getBlockPos()))) {
                    placed = new PlacedCosmetic(existing.blockState(), existing.height() + 1);
                }
            }
        }
        if (placed != null) {
            PlacedCosmetic result = placed;
            return Plan.of(target, false, () -> tank.setCosmetic(cell, result));
        }
        if (!force || level == null) return Plan.refused(target, false);

        List<StructureConflict> conflicts = conflict != null ? List.of(conflict) : List.of();
        List<TankCell> floorCells = existing != null ? List.of(new TankCell(tank, cell)) : List.of();
        List<TankCell> cleared = new ArrayList<>();
        for (StructureConflict c : conflicts) cleared.addAll(structureCells(level, c));
        cleared.addAll(floorCells);
        PlacedCosmetic result = new PlacedCosmetic(fresh);
        Runnable apply = forcedApply(level, player, conflicts, floorCells, () -> tank.setCosmetic(cell, result));
        return new Plan(List.of(new TankCell(tank, cell)), false, null, apply, null, List.copyOf(cleared));
    }

    /**
     * A hanging cosmetic: an empty lid cell takes it, or the strand already hanging there grows
     * (see {@link HangingCosmetics#combine}) while its column has room below the lid. Forced, a
     * strand that won't combine — a different block, or one already at the column's full length —
     * is cut down and the new item hung from a clean lid instead.
     */
    private static Plan planHanging(Player player, FishTankBlockEntity clicked, Block block, boolean force) {
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
        if (placed != null && placed.height() <= room) {
            PlacedCosmetic result = placed;
            return Plan.of(target, true, () -> tank.setCeilingCosmetic(cell, result));
        }
        if (!force) return Plan.refused(target, true);

        PlacedCosmetic result = new PlacedCosmetic(block.defaultBlockState());
        TankCell cleared = new TankCell(tank, cell);
        Runnable apply = () -> {
            List<ItemStack> refunds = new ArrayList<>();
            ItemStack refund;
            while (!(refund = tank.removeCeilingCosmeticEntry(cell)).isEmpty()) refunds.add(refund);
            tank.setCeilingCosmetic(cell, result);
            if (player instanceof ServerPlayer serverPlayer) {
                for (ItemStack stack : refunds) RemoveTankEntryPacket.giveOrDrop(serverPlayer, stack);
            }
            if (!refunds.isEmpty()) reportRemovals(player, 1);
        };
        return new Plan(List.of(cleared), true, null, apply, null, List.of(cleared));
    }

    /**
     * A multi-cell structure: its footprint is rotated by the placing player's 4-way facing before
     * any validation runs, so the shape checked (and previewed) is always the shape that gets
     * rendered; every footprint cell must be in bounds, and every occupied cell
     * ({@link CosmeticStructure#occupied}) free of both single-cell and structure cosmetics —
     * unless forced, which clears what the occupied cells overlap and stands the structure anyway.
     * The rest of the footprint may overlap neighbours: parts reaching there are slight enough.
     */
    private static Plan planStructure(Player player, FishTankBlockEntity clicked, ResourceKey<CosmeticStructure> structureId, boolean force) {
        Level level = clicked.getLevel();
        if (level == null) return Plan.none(false);
        Optional<CosmeticStructure> structure = lookupStructure(level, structureId);
        if (structure.isEmpty()) {
            return new Plan(List.of(), false, null, null, "That cosmetic is no longer available", List.of());
        }
        if (structure.get().span().isPresent()) return planSpan(player, clicked, level, structureId, structure.get(), force);

        Target target = findFloorTarget(player, clicked);
        if (target == null) return Plan.none(false);
        FishTankBlockEntity tank = target.tank();
        CosmeticGridCell anchor = target.cell();
        Rotation rotation = rotationFromPlayerFacing(player);

        List<CosmeticGridCell> footprint = new ArrayList<>(structure.get().occupied().size());
        List<TankCell> shown = new ArrayList<>();
        List<StructureConflict> conflicts = new ArrayList<>();
        List<TankCell> floorCells = new ArrayList<>();
        String failMessage = null;
        for (CosmeticStructure.GridOffset offset : structure.get().footprintCells()) {
            CosmeticStructure.GridOffset rotated = CosmeticStructures.rotateFootprintCell(rotation, offset);
            if (!CosmeticGridCell.isValid(anchor.gridX() + rotated.dx(), anchor.gridZ() + rotated.dz())) {
                failMessage = "Not enough room to place that here";
            }
        }
        // Structures are stored by anchor cell, so a second one anchored there would replace the
        // first, even where the first doesn't occupy its own anchor (a span's anchor tank, a
        // bypass_anchor_cell_requirement structure).
        if (failMessage == null && tank.getStructureCosmetics().containsKey(anchor)) {
            if (!force) failMessage = "That space is already occupied" + FORCE_HINT;
            else conflicts.add(new StructureConflict(tank.getBlockPos(), anchor));
        }
        for (CosmeticStructure.GridOffset offset : structure.get().occupied()) {
            CosmeticStructure.GridOffset rotated = CosmeticStructures.rotateFootprintCell(rotation, offset);
            int gx = anchor.gridX() + rotated.dx();
            int gz = anchor.gridZ() + rotated.dz();
            if (!CosmeticGridCell.isValid(gx, gz)) continue;
            CosmeticGridCell cell = new CosmeticGridCell(gx, gz);
            if (failMessage == null) {
                boolean floorOccupied = tank.getCosmetics().containsKey(cell);
                StructureConflict conflict = structureConflictAt(level, tank, cell);
                if (floorOccupied || conflict != null) {
                    if (!force) {
                        failMessage = "That space is already occupied" + FORCE_HINT;
                    } else {
                        if (floorOccupied) floorCells.add(new TankCell(tank, cell));
                        if (conflict != null) conflicts.add(conflict);
                    }
                }
            }
            footprint.add(cell);
            shown.add(new TankCell(tank, cell));
        }
        if (failMessage != null) return new Plan(shown, false, null, null, failMessage, List.of());
        FishTankBlockEntity.PlacedStructureCosmetic placed = new FishTankBlockEntity.PlacedStructureCosmetic(structureId, rotation);
        List<StructureConflict> unique = distinct(conflicts);
        if (unique.isEmpty() && floorCells.isEmpty()) {
            return new Plan(shown, false, null,
                    () -> tank.setStructureCosmetic(anchor, placed, footprint), null, List.of());
        }
        List<TankCell> cleared = new ArrayList<>();
        for (StructureConflict conflict : unique) cleared.addAll(structureCells(level, conflict));
        cleared.addAll(floorCells);
        Runnable apply = forcedApply(level, player, unique, floorCells,
                () -> tank.setStructureCosmetic(anchor, placed, footprint));
        return new Plan(shown, false, null, apply, null, List.copyOf(cleared));
    }

    /**
     * A spanning structure ({@link SpanStructures}): the aimed floor tank picks the box — the
     * nearest one of the right size that contains it and fits — and the plan shows that box plus
     * every floor cell the structure would stand on. Forced, boxes holding other structures or
     * decorated floors count as fitting, and their contents are cleared along with the box's
     * footprint cells.
     */
    private static Plan planSpan(Player player, FishTankBlockEntity clicked, Level level,
                                 ResourceKey<CosmeticStructure> structureId, CosmeticStructure structure, boolean force) {
        Target target = findFloorTarget(player, clicked);
        if (target == null) return Plan.none(false);
        Rotation rotation = rotationFromPlayerFacing(player);
        Set<BlockPos> group = new HashSet<>();
        for (FishTankBlockEntity tank : groupOf(clicked)) group.add(tank.getBlockPos());

        BlockPos tp = target.tank().getBlockPos();
        SpanStructures.Fit fit = SpanStructures.fit(level, group, target.tank(),
                tp.getX() + target.cell().localX(), tp.getZ() + target.cell().localZ(), structure, rotation, force);

        List<TankCell> shown = spanCells(level, fit.min(), structure, rotation);
        if (fit.problem() != null) return new Plan(shown, false, fit.bounds(), null, fit.problem(), List.of());

        List<StructureConflict> conflicts = new ArrayList<>();
        List<TankCell> floorCells = new ArrayList<>();
        if (force) {
            Map<BlockPos, List<CosmeticGridCell>> footprint = SpanStructures.footprint(structure, rotation);
            CosmeticStructure.Span box = fit.box();
            for (int x = 0; x < box.x(); x++) {
                for (int y = 0; y < box.y(); y++) {
                    for (int z = 0; z < box.z(); z++) {
                        BlockPos offset = new BlockPos(x, y, z);
                        if (!(level.getBlockEntity(fit.min().offset(offset)) instanceof FishTankBlockEntity tank)) continue;
                        for (CosmeticGridCell anchor : tank.getStructureCosmetics().keySet()) {
                            conflicts.add(new StructureConflict(tank.getBlockPos(), anchor));
                        }
                        SpanStructures.Ref ref = SpanStructures.resolve(level, tank);
                        if (ref != null) conflicts.add(new StructureConflict(ref.anchor().getBlockPos(), SPAN_ANCHOR_CELL));
                        if (y == 0) {
                            for (CosmeticGridCell cell : footprint.getOrDefault(offset, List.of())) {
                                if (tank.getCosmetics().containsKey(cell)) floorCells.add(new TankCell(tank, cell));
                            }
                        }
                    }
                }
            }
        }
        FishTankBlockEntity.PlacedStructureCosmetic placed = new FishTankBlockEntity.PlacedStructureCosmetic(structureId, rotation);
        List<StructureConflict> unique = distinct(conflicts);
        if (unique.isEmpty() && floorCells.isEmpty()) {
            return new Plan(shown, false, fit.bounds(),
                    () -> SpanStructures.place(level, fit, placed, structure), null, List.of());
        }
        List<TankCell> cleared = new ArrayList<>();
        for (StructureConflict conflict : unique) cleared.addAll(structureCells(level, conflict));
        cleared.addAll(floorCells);
        Runnable apply = forcedApply(level, player, unique, floorCells,
                () -> SpanStructures.place(level, fit, placed, structure));
        return new Plan(shown, false, fit.bounds(), apply, null, List.copyOf(cleared));
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
            end = eye.add(look.scale(player.blockInteractionRange()));
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

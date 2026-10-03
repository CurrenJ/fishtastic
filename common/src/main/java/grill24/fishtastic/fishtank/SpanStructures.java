package grill24.fishtastic.fishtank;

import com.mojang.serialization.DataResult;
import grill24.FishtasticRegistries;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structures that span a whole box of connected tanks — a whale fall lying the length of a long
 * aquarium, a pagoda rising through three storeys. See {@link CosmeticStructure}'s {@code span}.
 *
 * <h2>Geometry</h2>
 * A spanning structure is authored facing south in its own build grid: part {@code (px, py, pz)}
 * is a block of size {@code scale} whose min corner sits {@code px·scale} from the box's interior
 * west wall, {@code py·scale} above the sand and {@code pz·scale} from the interior north wall.
 * Placed with a rotation, the whole layout turns about the box's centre and the box's footprint
 * swaps its x and z when turned a quarter. Positions are continuous across the box — tanks joined
 * by open faces have no glass between them, so a rib can cross a seam.
 *
 * <h2>Storage</h2>
 * The structure is stored once, on the box's <i>anchor</i>: its bottom tank at the minimum x and
 * z, under the usual structure map at cell (0,0). Every other tank in the box holds a
 * {@link FishTankBlockEntity.SpanLink} back to the anchor, carrying the floor cells the structure
 * covers in that tank, so per-tank floor rules (placement, crawling fish) see them without a
 * lookup. A link is only trusted after {@link #resolve} confirms the anchor still holds a span
 * whose box contains the linked tank; stale links (an anchor broken and carried off) are cleared
 * when the tank next updates its connections.
 *
 * <h2>Rendering</h2>
 * Like kelp strands, every tank in the box draws only the parts whose centre lies inside it, so
 * the set piece stays on screen whichever of its tanks is in view.
 */
public final class SpanStructures {

    /** Largest box edge a span may ask for, in tanks. */
    public static final int MAX_SPAN = 8;
    private static final double WALL = 1.0 / 16.0;
    /** Parts this low in the build grid count toward the floor footprint. */
    private static final float FOOTPRINT_MAX_PY = 1.0f;

    private SpanStructures() {}

    // ── Geometry ────────────────────────────────────────────────────────────────

    public static CosmeticStructure.Span rotated(CosmeticStructure.Span span, Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? new CosmeticStructure.Span(span.z(), span.y(), span.x())
                : span;
    }

    /** Interior extents of a box of tanks, in blocks: wall to wall, and sand to lid. */
    private static double interiorX(CosmeticStructure.Span span) { return span.x() - 2 * WALL; }
    private static double interiorY(CosmeticStructure.Span span) { return span.y() - CosmeticGridCell.FLOOR_Y - (1 - CosmeticGridCell.CEILING_Y); }
    private static double interiorZ(CosmeticStructure.Span span) { return span.z() - 2 * WALL; }

    /**
     * A part laid out in a box: its rotated state, the min corner of its scaled block (in blocks
     * from a reference block's min corner), and the faces ({@link Direction#ordinal()} bits) that
     * touch a neighbouring solid part of the same structure — hidden, so a mesh can drop them the
     * way the chunk mesher drops faces between neighbouring world blocks.
     */
    public record Placed(BlockState state, float x, float y, float z, int cullMask) {}

    /** A layout plus each part's authored height in the build grid (for the footprint). */
    private record Layout(List<Placed> parts, float[] buildY) {}

    private static final Map<CosmeticStructure, Map<Rotation, Layout>> LAYOUTS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** Every part, rotated and positioned relative to the box's min block corner. Cached per structure instance. */
    public static List<Placed> layout(CosmeticStructure structure, Rotation rotation) {
        return layoutWithHeights(structure, rotation).parts();
    }

    private static Layout layoutWithHeights(CosmeticStructure structure, Rotation rotation) {
        if (LAYOUTS.size() > 64) LAYOUTS.clear(); // a datapack reload makes new instances; drop the old ones
        return LAYOUTS.computeIfAbsent(structure, k -> Collections.synchronizedMap(new HashMap<>()))
                .computeIfAbsent(rotation, r -> computeLayout(structure, r));
    }

    private static Layout computeLayout(CosmeticStructure structure, Rotation rotation) {
        CosmeticStructure.Span span = structure.span().orElseThrow();
        CosmeticStructure.Span turned = rotated(span, rotation);
        float s = structure.scale();
        double halfBuildX = interiorX(span) / s / 2.0;
        double halfBuildZ = interiorZ(span) / s / 2.0;
        List<Placed> parts = new ArrayList<>(structure.parts().size());
        float[] buildY = new float[structure.parts().size()];
        java.util.Set<BlockPos> solid = new java.util.HashSet<>();
        for (CosmeticStructure.StructurePart part : structure.parts()) {
            if (part.state().isSolidRender()) solid.add(gridPos(part));
        }
        for (int i = 0; i < structure.parts().size(); i++) {
            CosmeticStructure.StructurePart part = structure.parts().get(i);
            float[] r = CosmeticStructures.rotateOffset(rotation,
                    (float) (part.offsetX() + 0.5 - halfBuildX), (float) (part.offsetZ() + 0.5 - halfBuildZ));
            float x = (float) (turned.x() / 2.0 + (r[0] - 0.5) * s);
            float z = (float) (turned.z() / 2.0 + (r[1] - 0.5) * s);
            float y = (float) (CosmeticGridCell.FLOOR_Y + part.offsetY() * s);
            int cullMask = 0;
            BlockPos at = gridPos(part);
            for (Direction authored : Direction.values()) {
                if (solid.contains(at.relative(authored))) cullMask |= 1 << rotation.rotate(authored).ordinal();
            }
            parts.add(new Placed(part.state().rotate(rotation), x, y, z, cullMask));
            buildY[i] = part.offsetY();
        }
        return new Layout(List.copyOf(parts), buildY);
    }

    /**
     * Where build cell {@code (0, 0)}'s centre lands horizontally, in blocks from the box's min
     * corner: the origin a build cell {@code (bx, bz)} is offset from by
     * {@code rotateOffset(rotation, bx, bz) × scale}, the convention a floor structure's anchor
     * cell follows. {@link #computeLayout} places every part this way, because rotating the
     * offset is linear. Lets a span's shelter be placed exactly where its parts are drawn.
     */
    public static float[] buildOrigin(CosmeticStructure structure, Rotation rotation) {
        return buildOrigin(structure.span().orElseThrow(), structure.scale(), rotation);
    }

    /** {@link #buildOrigin(CosmeticStructure, Rotation)} from the span's box and scale alone. */
    public static float[] buildOrigin(CosmeticStructure.Span span, float s, Rotation rotation) {
        CosmeticStructure.Span turned = rotated(span, rotation);
        float[] r = CosmeticStructures.rotateOffset(rotation,
                (float) (0.5 - interiorX(span) / s / 2.0), (float) (0.5 - interiorZ(span) / s / 2.0));
        return new float[]{(float) (turned.x() / 2.0 + r[0] * s), (float) (turned.z() / 2.0 + r[1] * s)};
    }

    private static BlockPos gridPos(CosmeticStructure.StructurePart part) {
        return new BlockPos(Math.round(part.offsetX()), Math.round(part.offsetY()), Math.round(part.offsetZ()));
    }

    /**
     * The floor cells the structure occupies, per tank, keyed by the tank's offset from the anchor
     * (always on the bottom layer): its authored {@code occupied_cells}, turned with the box, or
     * else every cell its lowest parts stand on.
     */
    public static Map<BlockPos, List<CosmeticGridCell>> footprint(CosmeticStructure structure, Rotation rotation) {
        if (!structure.occupiedCells().isEmpty()) return authoredFootprint(structure, rotation);
        Layout layout = layoutWithHeights(structure, rotation);
        float s = structure.scale();
        Map<BlockPos, java.util.LinkedHashSet<CosmeticGridCell>> cells = new LinkedHashMap<>();
        for (int i = 0; i < layout.parts().size(); i++) {
            if (layout.buildY()[i] >= FOOTPRINT_MAX_PY) continue;
            Placed p = layout.parts().get(i);
            double cx = p.x() + s / 2.0, cz = p.z() + s / 2.0;
            int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
            cells.computeIfAbsent(new BlockPos(bx, 0, bz), k -> new java.util.LinkedHashSet<>())
                    .add(cellAt(cx - bx, cz - bz));
        }
        Map<BlockPos, List<CosmeticGridCell>> out = new LinkedHashMap<>();
        cells.forEach((pos, set) -> out.put(pos, List.copyOf(set)));
        return out;
    }

    /**
     * {@code occupied_cells} turned with the box: each authored cell's centre is rotated about the
     * box's centre, as {@link #computeLayout} turns a part's, and lands in the rotated cell.
     */
    private static Map<BlockPos, List<CosmeticGridCell>> authoredFootprint(CosmeticStructure structure, Rotation rotation) {
        CosmeticStructure.Span span = structure.span().orElseThrow();
        CosmeticStructure.Span turned = rotated(span, rotation);
        Map<BlockPos, java.util.LinkedHashSet<CosmeticGridCell>> cells = new LinkedHashMap<>();
        for (CosmeticStructure.GridOffset cell : structure.occupiedCells()) {
            double ax = cellCentre(cell.dx()) - span.x() / 2.0;
            double az = cellCentre(cell.dz()) - span.z() / 2.0;
            float[] r = CosmeticStructures.rotateOffset(rotation, (float) ax, (float) az);
            double cx = turned.x() / 2.0 + r[0], cz = turned.z() / 2.0 + r[1];
            int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
            cells.computeIfAbsent(new BlockPos(bx, 0, bz), k -> new java.util.LinkedHashSet<>())
                    .add(cellAt(cx - bx, cz - bz));
        }
        Map<BlockPos, List<CosmeticGridCell>> out = new LinkedHashMap<>();
        cells.forEach((pos, set) -> out.put(pos, List.copyOf(set)));
        return out;
    }

    /** Centre of the {@code index}th floor cell along a box's edge, in blocks from its min corner. */
    private static double cellCentre(int index) {
        int tank = Math.floorDiv(index, CosmeticGridCell.GRID_SIZE);
        int cell = Math.floorMod(index, CosmeticGridCell.GRID_SIZE);
        return tank + WALL + (cell + 0.5) * CosmeticGridCell.CELL_WIDTH;
    }

    /** The parts whose centre lies in the block at {@code offset} from the box's min corner, positioned relative to that block. */
    public static List<Placed> partsIn(CosmeticStructure structure, Rotation rotation, BlockPos offset) {
        float half = structure.scale() / 2f;
        List<Placed> out = new ArrayList<>();
        for (Placed p : layout(structure, rotation)) {
            float lx = p.x() - offset.getX(), ly = p.y() - offset.getY(), lz = p.z() - offset.getZ();
            float cx = lx + half, cy = ly + half, cz = lz + half;
            if (cx < 0 || cx >= 1 || cy < 0 || cy >= 1 || cz < 0 || cz >= 1) continue;
            out.add(new Placed(p.state(), lx, ly, lz, p.cullMask()));
        }
        return out;
    }

    /** The floor grid cell under a point inside a block, clamped onto the grid. */
    private static CosmeticGridCell cellAt(double localX, double localZ) {
        int gx = (int) Math.floor((localX - WALL) / CosmeticGridCell.CELL_WIDTH);
        int gz = (int) Math.floor((localZ - WALL) / CosmeticGridCell.CELL_WIDTH);
        return new CosmeticGridCell(net.minecraft.util.Mth.clamp(gx, 0, CosmeticGridCell.GRID_SIZE - 1), net.minecraft.util.Mth.clamp(gz, 0, CosmeticGridCell.GRID_SIZE - 1));
    }

    /** Codec validation: every part must sit inside the box's interior, so nothing pokes through the glass. */
    static DataResult<CosmeticStructure> validate(CosmeticStructure structure) {
        CosmeticStructure.Span span = structure.span().orElseThrow();
        float s = structure.scale();
        double maxX = interiorX(span) / s, maxY = interiorY(span) / s, maxZ = interiorZ(span) / s;
        for (CosmeticStructure.GridOffset cell : structure.occupiedCells()) {
            if (cell.dx() < 0 || cell.dx() >= span.x() * CosmeticGridCell.GRID_SIZE
                    || cell.dz() < 0 || cell.dz() >= span.z() * CosmeticGridCell.GRID_SIZE) {
                return DataResult.error(() -> "occupied cell " + cell + " is outside the " + span.x() + "x" + span.z() + " box's floor");
            }
        }
        for (CosmeticStructure.StructurePart part : structure.parts()) {
            if (part.offsetX() < 0 || part.offsetX() + 1 > maxX + 1e-3
                    || part.offsetY() < 0 || part.offsetY() + 1 > maxY + 1e-3
                    || part.offsetZ() < 0 || part.offsetZ() + 1 > maxZ + 1e-3) {
                return DataResult.error(() -> String.format(java.util.Locale.ROOT,
                        "span part %s at (%.1f, %.1f, %.1f) is outside the %dx%dx%d box's interior (%.1f x %.1f x %.1f build blocks)",
                        part.state(), part.offsetX(), part.offsetY(), part.offsetZ(),
                        span.x(), span.y(), span.z(), maxX, maxY, maxZ));
            }
        }
        return DataResult.success(structure);
    }

    // ── Placed spans ────────────────────────────────────────────────────────────

    /** A span as seen from one of its tanks: the anchor, what's placed there, and where this tank sits in the box. */
    public record Ref(FishTankBlockEntity anchor, FishTankBlockEntity.PlacedStructureCosmetic placed,
                      CosmeticStructure structure, BlockPos offsetInBox) {
        public CosmeticStructure.Span box() {
            return rotated(structure.span().orElseThrow(), placed.rotation());
        }
    }

    /** The spanning structure anchored in {@code tank} itself, or null. */
    @Nullable
    public static Ref anchoredIn(Level level, FishTankBlockEntity tank) {
        FishTankBlockEntity.PlacedStructureCosmetic placed = tank.getStructureCosmetics().get(new CosmeticGridCell(0, 0));
        if (placed == null) return null;
        CosmeticStructure structure = lookup(level, placed);
        if (structure == null || structure.span().isEmpty()) return null;
        return new Ref(tank, placed, structure, BlockPos.ZERO);
    }

    /**
     * The span {@code tank} belongs to — as its anchor or through a link that still checks out —
     * or null.
     */
    @Nullable
    public static Ref resolve(Level level, FishTankBlockEntity tank) {
        Ref own = anchoredIn(level, tank);
        if (own != null) return own;
        FishTankBlockEntity.SpanLink link = tank.getSpanLink();
        if (link == null) return null;
        BlockPos anchorPos = tank.getBlockPos().offset(link.toAnchor());
        if (!(level.getBlockEntity(anchorPos) instanceof FishTankBlockEntity anchor)) return null;
        Ref ref = anchoredIn(level, anchor);
        if (ref == null) return null;
        BlockPos offset = tank.getBlockPos().subtract(anchorPos);
        CosmeticStructure.Span box = ref.box();
        if (offset.getX() < 0 || offset.getY() < 0 || offset.getZ() < 0
                || offset.getX() >= box.x() || offset.getY() >= box.y() || offset.getZ() >= box.z()) return null;
        return new Ref(anchor, ref.placed(), ref.structure(), offset);
    }

    @Nullable
    private static CosmeticStructure lookup(Level level, FishTankBlockEntity.PlacedStructureCosmetic placed) {
        return level.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY)
                .getOptional(placed.structureId()).orElse(null);
    }

    /** Removes the links the span anchored at {@code anchor} wrote into the rest of its box. */
    public static void clearLinks(Level level, FishTankBlockEntity anchor, FishTankBlockEntity.PlacedStructureCosmetic placed) {
        CosmeticStructure structure = lookup(level, placed);
        if (structure == null || structure.span().isEmpty()) return;
        CosmeticStructure.Span box = rotated(structure.span().get(), placed.rotation());
        BlockPos min = anchor.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(box.x() - 1, box.y() - 1, box.z() - 1))) {
            if (pos.equals(min)) continue;
            if (level.getBlockEntity(pos) instanceof FishTankBlockEntity tank && tank.getSpanLink() != null
                    && pos.offset(tank.getSpanLink().toAnchor()).equals(min)) {
                tank.clearSpanLink();
            }
        }
    }

    // ── Placement ───────────────────────────────────────────────────────────────

    /** Where a span would go: the box's min corner, or why it can't. */
    public record Fit(BlockPos min, CosmeticStructure.Span box, @Nullable String problem) {
        public AABB bounds() {
            return new AABB(min.getX(), min.getY(), min.getZ(),
                    min.getX() + box.x(), min.getY() + box.y(), min.getZ() + box.z());
        }
    }

    /**
     * Chooses the box for a span placed from the floor tank {@code target}: every box of the right
     * size whose bottom layer contains {@code target} is tried, and the one centred nearest the
     * aimed point that fits wins. When none fits, the nearest box comes back with its problem, so
     * the preview can outline it in red and the click can say why.
     */
    public static Fit fit(Level level, Set<BlockPos> group, FishTankBlockEntity target, double aimX, double aimZ,
                          CosmeticStructure structure, Rotation rotation) {
        return fit(level, group, target, aimX, aimZ, structure, rotation, false);
    }

    /**
     * {@link #fit(Level, Set, FishTankBlockEntity, double, double, CosmeticStructure, Rotation)}
     * with {@code ignoreCosmeticConflicts} for the shift-click force placement: boxes holding
     * other structures or decorated footprint cells become candidates too, because the caller is
     * going to clear them (see {@code CosmeticPlacement#planSpan}). The box geometry itself —
     * tanks present, open faces, sand under the bottom layer — is always enforced; a span can
     * never be forced into a hole that isn't there.
     */
    public static Fit fit(Level level, Set<BlockPos> group, FishTankBlockEntity target, double aimX, double aimZ,
                          CosmeticStructure structure, Rotation rotation, boolean ignoreCosmeticConflicts) {
        CosmeticStructure.Span box = rotated(structure.span().orElseThrow(), rotation);
        Map<BlockPos, List<CosmeticGridCell>> footprint = footprint(structure, rotation);
        Fit bestOk = null, bestAny = null;
        double bestOkDist = Double.MAX_VALUE, bestAnyDist = Double.MAX_VALUE;
        for (int ox = 0; ox < box.x(); ox++) {
            for (int oz = 0; oz < box.z(); oz++) {
                BlockPos min = target.getBlockPos().offset(-ox, 0, -oz);
                double dx = min.getX() + box.x() / 2.0 - aimX, dz = min.getZ() + box.z() / 2.0 - aimZ;
                double dist = dx * dx + dz * dz;
                Fit candidate = new Fit(min, box, problemWith(level, group, min, box, footprint, ignoreCosmeticConflicts));
                if (candidate.problem() == null && dist < bestOkDist) { bestOk = candidate; bestOkDist = dist; }
                if (dist < bestAnyDist) { bestAny = candidate; bestAnyDist = dist; }
            }
        }
        return bestOk != null ? bestOk : bestAny;
    }

    @Nullable
    private static String problemWith(Level level, Set<BlockPos> group, BlockPos min, CosmeticStructure.Span box,
                                      Map<BlockPos, List<CosmeticGridCell>> footprint, boolean ignoreCosmeticConflicts) {
        String needs = "This needs a tank " + box.x() + " long, " + box.z() + " deep and " + box.y() + " tall";
        for (int x = 0; x < box.x(); x++) {
            for (int y = 0; y < box.y(); y++) {
                for (int z = 0; z < box.z(); z++) {
                    BlockPos pos = min.offset(x, y, z);
                    if (!group.contains(pos) || !(level.getBlockEntity(pos) instanceof FishTankBlockEntity tank)) return needs;
                    // No glass inside the box: every face toward another box tank must be open.
                    if (x + 1 < box.x() && !tank.isFaceOpen(Direction.EAST)) return needs;
                    if (z + 1 < box.z() && !tank.isFaceOpen(Direction.SOUTH)) return needs;
                    if (y + 1 < box.y() && !tank.isFaceOpen(Direction.UP)) return needs;
                    if (y == 0 && tank.isFaceOpen(Direction.DOWN)) return needs;
                    if (ignoreCosmeticConflicts) continue;
                    if (!tank.getStructureCosmetics().isEmpty() || resolve(level, tank) != null) {
                        return "Another structure is in the way" + CosmeticPlacement.FORCE_HINT;
                    }
                    if (y == 0) {
                        for (CosmeticGridCell cell : footprint.getOrDefault(new BlockPos(x, 0, z), List.of())) {
                            if (tank.getCosmetics().containsKey(cell) || tank.getStructureAnchor(cell) != null) {
                                return "Clear the floor first" + CosmeticPlacement.FORCE_HINT;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Writes the span into its box: the structure on the anchor, a link (with its floor cells) on every other tank. */
    public static void place(Level level, Fit fit, FishTankBlockEntity.PlacedStructureCosmetic placed, CosmeticStructure structure) {
        Map<BlockPos, List<CosmeticGridCell>> footprint = footprint(structure, placed.rotation());
        CosmeticStructure.Span box = fit.box();
        for (int x = 0; x < box.x(); x++) {
            for (int y = 0; y < box.y(); y++) {
                for (int z = 0; z < box.z(); z++) {
                    BlockPos offset = new BlockPos(x, y, z);
                    if (!(level.getBlockEntity(fit.min().offset(offset)) instanceof FishTankBlockEntity tank)) continue;
                    List<CosmeticGridCell> cells = footprint.getOrDefault(offset, List.of());
                    if (offset.equals(BlockPos.ZERO)) {
                        tank.setStructureCosmetic(new CosmeticGridCell(0, 0), placed, cells);
                    } else {
                        tank.setSpanLink(new FishTankBlockEntity.SpanLink(BlockPos.ZERO.subtract(offset), cells));
                    }
                }
            }
        }
    }
}

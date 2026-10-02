package grill24.fishtastic.fishtank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A multi-block cosmetic decoration spanning a reserved footprint in a fish tank's 3×3 floor grid
 * (e.g. a small arch built from fence posts). Placed/rotated/removed as one rigid unit, alongside
 * (not replacing) single-block {@link PlacedCosmetic}s. Loaded as datapack content, synced to clients
 * the same way {@code Quest}/{@code ShopEntry} are.
 * <p>
 * Authored assuming the structure faces south; at placement it is turned so that front faces the
 * placing player (see {@code CosmeticPlacement#rotationFromPlayerFacing}).
 * <p>
 * {@code scale} shrinks every part uniformly. Part offsets are in grid-cell units and get converted
 * to block-local space by multiplying by {@link CosmeticGridCell#CELL_WIDTH} — the same pitch a plain
 * 1-block gap would use if grid units mapped 1:1 to world blocks. Scaling parts by that same
 * {@code CELL_WIDTH} therefore reproduces the structure as a uniformly-shrunk replica of how it'd look
 * built at full size in the world: geometry authored assuming 1-block neighbor spacing (e.g. a fence's
 * connector arm reaching toward an adjacent post) lands exactly right at the grid's smaller spacing too.
 * This intentionally differs from {@link CosmeticTransforms.Transform#DEFAULT}'s scale, which instead
 * shrinks a single free-floating cosmetic to leave breathing room inside its own cell.
 * <p>
 * {@code itemIcon} is a manual nudge (offset/rotation/scale, on top of the automatic bounding-box fit
 * a client-side {@code ItemModel} computes) for how this structure renders as a held item icon —
 * distinct from placement in a tank, which never reads this field. Reuses the same
 * {@link CosmeticTransforms.Transform} shape single-block cosmetics use, but its {@code scale} is a
 * multiplier on the auto-fit (default {@code 1.0}, identity), not an absolute block scale.
 * <p>
 * <b>Spanning structures.</b> With a {@code span} the structure is a set piece for a whole tank
 * arrangement rather than one tank's floor grid: {@code span} names the box of tanks it needs
 * (e.g. 4 long, 2 deep, 2 storeys) and the parts are laid out in that box's own build-block
 * coordinates instead — {@code offsetX/Y/Z} are a part's min corner, counted in blocks of
 * {@code scale} from the box's interior corner on the sand (see {@link SpanStructures}). Such a
 * structure's floor footprint is derived from its lowest parts, so {@code footprint_cells} is
 * ignored. Placement, storage and rendering across the box live in {@link SpanStructures}.
 * <p>
 * <b>Shelters.</b> With a {@code shelter} the structure has a hollow fish can swim into
 * (docs/fish-shelters.md §3): {@code interior} lists the build-grid cells of the hollow (the
 * integer grid the parts were captured on, before {@code scale}, facing south — for a span, the
 * box's own build grid). Mouths, hull and run are derived from those cells and the parts by
 * {@link ShelterGeometry}, and a hollow with no way out fails validation. Rotation follows the
 * parts: the stored shelter is unrotated and turns with the structure at placement.
 */
public record CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float scale,
                                 CosmeticTransforms.Transform itemIcon, boolean bypassAnchorCellRequirement,
                                 Optional<Span> span, Optional<ShelterSpec> shelter) {

    /** The box of tanks a spanning structure fills: {@code x} long, {@code z} deep, {@code y} storeys, as authored (facing south). */
    public record Span(int x, int y, int z) {
        public static final Codec<Span> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, SpanStructures.MAX_SPAN).fieldOf("x").forGetter(Span::x),
                Codec.intRange(1, SpanStructures.MAX_SPAN).fieldOf("y").forGetter(Span::y),
                Codec.intRange(1, SpanStructures.MAX_SPAN).fieldOf("z").forGetter(Span::z)
        ).apply(i, Span::new));
    }

    /**
     * The authored half of a shelter: the hollow's cells and, optionally, how many fish it holds
     * at once (default {@link ShelterGeometry#defaultCapacity}).
     */
    public record ShelterSpec(List<ShelterGeometry.Cell> interior, Optional<Integer> capacity) {
        private static final Codec<ShelterGeometry.Cell> CELL_CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(ShelterGeometry.Cell::x),
                Codec.INT.fieldOf("y").forGetter(ShelterGeometry.Cell::y),
                Codec.INT.fieldOf("z").forGetter(ShelterGeometry.Cell::z)
        ).apply(i, ShelterGeometry.Cell::new));

        public static final Codec<ShelterSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                CELL_CODEC.listOf().fieldOf("interior").forGetter(ShelterSpec::interior),
                Codec.intRange(1, 64).optionalFieldOf("capacity").forGetter(ShelterSpec::capacity)
        ).apply(i, ShelterSpec::new));

        public int capacityOrDefault() {
            return capacity.orElseGet(() -> ShelterGeometry.defaultCapacity(interior.size()));
        }
    }

    /** Identity item-icon transform: auto-fit only, no authored position/scale/rotation nudge. */
    private static final CosmeticTransforms.Transform ITEM_ICON_DEFAULT =
            new CosmeticTransforms.Transform(0f, 0f, 0f, 0f, 0f, 0f, 1f);

    public CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float scale) {
        this(footprintCells, parts, scale, ITEM_ICON_DEFAULT, false, Optional.empty(), Optional.empty());
    }

    public CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float scale,
                             CosmeticTransforms.Transform itemIcon) {
        this(footprintCells, parts, scale, itemIcon, false, Optional.empty(), Optional.empty());
    }

    /** This structure with its hollow marked as a shelter. */
    public CosmeticStructure withShelter(Optional<ShelterSpec> shelter) {
        return new CosmeticStructure(footprintCells, parts, scale, itemIcon, bypassAnchorCellRequirement, span, shelter);
    }

    /**
     * Each part's cell in the build grid — the inverse of how capture laid the parts out. A span's
     * offsets are already build blocks; a floor structure's horizontal offsets were compressed by
     * {@code scale / CELL_WIDTH} at capture (see {@code CosmeticCommand.capture}) and are expanded
     * back, while its {@code offsetY} was never compressed.
     */
    public List<ShelterGeometry.Cell> partCells() {
        float xzRatio = span.isPresent() ? 1f : scale / (float) CosmeticGridCell.CELL_WIDTH;
        List<ShelterGeometry.Cell> cells = new ArrayList<>(parts.size());
        for (StructurePart part : parts) {
            cells.add(new ShelterGeometry.Cell(Math.round(part.offsetX() / xzRatio),
                    Math.round(part.offsetY()), Math.round(part.offsetZ() / xzRatio)));
        }
        return cells;
    }

    /** The derived shape of this structure's shelter, or empty when it has none (or it fails validation). */
    public Optional<ShelterGeometry.Shape> shelterShape() {
        return shelter.map(spec -> ShelterGeometry.derive(spec.interior(), partCells()))
                .filter(ShelterGeometry.Result::ok)
                .map(ShelterGeometry.Result::shape);
    }

    public record GridOffset(int dx, int dz) {
        public static final Codec<GridOffset> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("dx").forGetter(GridOffset::dx),
                Codec.INT.fieldOf("dz").forGetter(GridOffset::dz)
        ).apply(i, GridOffset::new));
    }

    /**
     * One block placed as part of the structure. {@code offsetX}/{@code offsetZ} are in grid-cell
     * units — the same units as {@link GridOffset#dx()}/{@link GridOffset#dz()} — so a part offset of
     * {@code (1, 0)} sits centered on the footprint cell one grid unit over from the anchor.
     * <p>
     * {@code offsetY} has no vertical grid to snap to, so it's expressed in the same "as if built at
     * full size" terms as offsetX/offsetZ: a value of {@code 1.0} means one full block up, and the
     * renderer multiplies it by the structure's {@code scale} before translating — the same uniform
     * shrink applied to X/Z spacing — so vertically stacked parts (e.g. a multi-block fence post) stay
     * flush instead of drifting apart as {@code scale} shrinks the models but not the gap between them.
     */
    public record StructurePart(BlockState state, float offsetX, float offsetY, float offsetZ) {
        public static final Codec<StructurePart> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("state").forGetter(StructurePart::state),
                Codec.FLOAT.optionalFieldOf("offsetX", 0f).forGetter(StructurePart::offsetX),
                Codec.FLOAT.optionalFieldOf("offsetY", 0f).forGetter(StructurePart::offsetY),
                Codec.FLOAT.optionalFieldOf("offsetZ", 0f).forGetter(StructurePart::offsetZ)
        ).apply(i, StructurePart::new));
    }

    private static final Codec<CosmeticStructure> RAW_CODEC = RecordCodecBuilder.create(i -> i.group(
            GridOffset.CODEC.listOf().optionalFieldOf("footprint_cells", List.of()).forGetter(CosmeticStructure::footprintCells),
            StructurePart.CODEC.listOf().fieldOf("parts").forGetter(CosmeticStructure::parts),
            Codec.FLOAT.optionalFieldOf("scale", (float) CosmeticGridCell.CELL_WIDTH).forGetter(CosmeticStructure::scale),
            CosmeticTransforms.Transform.MAP_CODEC.codec().optionalFieldOf("item_icon", ITEM_ICON_DEFAULT).forGetter(CosmeticStructure::itemIcon),
            Codec.BOOL.optionalFieldOf("bypass_anchor_cell_requirement", false).forGetter(CosmeticStructure::bypassAnchorCellRequirement),
            Span.CODEC.optionalFieldOf("span").forGetter(CosmeticStructure::span),
            ShelterSpec.CODEC.optionalFieldOf("shelter").forGetter(CosmeticStructure::shelter)
    ).apply(i, CosmeticStructure::new));

    public static final Codec<CosmeticStructure> CODEC = RAW_CODEC.flatXmap(
            CosmeticStructure::validate,
            structure -> DataResult.success(structure)
    );

    private static DataResult<CosmeticStructure> validate(CosmeticStructure structure) {
        if (structure.shelter.isPresent()) {
            ShelterGeometry.Result shelter = ShelterGeometry.derive(structure.shelter.get().interior(), structure.partCells());
            if (!shelter.ok()) return DataResult.error(shelter::error);
        }
        if (structure.span.isPresent()) return SpanStructures.validate(structure);
        if (!structure.bypassAnchorCellRequirement && !structure.footprintCells.contains(new GridOffset(0, 0))) {
            return DataResult.error(() -> "footprint_cells must include the anchor cell (0,0)"
                    + " (set \"bypass_anchor_cell_requirement\": true to override)");
        }
        for (net.minecraft.world.level.block.Rotation rotation : net.minecraft.world.level.block.Rotation.values()) {
            int minX = 0, maxX = 0, minZ = 0, maxZ = 0;
            for (GridOffset cell : structure.footprintCells) {
                GridOffset rotated = CosmeticStructures.rotateFootprintCell(rotation, cell);
                minX = Math.min(minX, rotated.dx());
                maxX = Math.max(maxX, rotated.dx());
                minZ = Math.min(minZ, rotated.dz());
                maxZ = Math.max(maxZ, rotated.dz());
            }
            if (maxX - minX + 1 > CosmeticGridCell.GRID_SIZE || maxZ - minZ + 1 > CosmeticGridCell.GRID_SIZE) {
                return DataResult.error(() -> "footprint_cells does not fit within a "
                        + CosmeticGridCell.GRID_SIZE + "x" + CosmeticGridCell.GRID_SIZE + " grid when rotated " + rotation);
            }
        }
        return DataResult.success(structure);
    }
}

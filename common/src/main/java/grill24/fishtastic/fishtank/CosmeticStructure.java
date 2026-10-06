package grill24.fishtastic.fishtank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
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
 * parts: the stored shelter is unrotated and turns with the structure at placement. Its
 * {@code kind} says what a visit looks like ({@link ShelterKind}); a hollow by default.
 * <p>
 * A structure may hold several shelters ({@code shelters}, docs/fish-shelters.md §12.9): the
 * Drowned Cathedral's chapels, apse and nave are three. One is written as {@code shelter}, as
 * every structure before them was. They may not share a cell, and none may reach into another's
 * hull, which is solid to every fish not using that shelter.
 * <p>
 * <b>Occupied cells.</b> {@code footprint_cells} is the structure's extent: every cell its parts
 * reach into, which must all land on the grid, so nothing pokes through the glass. Optional
 * {@code occupied_cells} is the subset that actually blocks other cosmetics and crawling fish —
 * the cells with solid mass near the sand (docs/fish-shelters.md §12.10). Missing, every
 * footprint cell is occupied. A span lists its occupied cells across the whole box, {@code dx}
 * and {@code dz} counting floor cells from the box's north-west corner as authored (facing
 * south); missing, they are derived from its lowest parts ({@link SpanStructures#footprint}).
 */
public record CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float scale,
                                 CosmeticTransforms.Transform itemIcon, boolean bypassAnchorCellRequirement,
                                 Optional<Span> span, List<ShelterSpec> shelters, List<GridOffset> occupiedCells) {

    public CosmeticStructure {
        shelters = List.copyOf(shelters);
        occupiedCells = List.copyOf(occupiedCells);
    }

    /** The floor cells that block other cosmetics, relative to the anchor: {@code occupied_cells}, or the whole footprint. Floor structures only. */
    public List<GridOffset> occupied() {
        return occupiedCells.isEmpty() ? footprintCells : occupiedCells;
    }

    /** The box of tanks a spanning structure fills: {@code x} long, {@code z} deep, {@code y} storeys, as authored (facing south). */
    public record Span(int x, int y, int z) {
        public static final Codec<Span> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, SpanStructures.MAX_SPAN).fieldOf("x").forGetter(Span::x),
                Codec.intRange(1, SpanStructures.MAX_SPAN).fieldOf("y").forGetter(Span::y),
                Codec.intRange(1, SpanStructures.MAX_SPAN).fieldOf("z").forGetter(Span::z)
        ).apply(i, Span::new));
    }

    /** What a visit to a shelter looks like (docs/fish-shelters.md §12.1, §12.4). */
    public enum ShelterKind implements StringRepresentable {
        /** A hollow fish hide in: the Hollow Log, the Clay Pipe, the whale's skull. */
        HOLLOW,
        /** A space fish visit on show, open to view on its sides: the Spruce Gazebo's floor. */
        OPEN,
        /**
         * An opening swum straight through: a fence arch, the Torii Gate. Its parts stay solid
         * round the opening, and it needs a horizontal mouth on each side.
         */
        GATE;

        public static final Codec<ShelterKind> CODEC = StringRepresentable.fromEnum(ShelterKind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The authored half of a shelter: the hollow's cells, optionally how many fish it holds at
     * once (default {@link ShelterGeometry#defaultCapacity}), its {@link ShelterKind}, and
     * optionally the shortest fish that may use it, in blocks of rendered length (§12.9).
     */
    public record ShelterSpec(List<ShelterGeometry.Cell> interior, Optional<Integer> capacity, ShelterKind kind,
                              Optional<Float> minLength) {
        private static final Codec<ShelterGeometry.Cell> CELL_CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(ShelterGeometry.Cell::x),
                Codec.INT.fieldOf("y").forGetter(ShelterGeometry.Cell::y),
                Codec.INT.fieldOf("z").forGetter(ShelterGeometry.Cell::z)
        ).apply(i, ShelterGeometry.Cell::new));

        public static final Codec<ShelterSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                CELL_CODEC.listOf().fieldOf("interior").forGetter(ShelterSpec::interior),
                Codec.intRange(1, 64).optionalFieldOf("capacity").forGetter(ShelterSpec::capacity),
                ShelterKind.CODEC.optionalFieldOf("kind", ShelterKind.HOLLOW).forGetter(ShelterSpec::kind),
                Codec.floatRange(0f, 4f).optionalFieldOf("min_length").forGetter(ShelterSpec::minLength)
        ).apply(i, ShelterSpec::new));

        /** A hollow — what the capture command writes. */
        public ShelterSpec(List<ShelterGeometry.Cell> interior, Optional<Integer> capacity) {
            this(interior, capacity, ShelterKind.HOLLOW);
        }

        /** A shelter open to fish of any size. */
        public ShelterSpec(List<ShelterGeometry.Cell> interior, Optional<Integer> capacity, ShelterKind kind) {
            this(interior, capacity, kind, Optional.empty());
        }

        public int capacityOrDefault() {
            return capacity.orElseGet(() -> ShelterGeometry.defaultCapacity(interior.size()));
        }
    }

    /** Identity item-icon transform: auto-fit only, no authored position/scale/rotation nudge. */
    private static final CosmeticTransforms.Transform ITEM_ICON_DEFAULT =
            new CosmeticTransforms.Transform(0f, 0f, 0f, 0f, 0f, 0f, 1f);

    public CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float scale) {
        this(footprintCells, parts, scale, ITEM_ICON_DEFAULT, false, Optional.empty(), List.of(), List.of());
    }

    public CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float scale,
                             CosmeticTransforms.Transform itemIcon) {
        this(footprintCells, parts, scale, itemIcon, false, Optional.empty(), List.of(), List.of());
    }

    /** This structure with these shelters marked in it. */
    public CosmeticStructure withShelters(List<ShelterSpec> shelters) {
        return new CosmeticStructure(footprintCells, parts, scale, itemIcon, bypassAnchorCellRequirement, span, shelters, occupiedCells);
    }

    /** A shelter paired with the shape derived for it. */
    public record DerivedShelter(ShelterSpec spec, ShelterGeometry.Shape shape) {}

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

    /**
     * The parts a shelter's shape is derived against. A gate's opening may take in a part's cell:
     * the fence arches' lantern hangs in the middle of the doorway, and it is soft (fish swim
     * through it, {@link grill24.fishtastic.FishtasticBlockTags#SOFT_COSMETIC}), so the doorway runs up past it. Those
     * cells count as opening, not wall. Whether such a part is soft is a tag, which isn't bound when
     * a structure loads, so {@code ShelterKindTest} holds every shipped gate to it instead.
     */
    public List<ShelterGeometry.Cell> shelterPartCells(ShelterSpec spec) {
        List<ShelterGeometry.Cell> cells = partCells();
        if (spec.kind() == ShelterKind.GATE) cells.removeAll(new HashSet<>(spec.interior()));
        return cells;
    }

    /** Derives one of this structure's shelters against its parts. */
    public ShelterGeometry.Result derive(ShelterSpec spec) {
        return ShelterGeometry.derive(spec.interior(), shelterPartCells(spec));
    }

    /**
     * Derived shelters per structure instance: deriving floods the structure's bounding box, and a
     * tank asks on every rebuild. A record's own hash walks every part, so keyed by identity, as
     * {@code CosmeticObstacles} keys its boxes; a datapack reload makes new instances.
     */
    private static final Map<CosmeticStructure, List<DerivedShelter>> DERIVED =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** Every shelter in this structure with its derived shape, in authored order, skipping any that fail validation. */
    public List<DerivedShelter> shelterShapes() {
        if (shelters.isEmpty()) return List.of();
        if (DERIVED.size() > 256) DERIVED.clear();
        return DERIVED.computeIfAbsent(this, s -> {
            List<DerivedShelter> out = new ArrayList<>(s.shelters.size());
            for (ShelterSpec spec : s.shelters) {
                ShelterGeometry.Result result = s.derive(spec);
                if (result.ok()) out.add(new DerivedShelter(spec, result.shape()));
            }
            return List.copyOf(out);
        });
    }

    /**
     * The parts the tank renderer handles itself every frame, by index into {@link #parts()}:
     * chests, drawn with their animated lid, and lit furnaces, which smoke. Found once per
     * structure instance rather than by walking every part every frame: that walk, which also
     * rotated each part's state first, cost up to 16 µs per tank per frame (docs/cosmetic-render-cost.md).
     * Turning a structure changes neither a part's block nor whether a furnace is lit, so the
     * parts as authored decide. Arrays are shared: don't write to them.
     */
    public record LiveParts(int[] chests, int[] litFurnaces) {}

    private static final LiveParts NO_LIVE_PARTS = new LiveParts(new int[0], new int[0]);

    /** Live parts per structure instance, keyed by identity as {@link #DERIVED} is; a datapack reload makes new instances. */
    private static final Map<CosmeticStructure, LiveParts> LIVE =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** This structure's chests and lit furnaces, by part index. */
    public LiveParts liveParts() {
        if (LIVE.size() > 256) LIVE.clear();
        return LIVE.computeIfAbsent(this, s -> {
            List<Integer> chests = new ArrayList<>(), furnaces = new ArrayList<>();
            for (int i = 0; i < s.parts.size(); i++) {
                BlockState state = s.parts.get(i).state();
                if (state.getBlock() == net.minecraft.world.level.block.Blocks.CHEST) chests.add(i);
                if (state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                        && state.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)) furnaces.add(i);
            }
            if (chests.isEmpty() && furnaces.isEmpty()) return NO_LIVE_PARTS;
            return new LiveParts(chests.stream().mapToInt(Integer::intValue).toArray(),
                    furnaces.stream().mapToInt(Integer::intValue).toArray());
        });
    }

    /** Whether this structure holds a shelter that derives. */
    public boolean hasShelter() {
        return !shelterShapes().isEmpty();
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
            // One shelter is written as "shelter", as before there could be several; more as "shelters".
            ShelterSpec.CODEC.optionalFieldOf("shelter")
                    .forGetter(s -> s.shelters.size() == 1 ? Optional.of(s.shelters.get(0)) : Optional.empty()),
            ShelterSpec.CODEC.listOf().optionalFieldOf("shelters", List.of())
                    .forGetter(s -> s.shelters.size() == 1 ? List.of() : s.shelters),
            GridOffset.CODEC.listOf().optionalFieldOf("occupied_cells", List.of()).forGetter(CosmeticStructure::occupiedCells)
    ).apply(i, (footprint, parts, scale, icon, bypass, span, one, many, occupied) -> {
        List<ShelterSpec> shelters = new ArrayList<>();
        one.ifPresent(shelters::add);
        shelters.addAll(many);
        return new CosmeticStructure(footprint, parts, scale, icon, bypass, span, shelters, occupied);
    }));

    public static final Codec<CosmeticStructure> CODEC = RAW_CODEC.flatXmap(
            CosmeticStructure::validate,
            structure -> DataResult.success(structure)
    );

    /** Whether some pair of the shape's horizontal mouths face opposite ways — a way through. */
    static boolean opensBothWays(ShelterGeometry.Shape shape) {
        for (ShelterGeometry.Mouth a : shape.mouths()) {
            for (ShelterGeometry.Mouth b : shape.mouths()) {
                if (a.outward()[1] == 0 && a.outward()[0] == -b.outward()[0] && a.outward()[2] == -b.outward()[2]
                        && b.outward()[1] == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Why two of a structure's shelters can't stand together, or null when they can: they share a
     * cell, or one's interior reaches into another's hull. A hollow's or an open shelter's hull is
     * solid to every fish not using it, so a fish on its way through the other would be shut out.
     * A gate has no hull, so nothing can reach into it.
     */
    static String overlap(List<ShelterSpec> specs, List<ShelterGeometry.Shape> shapes) {
        for (int a = 0; a < specs.size(); a++) {
            for (int b = 0; b < specs.size(); b++) {
                if (a == b) continue;
                if (a < b) {
                    for (ShelterGeometry.Cell cell : specs.get(a).interior()) {
                        if (specs.get(b).interior().contains(cell)) {
                            return "shelters " + a + " and " + b + " share the cell " + cell;
                        }
                    }
                }
                if (specs.get(b).kind() == ShelterKind.GATE) continue;
                ShelterGeometry.Shape hull = shapes.get(b);
                for (ShelterGeometry.Cell c : specs.get(a).interior()) {
                    if (c.x() >= hull.hullMin().x() && c.x() <= hull.hullMax().x()
                            && c.y() >= hull.hullMin().y() && c.y() <= hull.hullMax().y()
                            && c.z() >= hull.hullMin().z() && c.z() <= hull.hullMax().z()) {
                        return "shelter " + a + "'s cell " + c + " lies in shelter " + b + "'s hull";
                    }
                }
            }
        }
        return null;
    }

    private static DataResult<CosmeticStructure> validate(CosmeticStructure structure) {
        List<ShelterGeometry.Shape> shapes = new ArrayList<>();
        for (int n = 0; n < structure.shelters.size(); n++) {
            ShelterSpec spec = structure.shelters.get(n);
            ShelterGeometry.Result shelter = structure.derive(spec);
            String which = structure.shelters.size() == 1 ? "" : "shelter " + n + ": ";
            if (!shelter.ok()) return DataResult.error(() -> which + shelter.error());
            if (spec.kind() == ShelterKind.GATE && !opensBothWays(shelter.shape())) {
                return DataResult.error(() -> which + "a gate's opening needs a horizontal mouth on each side");
            }
            shapes.add(shelter.shape());
        }
        String overlap = overlap(structure.shelters, shapes);
        if (overlap != null) return DataResult.error(() -> overlap);
        if (structure.span.isPresent()) return SpanStructures.validate(structure);
        if (!structure.bypassAnchorCellRequirement && !structure.footprintCells.contains(new GridOffset(0, 0))) {
            return DataResult.error(() -> "footprint_cells must include the anchor cell (0,0)"
                    + " (set \"bypass_anchor_cell_requirement\": true to override)");
        }
        if (!structure.occupiedCells.isEmpty()) {
            if (!structure.bypassAnchorCellRequirement && !structure.occupiedCells.contains(new GridOffset(0, 0))) {
                return DataResult.error(() -> "occupied_cells must include the anchor cell (0,0)");
            }
            for (GridOffset cell : structure.occupiedCells) {
                if (!structure.footprintCells.contains(cell)) {
                    return DataResult.error(() -> "occupied cell " + cell + " is not in footprint_cells");
                }
            }
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

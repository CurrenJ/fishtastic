package grill24.fishtastic.fishtank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A multi-block cosmetic decoration spanning a reserved footprint in a fish tank's 3×3 floor grid
 * (e.g. a small arch built from fence posts). Placed/rotated/removed as one rigid unit, alongside
 * (not replacing) single-block {@link PlacedCosmetic}s. Loaded as datapack content, synced to clients
 * the same way {@code Quest}/{@code ShopEntry} are.
 * <p>
 * Authored assuming the structure faces south; the placing player's facing supplies the
 * {@link net.minecraft.world.level.block.Rotation} applied at placement (see {@code FishTankBlock}).
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
 */
public record CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float scale,
                                 CosmeticTransforms.Transform itemIcon) {

    /** Identity item-icon transform: auto-fit only, no authored position/scale/rotation nudge. */
    private static final CosmeticTransforms.Transform ITEM_ICON_DEFAULT =
            new CosmeticTransforms.Transform(0f, 0f, 0f, 0f, 0f, 0f, 1f);

    public CosmeticStructure(List<GridOffset> footprintCells, List<StructurePart> parts, float scale) {
        this(footprintCells, parts, scale, ITEM_ICON_DEFAULT);
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
            GridOffset.CODEC.listOf().fieldOf("footprint_cells").forGetter(CosmeticStructure::footprintCells),
            StructurePart.CODEC.listOf().fieldOf("parts").forGetter(CosmeticStructure::parts),
            Codec.FLOAT.optionalFieldOf("scale", (float) CosmeticGridCell.CELL_WIDTH).forGetter(CosmeticStructure::scale),
            CosmeticTransforms.Transform.MAP_CODEC.codec().optionalFieldOf("item_icon", ITEM_ICON_DEFAULT).forGetter(CosmeticStructure::itemIcon)
    ).apply(i, CosmeticStructure::new));

    public static final Codec<CosmeticStructure> CODEC = RAW_CODEC.flatXmap(
            CosmeticStructure::validate,
            structure -> DataResult.success(structure)
    );

    private static DataResult<CosmeticStructure> validate(CosmeticStructure structure) {
        if (!structure.footprintCells.contains(new GridOffset(0, 0))) {
            return DataResult.error(() -> "footprint_cells must include the anchor cell (0,0)");
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

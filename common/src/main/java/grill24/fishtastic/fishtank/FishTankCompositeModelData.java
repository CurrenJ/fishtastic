package grill24.fishtastic.fishtank;

import grill24.fishtastic.FishtasticBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable snapshot of the block-entity data that drives the fish tank's composite model:
 * which body geometry ({@code shape}) to use, which blocks to use for the frame, fill (sand),
 * and glass layers, and which faces are open (connected to an adjacent tank).
 *
 * <p>Platform adapters pass this around as Fabric render data or a NeoForge {@code ModelProperty} value.
 */
public record FishTankCompositeModelData(FishTankShape shape, Block frameBlock, Block sandBlock, Block glassBlock, Set<Direction> openFaces, Set<TankDiagonal> filledDiagonals, Set<TankEdgeDiagonal> filledEdgeDiagonals) {

    public static final FishTankCompositeModelData DEFAULT = new FishTankCompositeModelData(
            FishTankShape.STANDARD, Blocks.OAK_PLANKS, Blocks.SAND,
            FishtasticBlocks.CLEAR_STAINED_GLASS.get(DyeColor.BLUE).value(),
            EnumSet.noneOf(Direction.class), EnumSet.noneOf(TankDiagonal.class), EnumSet.noneOf(TankEdgeDiagonal.class)
    );

    public FishTankCompositeModelData(FishTankShape shape, Block frameBlock, Block sandBlock, Block glassBlock) {
        this(shape, frameBlock, sandBlock, glassBlock, EnumSet.noneOf(Direction.class), EnumSet.noneOf(TankDiagonal.class), EnumSet.noneOf(TankEdgeDiagonal.class));
    }

    /**
     * Returns a 0–63 bitmask index into the permutation model arrays,
     * where each bit corresponds to a {@link Direction} ordinal being open.
     */
    public int getPermutationIndex() {
        int index = 0;
        for (Direction dir : Direction.values()) {
            if (openFaces.contains(dir)) {
                index |= (1 << dir.ordinal());
            }
        }
        return index;
    }

    public static Set<Direction> openFacesFromIndex(int index) {
        Set<Direction> openFaces = EnumSet.noneOf(Direction.class);
        for (Direction dir : Direction.values()) {
            if ((index & (1 << dir.ordinal())) != 0) {
                openFaces.add(dir);
            }
        }
        return openFaces;
    }

    /**
     * The corners that need a post rendered back in despite both their orthogonal faces being
     * open — true only when the diagonal neighbor cell for that corner is empty. This is the
     * single canonicalization point for the corner-post override logic (see docs/... diagonal
     * corner posts): {@code TaperedFrameGeometryGenerator}/{@code ShellFrameGeometryGenerator}/etc.
     * gate a corner post on "both adjacent faces closed"; this mask adds back the corners where
     * that gate says "no post" but the diagonal being empty means one is still needed to close off
     * the tank's silhouette there.
     */
    public Set<TankDiagonal> getDiagonalOverrideMask() {
        Set<TankDiagonal> mask = EnumSet.noneOf(TankDiagonal.class);
        for (TankDiagonal diagonal : TankDiagonal.values()) {
            boolean orthogonallyEligible = openFaces.contains(diagonal.first()) && openFaces.contains(diagonal.second());
            if (orthogonallyEligible && !filledDiagonals.contains(diagonal)) {
                mask.add(diagonal);
            }
        }
        return mask;
    }

    /**
     * The edges that need a frame beam rendered back in despite both their horizontal and
     * vertical faces being open — true only when the edge-diagonal neighbor cell is empty.
     * Mirrors {@link #getDiagonalOverrideMask()} for the edge-diagonal case: the base
     * per-permutation bake never draws an edge beam (nothing in the frame generators gates on the
     * combination of one horizontal face and one vertical face), so this mask is unconditional on
     * eligibility rather than "the base bake omitted it" — see {@code TaperedFrameGeometryGenerator#generateEdgeFragment}.
     */
    public Set<TankEdgeDiagonal> getEdgeDiagonalOverrideMask() {
        Set<TankEdgeDiagonal> mask = EnumSet.noneOf(TankEdgeDiagonal.class);
        for (TankEdgeDiagonal edgeDiagonal : TankEdgeDiagonal.values()) {
            boolean eligible = openFaces.contains(edgeDiagonal.horizontal()) && openFaces.contains(edgeDiagonal.vertical());
            if (eligible && !filledEdgeDiagonals.contains(edgeDiagonal)) {
                mask.add(edgeDiagonal);
            }
        }
        return mask;
    }

    /**
     * The edges whose beam does <em>not</em> render (edge-diagonal cell filled by a real neighbor —
     * the inverse of {@link #getEdgeDiagonalOverrideMask()}) and so need their base glass bake's
     * flush corner sliver restored instead. The base glass bake (see
     * {@code TaperedGlassGeometryGenerator#addNorthGlassPane} et al.) always omits that sliver at an
     * eligible edge's cap band — regardless of runtime fill state, since the glass model is baked
     * once per permutation with no knowledge of it — so this mask adds it back with a small
     * glass-textured fragment on exactly the permutations where the beam itself won't be there to
     * cover it. Each entry still needs a per-corner check against the actual perpendicular wall
     * being closed (a corner cell only has glass to restore at all when that wall exists) — see
     * {@code TankEdgeDiagonal#endDiagonals()}/{@code #wallFace}, applied by the compositor using
     * this same {@link #openFaces()}.
     */
    public Set<TankEdgeDiagonal> getEdgeDiagonalGlassFillMask() {
        Set<TankEdgeDiagonal> mask = EnumSet.noneOf(TankEdgeDiagonal.class);
        for (TankEdgeDiagonal edgeDiagonal : TankEdgeDiagonal.values()) {
            boolean eligible = openFaces.contains(edgeDiagonal.horizontal()) && openFaces.contains(edgeDiagonal.vertical());
            if (eligible && filledEdgeDiagonals.contains(edgeDiagonal)) {
                mask.add(edgeDiagonal);
            }
        }
        return mask;
    }
}

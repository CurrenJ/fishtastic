package grill24.fishtastic.client.ambient;

import grill24.fishsim.ambient.WaterSource;
import grill24.fishtastic.ecology.AmbientFluid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The {@link WaterSource} the ambient discovery reads the client level through — the only
 * Minecraft-aware part of discovery (docs/fish-in-world.md §discovery).
 *
 * <p><b>Loaded chunks only.</b> Every read starts with {@link ClientLevel#hasChunkAt}, and a cell
 * whose chunk is absent answers {@link #LOADED} clear. Asking the level for a block in an unloaded
 * chunk would load it — the one thing ambient discovery must never do — so the guard comes first
 * and short-circuits the rest.
 *
 * <p>The three answers mirror vanilla exactly: water is {@link FluidTags#WATER} ({@link FluidTags#LAVA} in the
 * Nether, where the ambient fluid is lava and the WATER flag means "holds the ambient fluid"); its surface is
 * {@link FluidState#getHeight} above the cell's own bottom, which is what makes a still source cell
 * 8/9 of a block and a flowing cell lower; and a block's collision top is the highest point of its
 * collision shape, so a waterlogged bottom slab leaves the water standing on the slab. A shape
 * taller than its own block — a fence post, a wall — also rises into the cell above it, so that
 * cell reports collision too and says how far up the water above the obstacle starts.
 *
 * <p>Single-threaded (client thread only), allocation-free, and counted so the harness can report
 * what a tick of discovery actually cost in world reads.
 */
public final class AmbientWaterSource implements WaterSource {

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private AmbientFluid fluid = AmbientFluid.WATER;
    private long flagReads, fluidReads, collisionReads;

    public void setLevel(ClientLevel level) {
        this.level = level;
        this.fluid = level == null ? AmbientFluid.WATER
                : AmbientFluid.forDimension(level.dimension().identifier().toString());
    }

    /** The fluid ambient fish live in here: water, or lava in the Nether (an easter egg for fire-resistant swimmers). */
    public AmbientFluid fluid() {
        return fluid;
    }

    public ClientLevel level() {
        return level;
    }

    private record ContextKey(Object biome, java.util.Set<grill24.fishtastic.data.FishProfile.Zone> zones) {}

    @Override
    public Object contextKey(int x, int surfaceY, int z) {
        ClientLevel lvl = level;
        pos.set(x, surfaceY, z);
        if (lvl == null || !lvl.hasChunkAt(pos)) return null;
        var biome = lvl.getBiome(pos);
        return new ContextKey(biome.unwrapKey().map(key -> (Object) key).orElse(biome),
                grill24.fishtastic.data.FishProfile.Zone.resolve(biome, surfaceY, lvl.getSeaLevel()));
    }

    @Override
    public int flags(int x, int y, int z) {
        flagReads++;
        ClientLevel lvl = level;
        if (lvl == null) return 0;
        pos.set(x, y, z);
        if (!lvl.hasChunkAt(pos)) return 0;
        int f = LOADED;
        // The scan band is taller than the world is deep, so a column near the bottom floor runs
        // past it. Below the build height the answer is void air — definitive, not unknown — and
        // asking the level for it would be out of range.
        if (lvl.isOutsideBuildHeight(pos)) return f;
        boolean water = lvl.getFluidState(pos).is(fluid == AmbientFluid.LAVA ? FluidTags.LAVA : FluidTags.WATER);
        if (water) f |= WATER;
        if (!lvl.getBlockState(pos).getCollisionShape(lvl, pos).isEmpty()) {
            f |= COLLISION;
        } else if (water && shapeTop(lvl, below.set(x, y - 1, z)) > 1f) {
            // A fence post or a wall reaches half a block into the cell above it. That cell has no
            // collision shape of its own, but a body standing in its water would clip the posts, so
            // it reports collision and answers how far up it starts.
            f |= COLLISION;
        }
        return f;
    }

    @Override
    public float fluidTop(int x, int y, int z) {
        fluidReads++;
        ClientLevel lvl = level;
        if (lvl == null) return 1f;
        pos.set(x, y, z);
        if (!lvl.hasChunkAt(pos) || lvl.isOutsideBuildHeight(pos)) return 1f;
        FluidState fluid = lvl.getFluidState(pos);
        // A cell with no fluid can only be asked about inside a span the scan already found water
        // in, so the answer is "full to the cell's own top" — what a cell under water reads as.
        return fluid.isEmpty() ? 1f : fluid.getHeight(lvl, pos);
    }

    @Override
    public float collisionTop(int x, int y, int z) {
        collisionReads++;
        ClientLevel lvl = level;
        if (lvl == null) return 0f;
        pos.set(x, y, z);
        if (!lvl.hasChunkAt(pos) || lvl.isOutsideBuildHeight(pos)) return 0f;
        float top = shapeTop(lvl, pos);
        if (top < 1f) {
            // The slope of a bottom slab, or how far a fence post or wall from below rises into
            // this cell. Whatever a fish has to clear before its water starts.
            top = Math.max(top, shapeTop(lvl, below.set(x, y - 1, z)) - 1f);
        }
        return Math.max(0f, Math.min(1f, top));
    }

    /** Highest point of the collision shape at {@code at}, in that block's own frame; 0 if none. */
    private static float shapeTop(ClientLevel lvl, BlockPos at) {
        VoxelShape shape = lvl.getBlockState(at).getCollisionShape(lvl, at);
        return shape.isEmpty() ? 0f : (float) shape.max(Direction.Axis.Y);
    }

    public long flagReads() {
        return flagReads;
    }

    public long fluidReads() {
        return fluidReads;
    }

    public long collisionReads() {
        return collisionReads;
    }

    public void resetCounters() {
        flagReads = fluidReads = collisionReads = 0;
    }
}

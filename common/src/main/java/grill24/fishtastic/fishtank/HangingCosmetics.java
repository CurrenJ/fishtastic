package grill24.fishtastic.fishtank;

import grill24.fishtastic.FishtasticBlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules for cosmetics hung from a tank's lid (the {@code #fishtastic:tank_hanging_cosmetics} tag):
 * which pieces stack into longer strands, and which block states a strand is drawn from.
 *
 * <p>A hanging cosmetic is stored as a {@link PlacedCosmetic} whose block is the <i>pendant</i> —
 * the piece at the bottom — and whose {@code height} counts every segment from the lid down,
 * pendant included. Only the block is stored; the state of each segment (a lantern's
 * {@code hanging}, a dripstone's thickness, a vine's head versus its stem) is derived here, so
 * nothing about the drawing has to survive a save.
 *
 * <ul>
 *   <li>Chains, pointed dripstone, weeping vines and glow berries grow by adding more of the same.</li>
 *   <li>A lantern hangs from the lid on its own; adding chain lowers it one link at a time, and a
 *       lantern added to a bare chain hangs from its end.</li>
 *   <li>Everything else (hanging roots, spore blossoms) is a single piece.</li>
 * </ul>
 */
public final class HangingCosmetics {

    private HangingCosmetics() {}

    /** The hanging block a held stack would place, or null if it isn't a hanging cosmetic. */
    @Nullable
    public static Block blockOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(FishtasticBlockTags.TANK_HANGING_COSMETICS)) {
            return bi.getBlock();
        }
        return null;
    }

    /** A strand of the same block, grown by adding more of it. */
    private static boolean selfStacking(Block block) {
        return block == Blocks.IRON_CHAIN || block == Blocks.POINTED_DRIPSTONE
                || block == Blocks.WEEPING_VINES || block == Blocks.CAVE_VINES;
    }

    private static boolean isLantern(Block block) {
        return block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN;
    }

    /**
     * The cosmetic that results from adding {@code held} to {@code existing}, or null if the two
     * don't combine. The caller checks the result's height against the column's room.
     */
    @Nullable
    public static PlacedCosmetic combine(PlacedCosmetic existing, Block held) {
        Block pendant = existing.block();
        if (held == pendant && selfStacking(pendant)) {
            return new PlacedCosmetic(existing.blockState(), existing.height() + 1);
        }
        if (held == Blocks.IRON_CHAIN && isLantern(pendant)) {
            return new PlacedCosmetic(existing.blockState(), existing.height() + 1);
        }
        if (pendant == Blocks.IRON_CHAIN && isLantern(held)) {
            return new PlacedCosmetic(held.defaultBlockState(), existing.height() + 1);
        }
        return null;
    }

    /**
     * Takes one segment off a strand: the cosmetic left behind (null when nothing is) and the
     * block whose item goes back to the player. A lantern on a chain gives up its links first.
     */
    public record Removal(@Nullable PlacedCosmetic remaining, Block returned) {}

    public static Removal removeOne(PlacedCosmetic existing) {
        if (existing.height() <= 1) {
            return new Removal(null, existing.block());
        }
        Block returned = isLantern(existing.block()) ? Blocks.IRON_CHAIN : existing.block();
        return new Removal(new PlacedCosmetic(existing.blockState(), existing.height() - 1), returned);
    }

    /** Every segment's block state, from the lid down. */
    public static List<BlockState> segments(PlacedCosmetic cosmetic) {
        Block pendant = cosmetic.block();
        int height = Math.max(1, cosmetic.height());
        List<BlockState> out = new ArrayList<>(height);

        if (pendant == Blocks.POINTED_DRIPSTONE) {
            // Vanilla's order from the attached end: base, middles, frustum, tip.
            for (int i = 0; i < height; i++) {
                int fromTip = height - 1 - i;
                DripstoneThickness thickness = fromTip == 0 ? DripstoneThickness.TIP
                        : fromTip == 1 ? DripstoneThickness.FRUSTUM
                        : i == 0 ? DripstoneThickness.BASE
                        : DripstoneThickness.MIDDLE;
                out.add(Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)
                        .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, thickness));
            }
            return out;
        }
        if (pendant == Blocks.WEEPING_VINES) {
            for (int i = 0; i < height - 1; i++) out.add(Blocks.WEEPING_VINES_PLANT.defaultBlockState());
            out.add(Blocks.WEEPING_VINES.defaultBlockState());
            return out;
        }
        if (pendant == Blocks.CAVE_VINES) {
            // Placed from glow berries, so the strand carries some: every other stem segment and the head.
            for (int i = 0; i < height - 1; i++) {
                out.add(Blocks.CAVE_VINES_PLANT.defaultBlockState()
                        .setValue(BlockStateProperties.BERRIES, (height - 1 - i) % 2 == 0));
            }
            out.add(Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true));
            return out;
        }
        if (isLantern(pendant)) {
            for (int i = 0; i < height - 1; i++) out.add(Blocks.IRON_CHAIN.defaultBlockState());
            out.add(pendant.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
            return out;
        }
        for (int i = 0; i < height; i++) out.add(pendant.defaultBlockState());
        return out;
    }
}

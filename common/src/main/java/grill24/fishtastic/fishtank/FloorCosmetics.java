package grill24.fishtastic.fishtank;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Rules for single-cell floor cosmetics (the {@code #fishtastic:tank_cosmetics} tag) whose look
 * depends on more than the block: which ones stack in one cell, and which block states they are
 * drawn from.
 *
 * <ul>
 *   <li>Sea pickles, pink petals, wildflowers and leaf litter stack: each click adds one more to
 *       the cell, as placing them in the world does, and removal hands them back one at a time.
 *       Only the count is saved.</li>
 *   <li>Two-tall plants (sunflower, lilac, tall grass, small dripleaf, ...) are stored as their
 *       lower half; the mesh draws the upper half above it.</li>
 *   <li>A sweet berry bush is shown grown, with berries. Its age is never saved — it is derived
 *       here at placement and on load.</li>
 * </ul>
 */
public final class FloorCosmetics {

    private FloorCosmetics() {}

    private static final List<IntegerProperty> STACK_PROPERTIES = List.of(
            BlockStateProperties.PICKLES, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT);

    /** The count a cosmetic stacks by in one cell, or null if it doesn't stack. */
    @Nullable
    public static IntegerProperty stackProperty(BlockState state) {
        for (IntegerProperty property : STACK_PROPERTIES) {
            if (state.hasProperty(property)) return property;
        }
        return null;
    }

    public static int maxCount(IntegerProperty property) {
        return Collections.max(property.getPossibleValues());
    }

    /** The state a placed (or loaded) cosmetic is drawn as, from the state placement or loading built. */
    public static BlockState displayState(BlockState state) {
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            return state.setValue(BlockStateProperties.AGE_3, 3);
        }
        return state;
    }

    /** The upper half drawn one block above a two-tall plant, or null for anything one block tall. */
    @Nullable
    public static BlockState upperHalf(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return null;
        return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
    }
}

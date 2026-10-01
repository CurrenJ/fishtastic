package grill24.fishtastic.fishtank;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * Vertical runs of connected tanks — a stack whose floors and ceilings are open between storeys,
 * so it holds one tall body of water. Kelp grows up such a column from the bottom tank's sand, and
 * hanging cosmetics drop down it from the top tank's lid.
 *
 * <p>Column cosmetics are stored once, on the tank they are attached to (the floor tank for kelp,
 * the ceiling tank for hanging pieces), and measured in segments of {@link #SEGMENT_HEIGHT}. Every
 * tank in the column draws the segments that start inside it — see
 * {@code FishTankBlockEntityRenderer}'s column segments — so a tall strand stays visible whichever
 * of its tanks is on screen.
 */
public final class TankColumns {

    /** Height of one kelp or hanging segment, in blocks (the default cosmetic scale). */
    public static final float SEGMENT_HEIGHT = 0.25f;
    /** How many segments one storey adds to a column. */
    public static final int SEGMENTS_PER_BLOCK = 4;
    /** Never walk further than this — a group is far smaller, this only guards a corrupt loop. */
    private static final int MAX_WALK = 64;

    private TankColumns() {}

    /**
     * Most segments a column of {@code storeys} tanks can hold: everything between the sand and
     * the lid, less the one-segment gap a single tank already keeps (3 in one tank, 7 in two).
     */
    public static int maxSegments(int storeys) {
        return storeys * SEGMENTS_PER_BLOCK - 1;
    }

    /** The bottom tank of the column {@code pos} is in — the one whose sand the column stands on. */
    public static BlockPos floorOf(BlockGetter level, BlockPos pos) {
        return walk(level, pos, Direction.DOWN);
    }

    /** The top tank of the column {@code pos} is in — the one whose lid the column hangs from. */
    public static BlockPos ceilingOf(BlockGetter level, BlockPos pos) {
        return walk(level, pos, Direction.UP);
    }

    /** Number of tanks from {@code floor} up to the column's ceiling, inclusive. */
    public static int storeys(BlockGetter level, BlockPos floor) {
        return ceilingOf(level, floor).getY() - floor.getY() + 1;
    }

    /** Follows open faces in {@code dir} for as long as the next block is a tank that is open back. */
    private static BlockPos walk(BlockGetter level, BlockPos start, Direction dir) {
        BlockPos pos = start;
        for (int i = 0; i < MAX_WALK; i++) {
            if (!(level.getBlockEntity(pos) instanceof FishTankBlockEntity tank) || !tank.isFaceOpen(dir)) break;
            BlockPos next = pos.relative(dir);
            if (!(level.getBlockEntity(next) instanceof FishTankBlockEntity nextTank)
                    || !nextTank.isFaceOpen(dir.getOpposite())) break;
            pos = next;
        }
        return pos;
    }
}

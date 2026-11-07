package grill24.fishtastic.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** How a block stands in a companion's way (docs/fish-companions.md). */
public final class CompanionBlocks {

    private CompanionBlocks() {}

    /**
     * The block's collision as the companion sim should see it. The sim fills a cell with anything
     * that collides, so an open door's or trapdoor's thin plate would wall off the opening it swung
     * clear of: those read as empty. Shut, they block, as any other block with a collision shape.
     */
    public static VoxelShape collision(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) return Shapes.empty();
        if (state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) return Shapes.empty();
        return state.getCollisionShape(level, pos);
    }
}

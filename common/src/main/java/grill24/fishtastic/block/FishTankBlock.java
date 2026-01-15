package grill24.fishtastic.block;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FishTankBlock extends Block implements EntityBlock {
    public FishTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return RegistrationApiSided.getInstance().createFishTankBlockEntity(blockPos, blockState);
    }

    @Override
    protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(blockState, level, blockPos, oldState, movedByPiston);

        if (!level.isClientSide) {
            // Update connections for this tank
            updateConnections(level, blockPos);

            // Update connections for all adjacent tanks
            for (Direction direction : Direction.values()) {
                BlockPos adjacentPos = blockPos.relative(direction);
                if (level.getBlockEntity(adjacentPos) instanceof FishTankBlockEntity) {
                    updateConnections(level, adjacentPos);
                }
            }
        }
    }

    @Override
    protected BlockState updateShape(BlockState blockState, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos blockPos, BlockPos neighborPos) {
        if (!level.isClientSide()) {
            // Update connections when a neighboring block changes
            updateConnections((Level) level, blockPos);
        }
        return super.updateShape(blockState, direction, neighborState, level, blockPos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // If the block is being replaced by a different block (not just state change)
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                // Update connections for all adjacent tanks
                for (Direction direction : Direction.values()) {
                    BlockPos adjacentPos = pos.relative(direction);
                    if (level.getBlockEntity(adjacentPos) instanceof FishTankBlockEntity) {
                        updateConnections(level, adjacentPos);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Update connections for a fish tank by detecting adjacent fish tanks.
     */
    private void updateConnections(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FishTankBlockEntity fishTank) {
            fishTank.updateConnections(level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                // Get all blocks from configured tags via platform API
                List<Block> availableFrameBlocks = RegistrationApiSided.getInstance().getConfiguredFrameBlocks();

                if (!availableFrameBlocks.isEmpty()) {
                    Block newBlock = availableFrameBlocks.get(level.random.nextInt(availableFrameBlocks.size()));

                    // If sneaking, change sand block; otherwise change frame block
                    if (player.isShiftKeyDown()) {
                        fishTank.setSandBlock(newBlock);
                        player.displayClientMessage(
                            Component.literal("Fish tank sand changed to: " +
                                BuiltInRegistries.BLOCK.getKey(newBlock)),
                            true
                        );
                    } else {
                        fishTank.setFrameBlock(newBlock);
                        player.displayClientMessage(
                            Component.literal("Fish tank frame changed to: " +
                                BuiltInRegistries.BLOCK.getKey(newBlock)),
                            true
                        );
                    }

                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand hand, BlockHitResult blockHitResult) {
        if (!level.isClientSide) {
            // Check if the player is holding a block item
            if (itemStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                Block heldBlock = blockItem.getBlock();

                BlockEntity be = level.getBlockEntity(blockPos);
                if (be instanceof FishTankBlockEntity fishTank) {
                    // Get the hit position relative to the block
                    double relativeY = blockHitResult.getLocation().y - blockPos.getY();

                    // Top 3/4 of the block (y > 0.25) sets frame block
                    // Bottom 1/4 of the block (y <= 0.25) sets sand block
                    if (relativeY > 0.25) {
                        fishTank.setFrameBlock(heldBlock);
                        player.displayClientMessage(
                            Component.literal("Fish tank frame changed to: " +
                                BuiltInRegistries.BLOCK.getKey(heldBlock)),
                            true
                        );
                    } else {
                        fishTank.setSandBlock(heldBlock);
                        player.displayClientMessage(
                            Component.literal("Fish tank sand changed to: " +
                                BuiltInRegistries.BLOCK.getKey(heldBlock)),
                            true
                        );
                    }

                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}

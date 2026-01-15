package grill24.fishtastic.block;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
}

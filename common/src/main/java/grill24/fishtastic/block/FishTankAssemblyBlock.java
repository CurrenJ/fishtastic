package grill24.fishtastic.block;

import grill24.fishtastic.blockentity.FishTankAssemblyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Combines a frame, sand, and glass material into a Fish Tank item stamped with
 * exactly those materials (see {@code FishTankMaterials}). Replaces the old
 * in-world right-click customization flow — tanks are now built once, up front.
 */
public class FishTankAssemblyBlock extends Block implements EntityBlock {
    public FishTankAssemblyBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // This Minecraft version leaves spilling a container to each block; 26.1.2 does it in BlockEntity.
        if (!level.isClientSide() && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FishTankAssemblyBlockEntity assembly) {
            Containers.dropContents(level, pos, assembly);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FishTankAssemblyBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FishTankAssemblyBlockEntity assembly) {
            player.openMenu(assembly);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}

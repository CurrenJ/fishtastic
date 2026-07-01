package grill24.fishtastic.block;

import grill24.fishtastic.blockentity.FishTankAssemblyBlockEntity;
import net.minecraft.core.BlockPos;
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FishTankAssemblyBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FishTankAssemblyBlockEntity assembly) {
            player.openMenu(assembly);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}

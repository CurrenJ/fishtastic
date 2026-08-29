package grill24.fishtastic.block;

import grill24.fishtastic.blockentity.ElectricFishOrganizerBlockEntity;
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
 * Crafted from an Electric Eel; sorts sized fish dropped into it into per-species
 * {@code PileOfFishItem} stacks (see {@link ElectricFishOrganizerBlockEntity}).
 */
public class ElectricFishOrganizerBlock extends Block implements EntityBlock {
    public ElectricFishOrganizerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricFishOrganizerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ElectricFishOrganizerBlockEntity organizer) {
            player.openMenu(organizer);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}

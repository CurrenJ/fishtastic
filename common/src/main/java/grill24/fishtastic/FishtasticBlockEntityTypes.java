package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FishtasticBlockEntityTypes {
    public static Holder<BlockEntityType<?>> FISH_TANK;

    public static void registerBlockEntityTypes() {
        FISH_TANK = RegistrationApiSided.getInstance().registerBlockEntityType(
            "fish_tank",
            FishtasticBlockEntityTypes::createFishTankBlockEntity,
            () -> new Block[] { FishtasticBlocks.FISH_TANK.value() }
        );
    }

    /**
     * Factory method for FishTankBlockEntity - can be overridden by platform-specific implementations
     */
    private static FishTankBlockEntity createFishTankBlockEntity(BlockPos pos, BlockState state) {
        return RegistrationApiSided.getInstance().createFishTankBlockEntity(pos, state);
    }
}

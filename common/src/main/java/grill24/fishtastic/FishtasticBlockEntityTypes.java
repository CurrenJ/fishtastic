package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.blockentity.FishTankAssemblyBlockEntity;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.blockentity.MarineCompostBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FishtasticBlockEntityTypes {
    public static Holder<BlockEntityType<?>> FISH_TANK;
    public static Holder<BlockEntityType<?>> FISH_TANK_ASSEMBLY;
    public static Holder<BlockEntityType<?>> MARINE_COMPOST;
    public static Holder<BlockEntityType<?>> FISH_PILE;

    public static void registerBlockEntityTypes() {
        FISH_TANK = RegistrationApiSided.getInstance().registerBlockEntityType(
            "fish_tank",
            FishtasticBlockEntityTypes::createFishTankBlockEntity,
            () -> new Block[] { FishtasticBlocks.FISH_TANK.value() }
        );
        FISH_TANK_ASSEMBLY = RegistrationApiSided.getInstance().registerBlockEntityType(
            "fish_tank_assembly",
            FishTankAssemblyBlockEntity::new,
            () -> new Block[] { FishtasticBlocks.FISH_TANK_ASSEMBLY.value() }
        );
        MARINE_COMPOST = RegistrationApiSided.getInstance().registerBlockEntityType(
            "marine_compost",
            MarineCompostBlockEntity::new,
            () -> new Block[] { FishtasticBlocks.MARINE_COMPOST.value() }
        );
        FISH_PILE = RegistrationApiSided.getInstance().registerBlockEntityType(
            "fish_pile",
            FishPileBlockEntity::new,
            () -> new Block[] { FishtasticBlocks.FISH_PILE.value() }
        );
    }

    /**
     * Factory method for FishTankBlockEntity - can be overridden by platform-specific implementations
     */
    private static FishTankBlockEntity createFishTankBlockEntity(BlockPos pos, BlockState state) {
        return RegistrationApiSided.getInstance().createFishTankBlockEntity(pos, state);
    }
}

package grill24.fishtastic.architectury;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankFrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;
import java.util.function.Supplier;

public interface IRegistrationApi {
    <I extends Item> Holder<Item> registerItem(final String name, final Function<ResourceLocation, ? extends I> func);
    <I extends Block> Holder<Block> registerBlock(final String name, final Function<ResourceLocation, ? extends I> func);
     Holder<BlockEntityType<?>> registerBlockEntityType(final String name, Supplier<BlockEntityType.Builder<?>> builder);

    // Registries
    Registry<FishTankFrameType> fishTankFrameTypes();

    // Platform-specific BlockEntity creation
    FishTankBlockEntity createFishTankBlockEntity(BlockPos pos, BlockState state);

    // Request model data update for block entity (client-side)
    void requestModelDataUpdate(BlockEntity blockEntity);

    // Get all blocks from configured frame block tags
    java.util.List<Block> getConfiguredFrameBlocks();
}

package grill24.fishtastic.architectury;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankFrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface IRegistrationApi {
    <I extends Item> Holder<Item> registerItem(final String name, final Function<Identifier, ? extends I> func);
    <I extends Block> Holder<Block> registerBlock(final String name, final Function<Identifier, ? extends I> func);
    Holder<BlockEntityType<?>> registerBlockEntityType(final String name, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory, Supplier<Block[]> validBlocksSupplier);
    <T> Holder<DataComponentType<T>> registerDataComponent(final String name, final UnaryOperator<DataComponentType.Builder<T>> builderOperator);
    Holder<CreativeModeTab> registerCreativeModeTab(final String name, final Function<Identifier, ? extends CreativeModeTab> func);

    Registry<Block> blocks();
    Registry<Item> items();
    Registry<BlockEntityType<?>> blockEntityTypes();
    Registry<DataComponentType<?>> dataComponentTypes();
    Registry<CreativeModeTab> creativeModeTabs();

    // Registries
    Registry<FishTankFrameType> fishTankFrameTypes();

    // Platform-specific BlockEntity creation
    FishTankBlockEntity createFishTankBlockEntity(BlockPos pos, BlockState state);

    // Request model data update for block entity (client-side)
    void requestModelDataUpdate(BlockEntity blockEntity);

    // Check if a block is blacklisted for a specific fish tank part
    boolean isBlockBlacklisted(Block block, String partName);
}

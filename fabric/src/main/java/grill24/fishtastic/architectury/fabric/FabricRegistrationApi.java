package grill24.fishtastic.architectury.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.architectury.IRegistrationApi;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankFrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.naming.OperationNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static grill24.fishtastic.util.Utility.ft;

public class FabricRegistrationApi implements IRegistrationApi {
    public static FabricRegistrationApi INSTANCE = new FabricRegistrationApi();

    public static FabricRegistrationApi getInstance() {
        return INSTANCE;
    }

    // ----- Registration Methods ----- //

    @Override
    public <I extends Item> Holder<Item> registerItem(final String name, final Function<ResourceLocation, ? extends I> func) {
        return register(BuiltInRegistries.ITEM, name, func);
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(final String name, final Function<ResourceLocation, ? extends I> func) {
        // Create the block
        Block block = func.apply(ResourceLocation.fromNamespaceAndPath(Fishtastic.MOD_ID, name));
        // Register the BlockItem for the block
        registerItem(name, loc -> new BlockItem(block, new Item.Properties()));
        // Register the block and return holder
        return register(BuiltInRegistries.BLOCK, name, loc -> block);
    }

    @Override
    public Holder<BlockEntityType<?>> registerBlockEntityType(String name, Supplier<BlockEntityType.Builder<?>> builder) {
        return register(BuiltInRegistries.BLOCK_ENTITY_TYPE, name, loc -> builder.get().build(null));
    }

    @Override
    public <T> Holder<DataComponentType<T>> registerDataComponent(String name, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        DataComponentType<T> componentType = builderOperator.apply(DataComponentType.<T>builder()).build();
        ResourceKey<DataComponentType<?>> key = ResourceKey.create(BuiltInRegistries.DATA_COMPONENT_TYPE.key(), ResourceLocation.fromNamespaceAndPath(Fishtastic.MOD_ID, name));
        @SuppressWarnings("unchecked")
        Holder<DataComponentType<T>> holder = (Holder<DataComponentType<T>>) (Holder<?>) Registry.registerForHolder(BuiltInRegistries.DATA_COMPONENT_TYPE, (ResourceKey<DataComponentType<?>>) key, componentType);
        return holder;
    }

    private static <T> Holder<T> register(Registry<T> registry, String name, Function<ResourceLocation, ? extends T> func) {
        T entry = func.apply(ResourceLocation.fromNamespaceAndPath(Fishtastic.MOD_ID, name));
        ResourceKey<T> entryKey = ResourceKey.create(registry.key(), ResourceLocation.fromNamespaceAndPath(Fishtastic.MOD_ID, name));
        return Registry.registerForHolder(registry, entryKey, entry);
    }

    // ----- Registries ----- //

    @Override
    public Registry<FishTankFrameType> fishTankFrameTypes() {
        try {
            throw new OperationNotSupportedException("TODO: Implement FishTankFrameType registry for Fabric");
        } catch (OperationNotSupportedException e) {
            throw new RuntimeException(e);
        }
        // TODO: Implement FishTankFrameType registry for Fabric
    }

    // ----- Platform-specific BlockEntity Creation ----- //

    @Override
    public FishTankBlockEntity createFishTankBlockEntity(BlockPos pos, BlockState state) {
        // Fabric uses the common implementation directly
        return new FishTankBlockEntity(pos, state);
    }

    @Override
    public void requestModelDataUpdate(BlockEntity blockEntity) {
        // Fabric may handle model data updates differently - implement if needed
        // For now, this is a no-op on Fabric
    }

    @Override
    public List<Block> getConfiguredFrameBlocks() {
        // TODO: Implement config reading for Fabric
        // For now, return empty list
        return new ArrayList<>();
    }
}

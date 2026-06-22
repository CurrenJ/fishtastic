package grill24.fishtastic.architectury.fabric;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.architectury.IRegistrationApi;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fabric.blockentity.FishTankBlockEntityFabric;
import grill24.fishtastic.fishtank.FishTankFrameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.naming.OperationNotSupportedException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.client.Minecraft;

public class FabricRegistrationApi implements IRegistrationApi {
    public static FabricRegistrationApi INSTANCE = new FabricRegistrationApi();

    public static FabricRegistrationApi getInstance() {
        return INSTANCE;
    }

    // ----- Registration Methods ----- //

    @Override
    public <I extends Item> Holder<Item> registerItem(final String name, final Function<Identifier, ? extends I> func) {
        return register(BuiltInRegistries.ITEM, name, func);
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(final String name, final Function<Identifier, ? extends I> func) {
        // Create the block
        Identifier id = Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, name);
        Block block = func.apply(id);
        // Register the BlockItem for the block (must set item ID on properties)
        registerItem(name, loc -> new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, loc))));
        // Register the block and return holder
        return register(BuiltInRegistries.BLOCK, name, loc -> block);
    }

    @Override
    public Holder<BlockEntityType<?>> registerBlockEntityType(String name, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory, Supplier<Block[]> validBlocksSupplier) {
        return register(BuiltInRegistries.BLOCK_ENTITY_TYPE, name, loc ->
                FabricBlockEntityTypeBuilder.create(factory::apply, validBlocksSupplier.get()).build()
        );
    }

    @Override
    public <T> Holder<DataComponentType<T>> registerDataComponent(String name, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        DataComponentType<T> componentType = builderOperator.apply(DataComponentType.<T>builder()).build();
        ResourceKey<DataComponentType<?>> key = ResourceKey.create(BuiltInRegistries.DATA_COMPONENT_TYPE.key(), Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, name));
        @SuppressWarnings("unchecked")
        Holder<DataComponentType<T>> holder = (Holder<DataComponentType<T>>) (Holder<?>) Registry.registerForHolder(BuiltInRegistries.DATA_COMPONENT_TYPE, (ResourceKey<DataComponentType<?>>) key, componentType);
        return holder;
    }

    @Override
    public Holder<CreativeModeTab> registerCreativeModeTab(String name, Function<Identifier, ? extends CreativeModeTab> func) {
        return register(BuiltInRegistries.CREATIVE_MODE_TAB, name, func);
    }

    @Override
    public Holder<SoundEvent> registerSoundEvent(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, name);
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    @Override
    public Holder<SimpleParticleType> registerParticleType(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, name);
        @SuppressWarnings("unchecked")
        Holder<SimpleParticleType> holder = (Holder<SimpleParticleType>) (Holder<?>)
                Registry.registerForHolder(BuiltInRegistries.PARTICLE_TYPE, id, new SimpleParticleType(false) {});
        return holder;
    }

    // ----- Registry Accessors ----- //

    @Override
    public Registry<Block> blocks() {
        return BuiltInRegistries.BLOCK;
    }

    @Override
    public Registry<Item> items() {
        return BuiltInRegistries.ITEM;
    }

    @Override
    public Registry<BlockEntityType<?>> blockEntityTypes() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE;
    }

    @Override
    public Registry<DataComponentType<?>> dataComponentTypes() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE;
    }

    @Override
    public Registry<CreativeModeTab> creativeModeTabs() {
        return BuiltInRegistries.CREATIVE_MODE_TAB;
    }

    private static <T> Holder<T> register(Registry<T> registry, String name, Function<Identifier, ? extends T> func) {
        T entry = func.apply(Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, name));
        ResourceKey<T> entryKey = ResourceKey.create(registry.key(), Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, name));
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
        return new FishTankBlockEntityFabric(pos, state);
    }

    @Override
    public void requestModelDataUpdate(BlockEntity blockEntity) {
        net.minecraft.world.level.Level level = blockEntity.getLevel();
        if (level == null || !level.isClientSide()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            BlockPos pos = blockEntity.getBlockPos();
            mc.execute(() -> mc.levelRenderer.setBlocksDirty(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ()
            ));
        }
    }

    @Override
    public boolean isBlockBlacklisted(Block block, String partName) {
        // TODO: Implement blacklist checking for Fabric when config is added
        return false;
    }
}

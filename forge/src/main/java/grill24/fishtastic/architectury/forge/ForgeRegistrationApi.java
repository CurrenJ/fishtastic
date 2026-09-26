package grill24.fishtastic.architectury.forge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.architectury.IRegistrationApi;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankFrameType;
import grill24.fishtastic.forge.FishtasticRegistriesForge;
import grill24.fishtastic.forge.blockentity.FishTankBlockEntityForge;
import grill24.fishtastic.forge.fishtank.FishTankPartBlacklistChecker;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class ForgeRegistrationApi implements IRegistrationApi {
    public static final ForgeRegistrationApi INSTANCE = new ForgeRegistrationApi();

    // ----- Registration Methods ----- //

    @Override
    public <I extends Item> Holder<Item> registerItem(final String name, final Function<ResourceLocation, ? extends I> func) {
        ResourceLocation id = Ids.of(Fishtastic.MOD_ID, name);
        Item entry = func.apply(id);
        RegistryObject<Item> ro = FishtasticRegistriesForge.ITEMS.register(name, () -> entry);
        return new RegistryObjectHolder<>(ro);
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(final String name, final Function<ResourceLocation, ? extends I> func) {
        return registerBlock(name, func, (block, loc) -> new BlockItem(block, new Item.Properties()));
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(final String name, final Function<ResourceLocation, ? extends I> blockFunc, final BiFunction<Block, ResourceLocation, ? extends BlockItem> itemFunc) {
        ResourceLocation id = Ids.of(Fishtastic.MOD_ID, name);
        Block block = blockFunc.apply(id);
        RegistryObject<Block> blockRo = FishtasticRegistriesForge.BLOCKS.register(name, () -> block);
        registerItem(name, loc -> itemFunc.apply(block, loc));
        return new RegistryObjectHolder<>(blockRo);
    }

    @Override
    public Holder<BlockEntityType<?>> registerBlockEntityType(String name, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory, Supplier<Block[]> validBlocksSupplier) {
        RegistryObject<BlockEntityType<?>> ro = FishtasticRegistriesForge.BLOCK_ENTITY_TYPES.register(name, () ->
                BlockEntityType.Builder.<BlockEntity>of(factory::apply, validBlocksSupplier.get()).build(null)
        );
        return new RegistryObjectHolder<>(ro);
    }

    @Override
    public Holder<CreativeModeTab> registerCreativeModeTab(String name, Function<ResourceLocation, ? extends CreativeModeTab> func) {
        ResourceLocation id = Ids.of(Fishtastic.MOD_ID, name);
        CreativeModeTab entry = func.apply(id);
        RegistryObject<CreativeModeTab> ro = FishtasticRegistriesForge.CREATIVE_MODE_TABS.register(name, () -> entry);
        return new RegistryObjectHolder<>(ro);
    }

    @Override
    public Holder<SoundEvent> registerSoundEvent(String name) {
        RegistryObject<SoundEvent> ro = FishtasticRegistriesForge.SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(Ids.of(Fishtastic.MOD_ID, name)));
        return new RegistryObjectHolder<>(ro);
    }

    @Override
    public Holder<SimpleParticleType> registerParticleType(String name) {
        RegistryObject<net.minecraft.core.particles.ParticleType<?>> ro =
                FishtasticRegistriesForge.PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false) {});
        @SuppressWarnings("unchecked")
        Holder<SimpleParticleType> holder = (Holder<SimpleParticleType>) (Holder<?>) new RegistryObjectHolder<>(ro);
        return holder;
    }

    @Override
    public <M extends AbstractContainerMenu> Holder<MenuType<?>> registerMenuType(String name, MenuFactory<M> factory) {
        RegistryObject<MenuType<?>> ro = FishtasticRegistriesForge.MENU_TYPES.register(name,
                () -> new MenuType<>(factory::create, FeatureFlags.VANILLA_SET));
        return new RegistryObjectHolder<>(ro);
    }

    @Override
    public <T extends Recipe<?>> Holder<RecipeSerializer<?>> registerRecipeSerializer(String name, Supplier<RecipeSerializer<T>> supplier) {
        RegistryObject<RecipeSerializer<?>> ro = FishtasticRegistriesForge.RECIPE_SERIALIZERS.register(name, supplier::get);
        return new RegistryObjectHolder<>(ro);
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
    public Registry<CreativeModeTab> creativeModeTabs() {
        return BuiltInRegistries.CREATIVE_MODE_TAB;
    }

    // ----- Registries ----- //

    @Override
    public Registry<FishTankFrameType> fishTankFrameTypes() {
        // TODO: Forge 47's registries.RegistryBuilder produces an IForgeRegistry<T>, not a vanilla
        // Registry<T> (unlike NeoForge's own RegistryBuilder, which does) - needs its own adapter.
        // Left unimplemented like Fabric's stub (FabricRegistrationApi#fishTankFrameTypes) pending
        // that follow-up; nothing in the currently-ported common/Forge code calls this yet.
        throw new UnsupportedOperationException("TODO: Implement FishTankFrameType registry for Forge");
    }

    // ----- Platform-specific BlockEntity Creation ----- //

    @Override
    public FishTankBlockEntity createFishTankBlockEntity(BlockPos pos, BlockState state) {
        return new FishTankBlockEntityForge(pos, state);
    }

    @Override
    public void requestModelDataUpdate(BlockEntity blockEntity) {
        if (blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide()) {
            blockEntity.requestModelDataUpdate();
        }
    }

    @Override
    public boolean isBlockBlacklisted(Block block, String partName) {
        FishTankPartBlacklistChecker.FishTankPart part;
        try {
            part = FishTankPartBlacklistChecker.FishTankPart.valueOf(partName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false; // Unknown part type, don't blacklist
        }

        return FishTankPartBlacklistChecker.isBlacklisted(block, part);
    }
}

package grill24.fishtastic.architectury.forge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.architectury.IRegistrationApi;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankFrameType;
import grill24.fishtastic.forge.blockentity.FishTankBlockEntityForge;
import grill24.fishtastic.forge.fishtank.FishTankPartBlacklistChecker;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class ForgeRegistrationApi implements IRegistrationApi {
    public static final ForgeRegistrationApi INSTANCE = new ForgeRegistrationApi();

    /**
     * The RegisterEvent currently being handled. Forge 47 unfreezes a registry only while its own
     * RegisterEvent runs, so each common {@code register*()} is called from inside the event for its
     * registry (see {@code FishtasticForge}) and registers straight into it. That hands common code
     * the registry's real {@link Holder.Reference}, exactly as on Fabric — a wrapper around a
     * {@code RegistryObject} would not do: 1.20.1's {@code ItemStack.is(Holder)} and
     * {@code BlockState.is(Holder)} compare holders by identity.
     */
    private RegisterEvent currentEvent;

    /** BlockItems from registerBlock, registered once the ITEM registry's own event fires. */
    private final List<Runnable> pendingBlockItems = new ArrayList<>();

    private ForgeRegistrationApi() {}

    /** Runs {@code registration} with {@code event} as the target of every register call it makes. */
    public void registerDuring(RegisterEvent event, Runnable registration) {
        currentEvent = event;
        try {
            registration.run();
        } finally {
            currentEvent = null;
        }
    }

    /** Registers the BlockItems queued by registerBlock; call from inside the ITEM RegisterEvent. */
    public void registerPendingBlockItems() {
        pendingBlockItems.forEach(Runnable::run);
        pendingBlockItems.clear();
    }

    private <T> Holder<T> register(ResourceKey<? extends Registry<T>> registryKey, String name, Function<ResourceLocation, ? extends T> func) {
        RegisterEvent event = currentEvent;
        if (event == null || !event.getRegistryKey().equals(registryKey)) {
            // RegisterEvent.register silently ignores a mismatched key, so fail loudly instead.
            throw new IllegalStateException("fishtastic:" + name + " must be registered during the RegisterEvent for "
                    + registryKey.location() + ", not " + (event == null ? "outside one" : event.getRegistryKey().location()));
        }
        ResourceLocation id = Ids.of(Fishtastic.MOD_ID, name);
        T entry = func.apply(id);
        event.register(registryKey, id, () -> entry);
        Registry<T> registry = event.getVanillaRegistry();
        return registry.getHolderOrThrow(ResourceKey.create(registryKey, id));
    }

    // ----- Registration Methods ----- //

    @Override
    public <I extends Item> Holder<Item> registerItem(final String name, final Function<ResourceLocation, ? extends I> func) {
        return register(Registries.ITEM, name, func);
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(final String name, final Function<ResourceLocation, ? extends I> func) {
        return registerBlock(name, func, (block, loc) -> new BlockItem(block, new Item.Properties()));
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(final String name, final Function<ResourceLocation, ? extends I> blockFunc, final BiFunction<Block, ResourceLocation, ? extends BlockItem> itemFunc) {
        Holder<Block> block = register(Registries.BLOCK, name, blockFunc);
        pendingBlockItems.add(() -> registerItem(name, loc -> itemFunc.apply(block.value(), loc)));
        return block;
    }

    @Override
    public Holder<BlockEntityType<?>> registerBlockEntityType(String name, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory, Supplier<Block[]> validBlocksSupplier) {
        return register(Registries.BLOCK_ENTITY_TYPE, name, loc ->
                BlockEntityType.Builder.<BlockEntity>of(factory::apply, validBlocksSupplier.get()).build(null));
    }

    @Override
    public Holder<CreativeModeTab> registerCreativeModeTab(String name, Function<ResourceLocation, ? extends CreativeModeTab> func) {
        return register(Registries.CREATIVE_MODE_TAB, name, func);
    }

    @Override
    public Holder<SoundEvent> registerSoundEvent(String name) {
        return register(Registries.SOUND_EVENT, name, SoundEvent::createVariableRangeEvent);
    }

    @Override
    public Holder<SimpleParticleType> registerParticleType(String name) {
        @SuppressWarnings("unchecked")
        Holder<SimpleParticleType> holder = (Holder<SimpleParticleType>) (Holder<?>)
                register(Registries.PARTICLE_TYPE, name, loc -> new SimpleParticleType(false) {});
        return holder;
    }

    @Override
    public <M extends AbstractContainerMenu> Holder<MenuType<?>> registerMenuType(String name, MenuFactory<M> factory) {
        return register(Registries.MENU, name, loc -> new MenuType<>(factory::create, FeatureFlags.VANILLA_SET));
    }

    @Override
    public <T extends Recipe<?>> Holder<RecipeSerializer<?>> registerRecipeSerializer(String name, Supplier<RecipeSerializer<T>> supplier) {
        return register(Registries.RECIPE_SERIALIZER, name, loc -> supplier.get());
    }

    @Override
    public Holder<LootItemFunctionType> registerLootFunctionType(String name, Supplier<LootItemFunctionType> supplier) {
        return register(Registries.LOOT_FUNCTION_TYPE, name, loc -> supplier.get());
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

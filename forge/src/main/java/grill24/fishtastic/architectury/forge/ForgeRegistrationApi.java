package grill24.fishtastic.architectury.forge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.architectury.IRegistrationApi;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.forge.blockentity.FishTankBlockEntityForge;
import grill24.fishtastic.forge.fishtank.FishTankPartBlacklistChecker;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import net.minecraftforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ForgeRegistrationApi implements IRegistrationApi {
    public static final ForgeRegistrationApi INSTANCE = new ForgeRegistrationApi();

    private RegisterEvent currentEvent;
    private final List<Runnable> pendingBlockItems = new ArrayList<>();

    private ForgeRegistrationApi() {}

    public void registerDuring(RegisterEvent event, Runnable registration) {
        currentEvent = event;
        try {
            registration.run();
        } finally {
            currentEvent = null;
        }
    }

    public void registerPendingBlockItems() {
        pendingBlockItems.forEach(Runnable::run);
        pendingBlockItems.clear();
    }

    private <T> Holder<T> register(ResourceKey<? extends Registry<T>> registryKey, String name,
                                   Function<Identifier, ? extends T> func) {
        RegisterEvent event = currentEvent;
        if (event == null || !event.getRegistryKey().equals(registryKey)) {
            throw new IllegalStateException("fishtastic:" + name + " must be registered during the RegisterEvent for "
                    + registryKey.identifier() + ", not "
                    + (event == null ? "outside one" : event.getRegistryKey().identifier()));
        }
        Identifier id = Ids.of(Fishtastic.MOD_ID, name);
        T entry = func.apply(id);
        event.register(registryKey, id, () -> entry);
        @SuppressWarnings("unchecked")
        Registry<T> registry = (Registry<T>) event.getVanillaRegistry();
        return registry.get(id).orElseThrow();
    }

    @Override
    public <I extends Item> Holder<Item> registerItem(String name, Function<Identifier, ? extends I> func) {
        return register(Registries.ITEM, name, func);
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(String name, Function<Identifier, ? extends I> func) {
        return registerBlock(name, func,
                (block, loc) -> new BlockItem(block, new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, loc))));
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(
            String name, Function<Identifier, ? extends I> blockFunc,
            BiFunction<Block, Identifier, ? extends BlockItem> itemFunc) {
        Holder<Block> block = register(Registries.BLOCK, name, blockFunc);
        pendingBlockItems.add(() -> registerItem(name, loc -> itemFunc.apply(block.value(), loc)));
        return block;
    }

    @Override
    public Holder<BlockEntityType<?>> registerBlockEntityType(
            String name, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory,
            Supplier<Block[]> validBlocksSupplier) {
        return register(Registries.BLOCK_ENTITY_TYPE, name,
                loc -> new BlockEntityType<>(factory::apply, java.util.Set.of(validBlocksSupplier.get())));
    }

    @Override
    public <T> Holder<DataComponentType<T>> registerDataComponent(
            String name, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        @SuppressWarnings("unchecked")
        Holder<DataComponentType<T>> holder = (Holder<DataComponentType<T>>) (Holder<?>)
                register(Registries.DATA_COMPONENT_TYPE, name,
                        loc -> builderOperator.apply(DataComponentType.<T>builder()).build());
        return holder;
    }

    @Override
    public Holder<CreativeModeTab> registerCreativeModeTab(
            String name, Function<Identifier, ? extends CreativeModeTab> func) {
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
    public <M extends AbstractContainerMenu> Holder<MenuType<?>> registerMenuType(
            String name, MenuFactory<M> factory) {
        return register(Registries.MENU, name,
                loc -> new MenuType<>(factory::create, FeatureFlags.VANILLA_SET));
    }

    @Override
    public <T extends Recipe<?>> Holder<RecipeSerializer<?>> registerRecipeSerializer(
            String name, Supplier<RecipeSerializer<T>> supplier) {
        return register(Registries.RECIPE_SERIALIZER, name, loc -> supplier.get());
    }

    @Override public Registry<Block> blocks() { return BuiltInRegistries.BLOCK; }
    @Override public Registry<Item> items() { return BuiltInRegistries.ITEM; }
    @Override public Registry<BlockEntityType<?>> blockEntityTypes() { return BuiltInRegistries.BLOCK_ENTITY_TYPE; }
    @Override public Registry<DataComponentType<?>> dataComponentTypes() { return BuiltInRegistries.DATA_COMPONENT_TYPE; }
    @Override public Registry<CreativeModeTab> creativeModeTabs() { return BuiltInRegistries.CREATIVE_MODE_TAB; }

    @Override
    public FishTankBlockEntity createFishTankBlockEntity(BlockPos pos, BlockState state) {
        return new FishTankBlockEntityForge(pos, state);
    }

    @Override
    public void requestModelDataUpdate(BlockEntity blockEntity) {
        // F06 adds the Forge model-data invalidation hook.
    }

    @Override
    public boolean isBlockBlacklisted(Block block, String partName) {
        try {
            return FishTankPartBlacklistChecker.isBlacklisted(
                    block, FishTankPartBlacklistChecker.FishTankPart.valueOf(partName.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

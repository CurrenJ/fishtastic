package grill24.fishtastic.architectury.neoforge;

import com.electronwill.nightconfig.core.Config;
import grill24.fishtastic.architectury.IRegistrationApi;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankFrameType;
import grill24.fishtastic.neoforge.FishtasticConfig;
import grill24.fishtastic.neoforge.FishtasticRegistriesNeoForge;
import grill24.fishtastic.neoforge.blockentity.FishTankBlockEntityNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class NeoForgeRegistrationApi implements IRegistrationApi {
    public static final NeoForgeRegistrationApi INSTANCE = new NeoForgeRegistrationApi();

    // ----- Registration Methods ----- //

    @Override
    public <I extends Item> Holder<Item> registerItem(final String name, final Function<ResourceLocation, ? extends I> func) {
        return FishtasticRegistriesNeoForge.ITEMS.register(name, func);
    }

    @Override
    public <I extends Block> Holder<Block> registerBlock(final String name, final Function<ResourceLocation, ? extends I> func) {
        Holder<Block> blockHolder = FishtasticRegistriesNeoForge.BLOCKS.register(name, func);
        FishtasticRegistriesNeoForge.ITEMS.register(name, loc -> new BlockItem(blockHolder.value(), new Item.Properties()));
        return blockHolder;
    }

    @Override
    public Holder<BlockEntityType<?>> registerBlockEntityType(String name, Supplier<BlockEntityType.Builder<?>> builder) {
        return FishtasticRegistriesNeoForge.BLOCK_ENTITY_TYPES.register(name, () -> builder.get().build(null));
    }

    @Override
    public <T> Holder<DataComponentType<T>> registerDataComponent(String name, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        @SuppressWarnings("unchecked")
        Holder<DataComponentType<T>> holder = (Holder<DataComponentType<T>>) (Holder<?>) FishtasticRegistriesNeoForge.DATA_COMPONENT_TYPES.register(name, () -> builderOperator.apply(DataComponentType.<T>builder()).build());
        return holder;
    }

    // ----- Registries ----- //

    @Override
    public net.minecraft.core.Registry<FishTankFrameType> fishTankFrameTypes() {
        return FishtasticRegistriesNeoForge.FISH_TANK_FRAME_TYPE_REGISTRY;
    }

    // ----- Platform-specific BlockEntity Creation ----- //

    @Override
    public FishTankBlockEntity createFishTankBlockEntity(BlockPos pos, BlockState state) {
        return new FishTankBlockEntityNeoForge(pos, state);
    }

    @Override
    public void requestModelDataUpdate(BlockEntity blockEntity) {
        if (blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide) {
            blockEntity.requestModelDataUpdate();
        }
    }

    @Override
    public List<Block> getConfiguredFrameBlocks() {
        List<Block> blocks = new ArrayList<>();

        // Read configured tags from config
        for (Config entry : FishtasticConfig.STARTUP.customFishTankFrameTypes.get()) {
            if (entry.isEmpty())
                continue;

            String blocksStr = entry.get("blocks");
            if (blocksStr == null || !blocksStr.startsWith("#"))
                continue;

            try {
                var tagKey = TagKey.create(Registries.BLOCK, ResourceLocation.parse(blocksStr.substring(1)));
                var tagEntries = BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey);

                // Add all blocks from this tag
                tagEntries.forEach(holder -> blocks.add(holder.value()));
            } catch (Exception e) {
                // Skip invalid tags
            }
        }

        return blocks;
    }
}

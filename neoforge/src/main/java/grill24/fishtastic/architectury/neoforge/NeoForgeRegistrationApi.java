package grill24.fishtastic.architectury.neoforge;

import grill24.fishtastic.architectury.IRegistrationApi;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankFrameType;
import grill24.fishtastic.neoforge.FishtasticRegistriesNeoForge;
import grill24.fishtastic.neoforge.blockentity.FishTankBlockEntityNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

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
    public boolean isBlockBlacklisted(Block block, String partName) {
        // Convert part name to enum
        grill24.fishtastic.neoforge.fishtank.FishTankPartBlacklistChecker.FishTankPart part;
        try {
            part = grill24.fishtastic.neoforge.fishtank.FishTankPartBlacklistChecker.FishTankPart.valueOf(partName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false; // Unknown part type, don't blacklist
        }

        return grill24.fishtastic.neoforge.fishtank.FishTankPartBlacklistChecker.isBlacklisted(block, part);
    }
}

package grill24.fishtastic.blockentity;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FishTankBlockEntity extends BlockEntity {
    public FishTankBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(FishtasticBlockEntityTypes.FISH_TANK.value(), blockPos, blockState);
    }

    // Store the frame block directly - can be any block
    private Block frameBlock = Blocks.OAK_PLANKS; // Default frame block

    /**
     * Get the frame block for this fish tank.
     */
    public Block getFrameBlock() {
        return frameBlock;
    }

    /**
     * Set the frame block for this fish tank.
     */
    public void setFrameBlock(Block block) {
        this.frameBlock = block;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("FrameBlock", BuiltInRegistries.BLOCK.getKey(frameBlock).toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("FrameBlock")) {
            ResourceLocation blockId = ResourceLocation.tryParse(tag.getString("FrameBlock"));
            if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
                frameBlock = BuiltInRegistries.BLOCK.get(blockId);
            } else {
                frameBlock = Blocks.OAK_PLANKS; // Graceful fallback
            }
        }

        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}

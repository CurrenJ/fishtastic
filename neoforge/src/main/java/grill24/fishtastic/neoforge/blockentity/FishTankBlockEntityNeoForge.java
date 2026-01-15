package grill24.fishtastic.neoforge.blockentity;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.neoforge.fishtank.FishTankModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

/**
 * NeoForge-specific extension of FishTankBlockEntity that provides ModelData for rendering.
 */
public class FishTankBlockEntityNeoForge extends FishTankBlockEntity {

    public FishTankBlockEntityNeoForge(BlockPos blockPos, BlockState blockState) {
        super(blockPos, blockState);
    }

    @Override
    @NotNull
    public ModelData getModelData() {
        return ModelData.builder()
                .with(FishTankModelData.DATA_PROPERTY, new FishTankModelData(getFrameBlock(), getSandBlock(), getGlassBlock(), getOpenFaces()))
                .build();
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        // Handle data packet on client - this will trigger model data update
        if (level != null && level.isClientSide) {
            // Request model data update
            requestModelDataUpdate();

            // Mark the chunk section dirty to force re-render
            var minecraft = Minecraft.getInstance();
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.setBlocksDirty(
                    getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(),
                    getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ()
                );
            }
        }
    }
}


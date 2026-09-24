package grill24.fishtastic.neoforge.blockentity;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
// PORT A5.2: import grill24.fishtastic.fishtank.FishTankCompositeModelData;
// PORT A5.2: import grill24.fishtastic.neoforge.fishtank.FishTankModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NeoForge-specific extension of FishTankBlockEntity that provides ModelData for rendering.
 */
public class FishTankBlockEntityNeoForge extends FishTankBlockEntity {

    public FishTankBlockEntityNeoForge(BlockPos blockPos, BlockState blockState) {
        super(blockPos, blockState);
    }

    // PORT A5.2: the tank model's ModelData (net.neoforged.neoforge.client.model.data.ModelData on 1.21.1).
//    @Override
//    @NotNull
//    public ModelData getModelData() {
//        FishTankCompositeModelData data = new FishTankCompositeModelData(getShape(), getFrameBlock(), getSandBlock(), getGlassBlock(), getOpenFaces(), getFilledDiagonals(), getFilledEdgeDiagonals());
//        return ModelData.builder()
//                .with(FishTankModelData.DATA_PROPERTY, data)
//                .build();
//    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);

        // Handle data packet on client - this will trigger model data update
        if (level != null && level.isClientSide()) {
            // Request model data update
            requestModelDataUpdate();

            // Mark the chunk section dirty to force re-render
            var minecraft = Minecraft.getInstance();
            if (minecraft.levelRenderer != null) {
                minecraft.levelRenderer.setBlocksDirty(
                    getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(),
                    getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ()
                );
            } else {
                Fishtastic.LOGGER.warn("[FishTankBENF.onDataPacket][CLIENT] pos={}, levelRenderer is NULL!", getBlockPos());
            }
        }
    }
}

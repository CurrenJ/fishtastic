package grill24.fishtastic.forge.blockentity;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.forge.fishtank.FishTankModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

/**
 * Forge-specific extension of FishTankBlockEntity that provides ModelData for rendering.
 */
public class FishTankBlockEntityForge extends FishTankBlockEntity {

    public FishTankBlockEntityForge(BlockPos blockPos, BlockState blockState) {
        super(blockPos, blockState);
    }

    @Override
    @NotNull
    public ModelData getModelData() {
        FishTankCompositeModelData data = new FishTankCompositeModelData(getShape(), getFrameBlock(), getSandBlock(), getGlassBlock(), getOpenFaces(), getFilledDiagonals(), getFilledEdgeDiagonals());
        return ModelData.builder()
                .with(FishTankModelData.DATA_PROPERTY, data)
                .build();
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        super.onDataPacket(net, packet);

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
                Fishtastic.LOGGER.warn("[FishTankBEForge.onDataPacket][CLIENT] pos={}, levelRenderer is NULL!", getBlockPos());
            }
        }
    }
}

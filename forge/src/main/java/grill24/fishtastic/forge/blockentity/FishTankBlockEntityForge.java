package grill24.fishtastic.forge.blockentity;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.forge.fishtank.FishTankModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

public class FishTankBlockEntityForge extends FishTankBlockEntity {
    public FishTankBlockEntityForge(BlockPos blockPos, BlockState blockState) {
        super(blockPos, blockState);
    }

    @Override
    @NotNull
    public ModelData getModelData() {
        FishTankCompositeModelData data = new FishTankCompositeModelData(getShape(), getFrameBlock(), getSandBlock(), getGlassBlock(), getOpenFaces(), getFilledDiagonals(), getFilledEdgeDiagonals(),
                TankCosmeticMesh.snapshot(this));
        return ModelData.builder()
                .with(FishTankModelData.DATA_PROPERTY, data)
                .build();
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
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

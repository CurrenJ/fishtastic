package grill24.fishtastic.neoforge.blockentity;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.neoforge.fishtank.FishTankModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.model.data.ModelData;
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
        FishTankModelData data = new FishTankModelData(getFrameBlock(), getSandBlock(), getGlassBlock(), getOpenFaces());
        String side = (getLevel() != null && getLevel().isClientSide()) ? "CLIENT" : "SERVER";
        Fishtastic.LOGGER.info("[FishTankBENF.getModelData][{}] pos={}, frame={}, sand={}, glass={}, openFaces={}, permIdx={}",
                side, getBlockPos(),
                BuiltInRegistries.BLOCK.getKey(data.frameBlock()),
                BuiltInRegistries.BLOCK.getKey(data.sandBlock()),
                BuiltInRegistries.BLOCK.getKey(data.glassBlock()),
                data.openFaces(),
                data.getPermutationIndex());
        return ModelData.builder()
                .with(FishTankModelData.DATA_PROPERTY, data)
                .build();
    }

    @Override
    public void onDataPacket(Connection net, ValueInput valueInput) {
        Fishtastic.LOGGER.info("[FishTankBENF.onDataPacket] pos={}, BEFORE: frame={}, sand={}, glass={}",
                getBlockPos(),
                BuiltInRegistries.BLOCK.getKey(getFrameBlock()),
                BuiltInRegistries.BLOCK.getKey(getSandBlock()),
                BuiltInRegistries.BLOCK.getKey(getGlassBlock()));

        super.onDataPacket(net, valueInput);

        Fishtastic.LOGGER.info("[FishTankBENF.onDataPacket] pos={}, AFTER: frame={}, sand={}, glass={}",
                getBlockPos(),
                BuiltInRegistries.BLOCK.getKey(getFrameBlock()),
                BuiltInRegistries.BLOCK.getKey(getSandBlock()),
                BuiltInRegistries.BLOCK.getKey(getGlassBlock()));

        // Handle data packet on client - this will trigger model data update
        if (level != null && level.isClientSide()) {
            Fishtastic.LOGGER.info("[FishTankBENF.onDataPacket][CLIENT] pos={}, requesting model data update and marking chunk dirty", getBlockPos());
            // Request model data update
            requestModelDataUpdate();

            // Mark the chunk section dirty to force re-render
            var minecraft = Minecraft.getInstance();
            if (minecraft.levelRenderer != null) {
                Fishtastic.LOGGER.info("[FishTankBENF.onDataPacket][CLIENT] pos={}, calling setBlocksDirty", getBlockPos());
                minecraft.levelRenderer.setBlocksDirty(
                    getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(),
                    getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ()
                );
            } else {
                Fishtastic.LOGGER.warn("[FishTankBENF.onDataPacket][CLIENT] pos={}, levelRenderer is NULL!", getBlockPos());
            }
        } else {
            Fishtastic.LOGGER.info("[FishTankBENF.onDataPacket] pos={}, NOT on client side (level={}, isClientSide={})",
                    getBlockPos(), level != null, level != null && level.isClientSide());
        }
    }
}


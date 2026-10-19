package grill24.fishtastic.forge.blockentity;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.forge.fishtank.FishTankModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public class FishTankBlockEntityForge extends FishTankBlockEntity {
    public FishTankBlockEntityForge(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public ModelData getModelData() {
        FishTankCompositeModelData data = new FishTankCompositeModelData(
                getShape(), getFrameBlock(), getSandBlock(), getGlassBlock(),
                getOpenFaces(), getFilledDiagonals(), getFilledEdgeDiagonals(),
                TankCosmeticMesh.snapshot(this));
        return ModelData.builder().with(FishTankModelData.DATA_PROPERTY, data).build();
    }
}

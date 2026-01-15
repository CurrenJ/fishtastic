package grill24.fishtastic.neoforge.blockentity;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.neoforge.fishtank.FishTankModelData;
import net.minecraft.core.BlockPos;
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
                .with(FishTankModelData.DATA_PROPERTY, new FishTankModelData(getFrameBlock(), getSandBlock()))
                .build();
    }
}


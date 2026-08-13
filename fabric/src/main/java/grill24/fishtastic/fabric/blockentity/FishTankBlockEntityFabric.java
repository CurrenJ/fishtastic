package grill24.fishtastic.fabric.blockentity;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric-specific subclass of {@link FishTankBlockEntity} that provides a thread-safe
 * snapshot of model customization data for chunk meshing via Fabric's render data API.
 *
 * <p>{@link #getRenderData()} is called on the main thread when the render region is built;
 * the returned {@link FishTankCompositeModelData} is then returned by
 * {@code FabricBlockGetter.getBlockEntityRenderData()} during background chunk meshing,
 * avoiding race conditions on the live block entity state.
 */
public class FishTankBlockEntityFabric extends FishTankBlockEntity {

    public FishTankBlockEntityFabric(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    // Overrides the default RenderDataBlockEntity.getRenderData() (interface-injected by Fabric API).
    // Returns an immutable snapshot safe for background-thread chunk meshing.
    public Object getRenderData() {
        return new FishTankCompositeModelData(getShape(), getFrameBlock(), getSandBlock(), getGlassBlock(), getOpenFaces());
    }
}

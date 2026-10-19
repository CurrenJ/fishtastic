package grill24.fishtastic.forge.fishtank;

import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import net.minecraftforge.client.model.data.ModelProperty;

import java.util.List;

/**
 * Holds the Forge {@link ModelProperty} keys used to attach tank model data to a block position's
 * {@code ModelData}.
 */
public final class FishTankModelData {
    public static final ModelProperty<FishTankCompositeModelData> DATA_PROPERTY = new ModelProperty<>();
    public static final ModelProperty<List<TankCosmeticMesh.Quad>> TINTED_COSMETICS_PROPERTY = new ModelProperty<>();

    private FishTankModelData() {}
}

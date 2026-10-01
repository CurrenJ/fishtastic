package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.List;

/**
 * Holds the NeoForge {@link ModelProperty} key used to attach {@link FishTankCompositeModelData}
 * to a block position's {@code ModelData}.
 */
public final class FishTankModelData {
    public static final ModelProperty<FishTankCompositeModelData> DATA_PROPERTY = new ModelProperty<>();
    /**
     * PORT-ONLY: the tank's tinted cosmetic quads with their colour baked in for this position, set by
     * {@code FishTankBakedModel.getModelData} on the meshing thread (26.1.2 colours them in
     * {@code collectParts}, which has the level; 1.21.1's {@code getQuads} doesn't).
     */
    public static final ModelProperty<List<TankCosmeticMesh.Quad>> TINTED_COSMETICS_PROPERTY = new ModelProperty<>();

    private FishTankModelData() {}
}

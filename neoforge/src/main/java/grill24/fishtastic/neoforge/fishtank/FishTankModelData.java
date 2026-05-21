package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import net.neoforged.neoforge.model.data.ModelProperty;

/**
 * Holds the NeoForge {@link ModelProperty} key used to attach {@link FishTankCompositeModelData}
 * to a block position's {@code ModelData}.
 */
public final class FishTankModelData {
    public static final ModelProperty<FishTankCompositeModelData> DATA_PROPERTY = new ModelProperty<>();

    private FishTankModelData() {}
}

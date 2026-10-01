package grill24.fishtastic.forge.fishtank;

import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraftforge.client.model.data.ModelProperty;

import java.util.List;

/**
 * Holds the Forge {@link ModelProperty} key used to attach {@link FishTankCompositeModelData}
 * to a block position's {@code ModelData}.
 */
public final class FishTankModelData {
    public static final ModelProperty<FishTankCompositeModelData> DATA_PROPERTY = new ModelProperty<>();
    /**
     * PORT-ONLY: the tank's tinted cosmetic quads, coloured for this position at mesh time
     * ({@code FishTankBakedModel#getModelData}). Forge 47's {@code getQuads} has no level or
     * position, so the per-mesh tint 26.1.2 resolves inside its quad collection rides here.
     */
    public static final ModelProperty<List<BakedQuad>> COSMETIC_TINTED_PROPERTY = new ModelProperty<>();

    private FishTankModelData() {}
}

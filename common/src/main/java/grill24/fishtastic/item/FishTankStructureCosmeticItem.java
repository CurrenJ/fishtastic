package grill24.fishtastic.item;

import grill24.fishtastic.fishtank.CosmeticStructure;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * An item that places a multi-block {@link CosmeticStructure} decoration inside a fish tank.
 * Parallel to {@link FishTankCosmeticItem}, but references a structure definition instead of a
 * single block.
 */
public class FishTankStructureCosmeticItem extends Item {

    private static final Map<ResourceKey<CosmeticStructure>, FishTankStructureCosmeticItem> BY_STRUCTURE = new HashMap<>();

    private final ResourceKey<CosmeticStructure> structureId;

    public FishTankStructureCosmeticItem(ResourceKey<CosmeticStructure> structureId, Properties properties) {
        super(properties);
        this.structureId = structureId;
        BY_STRUCTURE.put(structureId, this);
    }

    public ResourceKey<CosmeticStructure> getStructureId() {
        return structureId;
    }

    @Nullable
    public static FishTankStructureCosmeticItem forStructure(ResourceKey<CosmeticStructure> structureId) {
        return BY_STRUCTURE.get(structureId);
    }
}

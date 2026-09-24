package grill24.fishtastic.fabric.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;

/**
 * Fabric implementation of the data generator entrypoint.
 * All common data generation is done in Fabric, just because we need to pick one modloader to run it on.
 * No point in adding data generation support for both platforms, when the resulting data assets should be identical.
 */
public class FishtasticDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(FishtasticModelProvider::new);
        pack.addProvider(FishTankFrameModelProvider::new);
        pack.addProvider(FishTankSandModelProvider::new);
        pack.addProvider(FishTankGlassModelProvider::new);
        pack.addProvider(FishtasticItemTagProvider::new);
        pack.addProvider(FishtasticBlockTagProvider::new);
        pack.addProvider(ItemEffectProvider::new);
        pack.addProvider(CosmeticStructureProvider::new);
        pack.addProvider(QuestProvider::new);
        pack.addProvider(ShopEntryFromQuestProvider::new);
        pack.addProvider(FishtasticRecipeProvider::new);
        pack.addProvider(FishtasticBlockLootTableProvider::new);
    }

    /**
     * 26.1's {@code createRegistryElementsPathProvider}: {@code data/<ns>/<ns>/<path>/}. On 1.21.1 vanilla
     * datagen leaves out the namespace directory, but Fabric API and NeoForge read modded registries from it.
     */
    static PackOutput.PathProvider registryElementsPathProvider(FabricDataOutput output, ResourceKey<? extends Registry<?>> key) {
        return output.createPathProvider(PackOutput.Target.DATA_PACK, key.location().getNamespace() + "/" + key.location().getPath());
    }
}

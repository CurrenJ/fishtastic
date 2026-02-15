package grill24.fishtastic.fabric.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

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
        pack.addProvider(ItemEffectProvider::new);
    }
}

package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;

import java.util.concurrent.CompletableFuture;

/**
 * Generates block tags for Fishtastic blocks.
 *
 * <ul>
 *   <li>Fish Tank – mineable with axe and pickaxe</li>
 * </ul>
 */
public class FishtasticBlockTagProvider extends FabricTagsProvider.BlockTagsProvider {

    public FishtasticBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        valueLookupBuilder(BlockTags.MINEABLE_WITH_AXE)
                .add(FishtasticBlocks.FISH_TANK.value());

        valueLookupBuilder(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(FishtasticBlocks.FISH_TANK.value());
    }
}

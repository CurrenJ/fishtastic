package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
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
public class FishtasticBlockTagProvider extends FabricTagProvider.BlockTagProvider {

    public FishtasticBlockTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_AXE)
                .add(FishtasticBlocks.FISH_TANK.value());

        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(FishtasticBlocks.FISH_TANK.value());
    }
}

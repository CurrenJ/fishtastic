package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * Fabric implementation of the item tag data provider.
 * Generates item tags for Fishtastic items.
 */
public class FishtasticItemTagProvider extends FabricTagsProvider.ItemTagsProvider {
    public FishtasticItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Fishing rods tag
        valueLookupBuilder(FishtasticItemTags.FISHING_RODS)
                .add(FishtasticItems.COPPER_FISHING_ROD.value());

        // Fish tag
        valueLookupBuilder(FishtasticItemTags.FISH)
                .add(FishtasticItems.BLUEGILL.value())
                .add(FishtasticItems.GARDEN_EEL.value())
                .add(FishtasticItems.GIANT_MANTA_RAY.value())
                .add(FishtasticItems.LIZARDFISH.value())
                .add(FishtasticItems.LONGNOSE_GAR.value())
                .add(FishtasticItems.MOORISH_IDOL.value())
                .add(FishtasticItems.NEON_TETRA.value())
                .add(FishtasticItems.NORTHERN_PIKE.value())
                .add(FishtasticItems.OCEAN_SUNFISH.value())
                .add(FishtasticItems.PARROTFISH.value())
                .add(FishtasticItems.PORTUGUESE_MAN_O_WAR.value())
                .add(FishtasticItems.RAINFORDIA.value())
                .add(FishtasticItems.ROYAL_GARDEN_EEL.value())
                .add(FishtasticItems.BLAZED_GRUB.value())
                .add(FishtasticItems.FROZEN_GIANT_MANTA_RAY.value())
                .add(FishtasticItems.MOLTEN_MOORISH_IDOL.value())
                .add(FishtasticItems.STARFISH.value())
                .add(FishtasticItems.SHRIMP.value())
                .add(FishtasticItems.GLASS_SQUID.value())
                .add(FishtasticItems.LEAFY_SEA_DRAGON.value());

        // Fishing bait
        valueLookupBuilder(FishtasticItemTags.FISHING_BAIT)
                .add(FishtasticItems.WORMS.value())
                .add(FishtasticItems.GUMMY_WORMS.value())
                .add(FishtasticItems.BLAZED_GRUB.value())
                .add(FishtasticItems.FRESHWATER_BAIT.value())
                .add(FishtasticItems.OCEAN_BAIT.value())
                .add(FishtasticItems.PREDATOR_BAIT.value())
                .add(FishtasticItems.DEEP_SEA_BAIT.value());

        valueLookupBuilder(ItemTags.FISHING_ENCHANTABLE)
                .addTag(FishtasticItemTags.FISHING_RODS);

        valueLookupBuilder(ItemTags.FISHES)
                .addTag(FishtasticItemTags.FISH);

        valueLookupBuilder(ItemTags.DURABILITY_ENCHANTABLE)
                .addTag(FishtasticItemTags.FISHING_RODS);

        // Exotic fish: eligible when using blazed grub bait
        valueLookupBuilder(FishtasticItemTags.EXOTIC_FISH)
                .add(FishtasticItems.GIANT_MANTA_RAY.value())
                .add(FishtasticItems.FROZEN_GIANT_MANTA_RAY.value())
                .add(FishtasticItems.LONGNOSE_GAR.value())
                .add(FishtasticItems.MOLTEN_MOORISH_IDOL.value())
                .add(FishtasticItems.NORTHERN_PIKE.value())
                .add(FishtasticItems.OCEAN_SUNFISH.value())
                .add(FishtasticItems.PORTUGUESE_MAN_O_WAR.value())
                .add(FishtasticItems.GLASS_SQUID.value())
                .add(FishtasticItems.LEAFY_SEA_DRAGON.value());
    }
}

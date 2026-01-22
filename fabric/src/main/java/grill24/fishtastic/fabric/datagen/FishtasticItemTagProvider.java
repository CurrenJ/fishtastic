package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * Fabric implementation of the item tag data provider.
 * Generates item tags for Fishtastic items.
 */
public class FishtasticItemTagProvider extends FabricTagProvider.ItemTagProvider {
    public FishtasticItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Fishing rods tag
        getOrCreateTagBuilder(FishtasticItemTags.FISHING_RODS)
                .add(FishtasticItems.COPPER_FISHING_ROD.value());

        // Fish tag
        getOrCreateTagBuilder(FishtasticItemTags.FISH)
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
                .add(FishtasticItems.SHRIMP.value());

        // Fishing bait
        getOrCreateTagBuilder(FishtasticItemTags.FISHING_BAIT)
                .add(FishtasticItems.WORMS.value())
                .add(FishtasticItems.GUMMY_WORMS.value())
                .add(FishtasticItems.BLAZED_GRUB.value());

        getOrCreateTagBuilder(ItemTags.FISHING_ENCHANTABLE)
                .addTag(FishtasticItemTags.FISHING_RODS);

        getOrCreateTagBuilder(ItemTags.FISHES)
                .addTag(FishtasticItemTags.FISH);

        getOrCreateTagBuilder(ItemTags.DURABILITY_ENCHANTABLE)
                .addTag(FishtasticItemTags.FISHING_RODS);
    }
}

package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;

import java.util.concurrent.CompletableFuture;

/**
 * Generates block loot tables for all Fishtastic blocks.
 *
 * <ul>
 *   <li>Fish Tank – drops itself when broken</li>
 *   <li>Borderless Stained Glass (×16) – drops itself when broken</li>
 *   <li>Clear Stained Glass (×16) – drops itself when broken</li>
 * </ul>
 */
public class FishtasticBlockLootTableProvider extends FabricBlockLootSubProvider {

    public FishtasticBlockLootTableProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generate() {
        // Fish Tank: drops itself
        dropSelf(FishtasticBlocks.FISH_TANK.value());

        // Glass variants: drop themselves (decorative blocks should always return on break)
        for (DyeColor color : DyeColor.values()) {
            dropSelf(FishtasticBlocks.BORDERLESS_STAINED_GLASS.get(color).value());
            dropSelf(FishtasticBlocks.CLEAR_STAINED_GLASS.get(color).value());
        }
    }
}

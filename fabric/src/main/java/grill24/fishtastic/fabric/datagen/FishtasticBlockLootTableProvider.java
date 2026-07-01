package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

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
        // Fish Tank: drops itself with the same frame/sand/glass materials it was built from.
        this.add(FishtasticBlocks.FISH_TANK.value(), createFishTankDrop(FishtasticBlocks.FISH_TANK.value()));

        dropSelf(FishtasticBlocks.FISH_TANK_ASSEMBLY.value());

        // Glass variants: drop themselves (decorative blocks should always return on break)
        for (DyeColor color : DyeColor.values()) {
            dropSelf(FishtasticBlocks.BORDERLESS_STAINED_GLASS.get(color).value());
            dropSelf(FishtasticBlocks.CLEAR_STAINED_GLASS.get(color).value());
        }
    }

    private LootTable.Builder createFishTankDrop(Block block) {
        return LootTable.lootTable()
                .withPool(
                        this.applyExplosionCondition(
                                block,
                                LootPool.lootPool()
                                        .setRolls(ConstantValue.exactly(1.0F))
                                        .add(
                                                LootItem.lootTableItem(block)
                                                        .apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                                                                .include(FishtasticDataComponents.FISH_TANK_MATERIALS.value()))
                                        )
                        )
                );
    }
}

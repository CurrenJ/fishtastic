package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticCreativeTabs;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side game tests for FishtasticCreativeTabs — verifies the fish tank cosmetics
 * (lamp/fence-arch variants, treasure chest, sea lantern) live exclusively in the
 * decorations tab and were actually removed from the main tab when it was split out.
 */
public final class CreativeTabGameTests {

    private CreativeTabGameTests() {}

    private static Collection<ItemStack> displayItemsOf(Holder<CreativeModeTab> tabHolder, GameTestHelper helper) {
        CreativeModeTab tab = tabHolder.value();
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
            FeatureFlags.REGISTRY.allFlags(), true, helper.getLevel().registryAccess());
        tab.buildContents(parameters);
        return tab.getDisplayItems();
    }

    public static void decorationsTabContainsExactlyCosmeticStructuresAndDecorations(GameTestHelper helper) {
        Set<Item> actual = displayItemsOf(FishtasticCreativeTabs.FISHTASTIC_DECORATIONS_TAB, helper).stream()
            .map(ItemStack::getItem)
            .collect(Collectors.toSet());

        Set<Item> expected = new HashSet<>();
        expected.add(FishtasticItems.COSMETIC_TREASURE_CHEST.value());
        expected.add(FishtasticItems.COSMETIC_SEA_LANTERN.value());
        FishtasticItems.COSMETIC_LAMP.values().forEach(holder -> expected.add(holder.value()));
        FishtasticItems.COSMETIC_FENCE_ARCH.values().forEach(holder -> expected.add(holder.value()));

        helper.assertTrue(actual.equals(expected),
            "Decorations tab must contain exactly the cosmetic structure + decoration items, expected "
                + expected.size() + " got " + actual.size());
        helper.succeed();
    }

    public static void mainTabNoLongerContainsMovedCosmetics(GameTestHelper helper) {
        Set<Item> actual = displayItemsOf(FishtasticCreativeTabs.FISHTASTIC_TAB, helper).stream()
            .map(ItemStack::getItem)
            .collect(Collectors.toSet());

        helper.assertTrue(!actual.contains(FishtasticItems.COSMETIC_TREASURE_CHEST.value()),
            "Main tab must not contain the treasure chest cosmetic (moved to the decorations tab)");
        helper.assertTrue(!actual.contains(FishtasticItems.COSMETIC_SEA_LANTERN.value()),
            "Main tab must not contain the sea lantern cosmetic (moved to the decorations tab)");
        helper.succeed();
    }
}

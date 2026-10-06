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
 * (lamp/fence-arch variants, treasure chest) live exclusively in the
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
        expected.add(FishtasticItems.COSMETIC_LIT_CAMPFIRE.value());
        expected.add(FishtasticItems.COSMETIC_MOSSY_BOULDER.value());
        expected.add(FishtasticItems.COSMETIC_PETALS.value());
        expected.add(FishtasticItems.COSMETIC_SPRUCE_GAZEBO.value());
        expected.add(FishtasticItems.COSMETIC_CATERPILLER.value());
        expected.add(FishtasticItems.COSMETIC_CORAL_REEF_1.value());
        expected.add(FishtasticItems.COSMETIC_CORAL_REEF_2.value());
        expected.add(FishtasticItems.COSMETIC_OAK_TREE.value());
        expected.add(FishtasticItems.COSMETIC_BIRCH_TREE.value());
        expected.add(FishtasticItems.COSMETIC_DYNAMIC_DUO.value());
        expected.add(FishtasticItems.COSMETIC_VINTAGE_VINE.value());
        expected.add(FishtasticItems.COSMETIC_CASTLE_RUIN.value());
        expected.add(FishtasticItems.COSMETIC_DROWNED_PAGODA.value());
        expected.add(FishtasticItems.COSMETIC_WHALE_FALL.value());
        expected.add(FishtasticItems.COSMETIC_WAX_SKULL_CANDLE.value());
        expected.add(FishtasticItems.COSMETIC_LIGHTHOUSE.value());
        expected.add(FishtasticItems.COSMETIC_TORII_GATE.value());
        expected.add(FishtasticItems.COSMETIC_STONE_LANTERN.value());
        expected.add(FishtasticItems.COSMETIC_FALLEN_COLUMN.value());
        expected.add(FishtasticItems.COSMETIC_SUNKEN_ANCHOR.value());
        expected.add(FishtasticItems.COSMETIC_GIANT_CLAM.value());
        expected.add(FishtasticItems.COSMETIC_AMETHYST_GEODE.value());
        expected.add(FishtasticItems.COSMETIC_HOLLOW_LOG.value());
        expected.add(FishtasticItems.COSMETIC_CLAY_PIPE.value());
        expected.add(FishtasticItems.COSMETIC_AMPHORA.value());
        expected.add(FishtasticItems.COSMETIC_DROWNED_BELL.value());
        expected.add(FishtasticItems.COSMETIC_MANGROVE_KNEES.value());
        expected.add(FishtasticItems.COSMETIC_BASALT_GROTTO.value());
        expected.add(FishtasticItems.COSMETIC_MOON_GATE.value());
        expected.add(FishtasticItems.COSMETIC_LEVIATHANS_SEAT.value());
        expected.add(FishtasticItems.COSMETIC_SEA_ARCH.value());
        expected.add(FishtasticItems.COSMETIC_SUNKEN_ZIGGURAT.value());
        expected.add(FishtasticItems.COSMETIC_CAPSIZED_GALLEON.value());
        expected.add(FishtasticItems.COSMETIC_DROWNED_CATHEDRAL.value());
        expected.add(FishtasticItems.COSMETIC_CORAL_WARREN.value());
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
        helper.succeed();
    }
}

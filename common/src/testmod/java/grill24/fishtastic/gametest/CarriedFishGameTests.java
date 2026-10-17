package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.CarriedFish;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The picker's grouping rule (docs/fish-companions.md §8.2): fish collapse into one row only when
 * they are identical in <em>every</em> component — two bluegills of different sizes, or one with a
 * custom name, are separate rows — so "you get the exact fish back" holds for whichever row you
 * pick. Same rule the server's find uses, so the picker can never offer a row the server refuses.
 */
public final class CarriedFishGameTests {
    private CarriedFishGameTests() {}

    private static ItemStack bluegill(float sizeCm) {
        return ItemSizeHelper.setSize(new ItemStack(FishtasticItems.BLUEGILL.value()), sizeCm);
    }

    public static void identicalFishCollapseWithTheirTotalCount(GameTestHelper helper) {
        List<CarriedFish.Group> groups = CarriedFish.group(List.of(
                bluegill(25f), bluegill(25f), bluegill(25f)));

        helper.assertTrue(groups.size() == 1, "identical fish must collapse, got " + groups.size() + " rows");
        helper.assertTrue(groups.get(0).count() == 3, "the row must total 3, got " + groups.get(0).count());
        helper.succeed();
    }

    public static void anyComponentDifferenceSplitsTheRow(GameTestHelper helper) {
        ItemStack named = bluegill(25f);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Bubbles"));

        List<CarriedFish.Group> groups = CarriedFish.group(List.of(
                bluegill(25f), bluegill(25f), bluegill(31f), named));

        helper.assertTrue(groups.size() == 3, "25, 31 and the renamed one are three rows, got " + groups.size());
        helper.assertTrue(groups.get(0).count() == 2, "the two plain 25s share a row");
        helper.assertTrue(groups.get(1).count() == 1 && groups.get(2).count() == 1, "the odd ones out stay single");
        helper.succeed();
    }

    public static void emptyStacksAreIgnored(GameTestHelper helper) {
        List<CarriedFish.Group> groups = CarriedFish.group(List.of(ItemStack.EMPTY, bluegill(25f)));
        helper.assertTrue(groups.size() == 1 && groups.get(0).count() == 1, "empty stacks must not become rows");
        helper.succeed();
    }
}

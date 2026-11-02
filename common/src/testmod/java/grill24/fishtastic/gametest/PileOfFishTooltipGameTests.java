package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.BundleContents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * PORT-ONLY (1.20.1): vanilla 1.20.1 {@code BundleItem} reads the "Items" NBT list, while a pile
 * stores its contents under the {@code fishtastic:bundle_contents} ComponentKey. The pile must
 * override the bundle tooltip/bar so they show the fish rather than empty slots.
 */
public final class PileOfFishTooltipGameTests {
    private PileOfFishTooltipGameTests() {}

    public static void pileOfFishTooltipShowsContents(GameTestHelper helper) {
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        BundleContents.Mutable contents = new BundleContents.Mutable(BundleContents.EMPTY);
        contents.tryInsert(new ItemStack(Items.COD));
        contents.tryInsert(new ItemStack(Items.SALMON));
        FishtasticItemData.setBundleContents(pile, contents.toImmutable());

        Optional<TooltipComponent> image = pile.getTooltipImage();
        if (image.isEmpty() || !(image.get() instanceof BundleTooltip tooltip)) {
            helper.fail("Pile of fish has no bundle tooltip image: " + image);
            return;
        }
        if (tooltip.getItems().size() != 2) {
            helper.fail("Pile tooltip shows " + tooltip.getItems().size() + " items, expected 2");
            return;
        }
        // Bundles list newest-first, like vanilla.
        if (tooltip.getItems().stream().noneMatch(s -> s.is(Items.COD))
                || tooltip.getItems().stream().noneMatch(s -> s.is(Items.SALMON))) {
            helper.fail("Pile tooltip items are wrong: " + tooltip.getItems());
            return;
        }
        if (tooltip.getWeight() <= 0) {
            helper.fail("Pile tooltip weight is " + tooltip.getWeight() + ", expected > 0");
            return;
        }
        if (!pile.isBarVisible()) {
            helper.fail("Pile fullness bar is hidden for a non-empty pile");
            return;
        }
        helper.succeed();
    }
}

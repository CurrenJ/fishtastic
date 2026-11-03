package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Inventory clicks with a carried Pile of Fish, driven through real container inputs on the
 * player's own inventory menu. Right-clicking the same slot repeatedly deals the pile out there,
 * growing a pile in the slot — it must never swap the carried pile into the slot.
 */
public final class PileOfFishClickGameTests {
    private PileOfFishClickGameTests() {}

    /** Inventory-menu slot of hotbar index 0. */
    private static final int HOTBAR_0 = 36;
    private static final int RIGHT = 1;

    private static ServerPlayer setUp(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        return player;
    }

    private static ItemStack bluegill(float sizeCm) {
        return ItemSizeHelper.setSize(new ItemStack(FishtasticItems.BLUEGILL.value()), sizeCm);
    }

    private static ItemStack pileOf(ItemStack... fish) {
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack f : fish) contents.add(f.copy());
        FishtasticItemData.setBundleContents(pile, new BundleContents(contents));
        return pile;
    }

    private static int fishIn(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!stack.is(FishtasticItems.PILE_OF_FISH.value())) return stack.getCount();
        BundleContents contents = FishtasticItemData.bundleContents(stack);
        if (contents == null) return 0;
        int n = 0;
        for (ItemStack t : contents.items()) n += t.getCount();
        return n;
    }

    private static void rightClick(AbstractContainerMenu menu, ServerPlayer player, int slot) {
        menu.clicked(slot, RIGHT, ClickType.PICKUP, player);
    }

    /**
     * Three distinct-size bluegills (which can't stack with each other) carried as a pile, dealt
     * onto one initially empty slot with three right-clicks: a fish, then a pile of two, then a
     * pile of all three — the cursor shrinking to a single fish and then to nothing.
     */
    public static void rightClickDealsPileIntoOneSlot(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        AbstractContainerMenu menu = player.inventoryMenu;
        menu.setCarried(pileOf(bluegill(10f), bluegill(11f), bluegill(12f)));

        rightClick(menu, player, HOTBAR_0);
        ItemStack slot = menu.getSlot(HOTBAR_0).getItem();
        helper.assertTrue(slot.is(FishtasticItems.BLUEGILL.value()) && slot.getCount() == 1,
                "1st right-click must drop one fish into the empty slot, got " + slot);
        helper.assertTrue(menu.getCarried().is(FishtasticItems.PILE_OF_FISH.value()) && fishIn(menu.getCarried()) == 2,
                "Cursor must still hold a pile of 2, got " + menu.getCarried());

        rightClick(menu, player, HOTBAR_0);
        slot = menu.getSlot(HOTBAR_0).getItem();
        helper.assertTrue(slot.is(FishtasticItems.PILE_OF_FISH.value()) && fishIn(slot) == 2,
                "2nd right-click must grow a pile of 2 in the slot instead of swapping, got " + slot);
        helper.assertTrue(menu.getCarried().is(FishtasticItems.BLUEGILL.value()) && menu.getCarried().getCount() == 1,
                "Cursor pile must decompose to its last fish, got " + menu.getCarried());

        rightClick(menu, player, HOTBAR_0);
        slot = menu.getSlot(HOTBAR_0).getItem();
        helper.assertTrue(slot.is(FishtasticItems.PILE_OF_FISH.value()) && fishIn(slot) == 3,
                "3rd right-click must add the last fish to the slot pile, got " + slot);
        helper.assertTrue(menu.getCarried().isEmpty(), "Cursor must be empty, got " + menu.getCarried());
        helper.succeed();
    }

    /**
     * The pile's next entry (index 0) is identical to the slot's fish, so it merges onto that
     * stack rather than starting a pile; the carried pile decomposes to its remaining fish.
     */
    public static void rightClickMergesIdenticalFishOntoSlotStack(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        AbstractContainerMenu menu = player.inventoryMenu;
        menu.setCarried(pileOf(bluegill(20f), bluegill(10f)));
        menu.getSlot(HOTBAR_0).set(bluegill(20f));

        rightClick(menu, player, HOTBAR_0);
        ItemStack slot = menu.getSlot(HOTBAR_0).getItem();
        helper.assertTrue(slot.is(FishtasticItems.BLUEGILL.value()) && slot.getCount() == 2,
                "Identical fish must merge onto the slot stack (2 bluegills), got " + slot);
        helper.assertTrue(ItemStack.isSameItemSameComponents(menu.getCarried(), bluegill(10f)),
                "Cursor must hold the remaining 10cm bluegill, got " + menu.getCarried());
        helper.succeed();
    }
}

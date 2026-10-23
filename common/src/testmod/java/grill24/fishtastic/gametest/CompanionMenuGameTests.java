package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.menu.CompanionMenu;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * The companion menu (docs/fish-companions.md): three persistent party slots (menu slots
 * 0–2, slot 0 the lead) backed by the companion attachment, driven here through real container
 * inputs. Every test checks the same thing underneath — a fish is always in exactly one place,
 * never duplicated, never lost.
 */
public final class CompanionMenuGameTests {
    private CompanionMenuGameTests() {}

    private static final int COMPANION = 0;
    private static final net.minecraft.resources.Identifier BLUEGILL =
            grill24.fishtastic.util.Ids.of("fishtastic", "bluegill");
    /** Quest-only species: never unlocked by setUp, so it stays locked. */
    private static final net.minecraft.resources.Identifier LOCKED =
            grill24.fishtastic.util.Ids.of("fishtastic", "molten_moorish_idol");

    /** Menu slot for a hotbar index (0-8). */
    private static int hotbar(int index) {
        return CompanionMenu.INVENTORY_END - 9 + index;
    }

    private static ServerPlayer setUp(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        CompanionStorage.clear(player);
        PlayerQuestState state = FishCatchSavedData.getOrCreate(player.level().getServer()).getOrCreateQuestState(player);
        state.clearCompanionBonds();
        state.unlockCompanionBond(BLUEGILL);
        state.drainPendingCompanionBanners();
        return player;
    }

    private static CompanionMenu open(ServerPlayer player) {
        return new CompanionMenu(1, player.getInventory());
    }

    private static ItemStack bluegill(float sizeCm) {
        return ItemSizeHelper.setSize(new ItemStack(FishtasticItems.BLUEGILL.value()), sizeCm);
    }

    private static void click(CompanionMenu menu, ServerPlayer player, int slot, int button, ContainerInput input) {
        menu.clicked(slot, button, input, player);
    }

    private static void pickup(CompanionMenu menu, ServerPlayer player, int slot) {
        click(menu, player, slot, 0, ContainerInput.PICKUP);
    }

    /** Bluegills anywhere the menu can put them: inventory, cursor, companion. */
    private static int bluegills(ServerPlayer player, AbstractContainerMenu menu) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(FishtasticItems.BLUEGILL.value())) total += stack.getCount();
            // Returned companions are piled (RemoveTankEntryPacket.giveOrDrop).
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) total += piled(stack);
        }
        if (menu.getCarried().is(FishtasticItems.BLUEGILL.value())) total += menu.getCarried().getCount();
        for (ItemStack companion : CompanionStorage.party(player).slots()) {
            if (companion.is(FishtasticItems.BLUEGILL.value())) total += companion.getCount();
        }
        return total;
    }

    private static int piled(ItemStack pile) {
        return grill24.fishtastic.FishtasticItemData.bundleContentsOrEmpty(pile).itemCopyStream()
                .filter(s -> s.is(FishtasticItems.BLUEGILL.value())).mapToInt(ItemStack::getCount).sum();
    }

    /** Loose or piled. */
    private static boolean holds(ServerPlayer player, ItemStack fish) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (ItemStack.isSameItemSameComponents(stack, fish)) return true;
            if (stack.is(FishtasticItems.PILE_OF_FISH.value()) && grill24.fishtastic.FishtasticItemData
                    .bundleContentsOrEmpty(stack).itemCopyStream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, fish))) return true;
        }
        return false;
    }

    /** Pick a fish up, drop it in the companion slot, take it back out, put it away. */
    public static void clickEquipsAndReturns(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f);
        player.getInventory().setItem(0, fish.copy());
        CompanionMenu menu = open(player);

        pickup(menu, player, hotbar(0));
        pickup(menu, player, COMPANION);
        helper.assertTrue(ItemStack.matches(CompanionStorage.lead(player), fish), "click must equip, got " + CompanionStorage.lead(player));
        helper.assertTrue(menu.getCarried().isEmpty(), "cursor must be empty after placing");

        pickup(menu, player, COMPANION);
        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "taking the fish out must clear the companion");
        pickup(menu, player, hotbar(0));
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), fish), "the fish must come home unchanged");
        helper.assertTrue(bluegills(player, menu) == 1, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Clicking the slot while carrying another fish swaps the two companions through the cursor. */
    public static void cursorSwapExchangesCompanions(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack first = bluegill(20f), second = bluegill(30f);
        Companions.equip(player, 0, first.copy());
        player.getInventory().setItem(0, second.copy());
        CompanionMenu menu = open(player);

        pickup(menu, player, hotbar(0));
        pickup(menu, player, COMPANION);

        helper.assertTrue(ItemStack.matches(CompanionStorage.lead(player), second), "companion " + CompanionStorage.lead(player));
        helper.assertTrue(ItemStack.matches(menu.getCarried(), first), "the old companion must be on the cursor, got " + menu.getCarried());
        helper.assertTrue(menu.getSlot(COMPANION).getItem().getCount() == 1, "exactly one fish in the slot");
        helper.assertTrue(bluegills(player, menu) == 2, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Number-key swaps go both ways, and swapping into an empty hotbar slot unequips. */
    public static void numberKeySwapExchangesAndReturns(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack first = bluegill(20f), second = bluegill(30f);
        Companions.equip(player, 0, first.copy());
        player.getInventory().setItem(2, second.copy());
        CompanionMenu menu = open(player);

        click(menu, player, COMPANION, 2, ContainerInput.SWAP);
        helper.assertTrue(ItemStack.matches(CompanionStorage.lead(player), second), "companion " + CompanionStorage.lead(player));
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(2), first), "hotbar 2 " + player.getInventory().getItem(2));

        click(menu, player, COMPANION, 3, ContainerInput.SWAP);
        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "swapping with an empty hotbar slot must unequip");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(3), second), "hotbar 3 " + player.getInventory().getItem(3));
        helper.assertTrue(bluegills(player, menu) == 2, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Dragging a stack across the companion slot puts exactly one fish there. */
    public static void dragPlacesOneFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f);
        player.getInventory().setItem(0, fish.copyWithCount(3));
        CompanionMenu menu = open(player);
        pickup(menu, player, hotbar(0));

        click(menu, player, -999, AbstractContainerMenu.getQuickcraftMask(0, 0), ContainerInput.QUICK_CRAFT);
        click(menu, player, COMPANION, AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT);
        click(menu, player, hotbar(1), AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT);
        click(menu, player, -999, AbstractContainerMenu.getQuickcraftMask(2, 0), ContainerInput.QUICK_CRAFT);

        helper.assertTrue(CompanionStorage.lead(player).getCount() == 1 && ItemStack.isSameItemSameComponents(CompanionStorage.lead(player), fish),
                "companion " + CompanionStorage.lead(player));
        helper.assertTrue(bluegills(player, menu) == 3, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Shift-clicking a stack bonds one fish from it into the first empty slot; the lead stays. */
    public static void shiftClickTakesOneFromAStack(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack previous = bluegill(20f), fish = bluegill(25f);
        Companions.equip(player, 0, previous.copy());
        player.getInventory().setItem(0, fish.copyWithCount(3));
        CompanionMenu menu = open(player);

        click(menu, player, hotbar(0), 0, ContainerInput.QUICK_MOVE);

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), previous), "the lead must stay, got " + CompanionStorage.get(player, 0));
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 1), fish), "slot 1 " + CompanionStorage.get(player, 1));
        helper.assertTrue(CompanionStorage.get(player, 2).isEmpty(), "exactly one fish may move, slot 2 " + CompanionStorage.get(player, 2));
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 2, "exactly one fish must leave the stack, left "
                + player.getInventory().getItem(0).getCount());
        helper.assertTrue(bluegills(player, menu) == 4, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /**
     * Shift-click fills the first empty slot, skipping filled ones, and moves exactly one fish —
     * vanilla would loop quick-move while the source holds the same item and fill every slot.
     */
    public static void shiftClickFillsTheFirstEmptySlotOnce(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack lead = bluegill(20f), last = bluegill(22f), fish = bluegill(30f);
        Companions.equip(player, 0, lead.copy());
        Companions.equip(player, 2, last.copy());
        player.getInventory().setItem(0, fish.copyWithCount(2));
        CompanionMenu menu = open(player);

        click(menu, player, hotbar(0), 0, ContainerInput.QUICK_MOVE);

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 1), fish), "slot 1 " + CompanionStorage.get(player, 1));
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), lead)
                && ItemStack.matches(CompanionStorage.get(player, 2), last), "filled slots must not change");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 1, "one fish left in the stack");
        helper.assertTrue(bluegills(player, menu) == 4, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /**
     * A full party takes nothing by shift-click (it never replaces a companion), and with no room
     * in the inventory, shift-clicking a companion out changes nothing either.
     */
    public static void shiftClickWithAFullPartyOrInventoryChangesNothing(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f);
        for (int i = 0; i < 3; i++) Companions.equip(player, i, bluegill(20f + i));
        for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        player.getInventory().setItem(0, fish.copyWithCount(3));
        CompanionMenu menu = open(player);

        click(menu, player, hotbar(0), 0, ContainerInput.QUICK_MOVE);
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 3, "a full party must leave the stack untouched");
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(ItemSizeHelper.getSize(CompanionStorage.get(player, i)) == 20f + i, "slot " + i + " changed");
        }

        click(menu, player, COMPANION, 0, ContainerInput.QUICK_MOVE);
        helper.assertTrue(ItemSizeHelper.getSize(CompanionStorage.get(player, 0)) == 20f, "a companion with nowhere to go must stay");
        helper.assertTrue(bluegills(player, menu) == 6, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Three fish into three slots by shift-click, then one returned from the middle. */
    public static void threeSlotsEquipAndReturn(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack a = bluegill(20f), b = bluegill(25f), c = bluegill(30f);
        player.getInventory().setItem(0, a.copy());
        player.getInventory().setItem(1, b.copy());
        player.getInventory().setItem(2, c.copy());
        CompanionMenu menu = open(player);

        for (int i = 0; i < 3; i++) click(menu, player, hotbar(i), 0, ContainerInput.QUICK_MOVE);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), a)
                && ItemStack.matches(CompanionStorage.get(player, 1), b)
                && ItemStack.matches(CompanionStorage.get(player, 2), c), "party " + CompanionStorage.party(player));

        click(menu, player, 1, 0, ContainerInput.QUICK_MOVE);
        helper.assertTrue(CompanionStorage.get(player, 1).isEmpty(), "the middle slot must be returned");
        helper.assertTrue(holds(player, b), "the middle fish must come home");
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), a)
                && ItemStack.matches(CompanionStorage.get(player, 2), c), "the other slots must stay");
        helper.assertTrue(bluegills(player, menu) == 3, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Moving a fish between party slots reorders the party: here the lead moves to the back and back again. */
    public static void movingBetweenSlotsReordersTheParty(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack a = bluegill(20f), b = bluegill(25f);
        Companions.equip(player, 0, a.copy());
        Companions.equip(player, 1, b.copy());
        CompanionMenu menu = open(player);

        pickup(menu, player, 0);
        pickup(menu, player, 2);
        helper.assertTrue(CompanionStorage.get(player, 0).isEmpty() && ItemStack.matches(CompanionStorage.get(player, 2), a),
                "the lead must move to slot 2, party " + CompanionStorage.party(player));

        pickup(menu, player, 1);
        pickup(menu, player, 0);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), b), "b is the new lead, party " + CompanionStorage.party(player));

        pickup(menu, player, 2);
        pickup(menu, player, 0);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), a) && ItemStack.matches(menu.getCarried(), b),
                "placing on a filled slot swaps through the cursor");
        pickup(menu, player, 1);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 1), b) && CompanionStorage.get(player, 2).isEmpty(),
                "party " + CompanionStorage.party(player));
        helper.assertTrue(bluegills(player, menu) == 2, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** A species without an unlocked bond can't enter the slot by any input. */
    public static void lockedFishIsRejected(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack locked = new ItemStack(BuiltInRegistries.ITEM.getOptional(LOCKED).orElseThrow());
        player.getInventory().setItem(0, locked.copy());
        CompanionMenu menu = open(player);

        click(menu, player, hotbar(0), 0, ContainerInput.QUICK_MOVE);
        click(menu, player, COMPANION, 0, ContainerInput.SWAP);
        pickup(menu, player, hotbar(0));
        pickup(menu, player, COMPANION);

        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "a locked fish must never bond, got " + CompanionStorage.lead(player));
        helper.assertTrue(ItemStack.matches(menu.getCarried(), locked), "the locked fish must stay on the cursor");
        helper.succeed();
    }

    /** Closing the menu with the companion on the cursor puts it back in the inventory. */
    public static void closingWithAFishOnTheCursorKeepsIt(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f);
        Companions.equip(player, 0, fish.copy());
        CompanionMenu menu = open(player);

        pickup(menu, player, COMPANION);
        menu.removed(player);

        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "companion " + CompanionStorage.lead(player));
        helper.assertTrue(menu.getCarried().isEmpty(), "cursor must be cleared on close");
        helper.assertTrue(holds(player, fish), "the cursor fish must land in the inventory");
        helper.succeed();
    }

    /**
     * A bond packet handled in the same tick as a click changes the attachment before the menu's
     * next broadcast. The click must act on the new companion, not hand back the old one a second time.
     */
    public static void clickAfterAnOutsideBondSeesTheNewCompanion(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack first = bluegill(20f), second = bluegill(30f);
        Companions.equip(player, 0, first.copy());
        player.getInventory().setItem(0, second.copy());
        CompanionMenu menu = open(player);

        // The pile strip's path: an explicit slot, so the first companion comes back.
        Companions.bond(player, second.copy(), 0);
        pickup(menu, player, COMPANION);

        helper.assertTrue(ItemStack.matches(menu.getCarried(), second), "the click must take the current companion, got " + menu.getCarried());
        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "companion " + CompanionStorage.lead(player));
        helper.assertTrue(bluegills(player, menu) == 2, "no fish may be duplicated, count " + bluegills(player, menu));
        helper.succeed();
    }

    /** A rename made outside the menu reaches its slot on the next broadcast, without re-saving. */
    public static void outsideRenameReachesTheSlot(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        Companions.equip(player, 0, bluegill(25f));
        CompanionMenu menu = open(player);

        Companions.rename(player, 0, "Gilbert");
        menu.broadcastChanges();

        ItemStack shown = menu.getSlot(COMPANION).getItem();
        helper.assertTrue(Component.literal("Gilbert").equals(shown.get(DataComponents.CUSTOM_NAME)), "slot shows " + shown);
        helper.assertTrue(ItemStack.matches(CompanionStorage.lead(player), shown), "storage and slot must agree");
        helper.succeed();
    }

    private static ItemStack pileOf(ItemStack... fish) {
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        java.util.List<net.minecraft.world.item.ItemStackTemplate> contents = new java.util.ArrayList<>();
        for (ItemStack f : fish) contents.add(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(f));
        grill24.fishtastic.FishtasticItemData.setBundleContents(pile, new net.minecraft.world.item.component.BundleContents(contents));
        return pile;
    }

    /**
     * The pile strip (§5.5) puts the chosen fish on the cursor, taken out of the pile — never the
     * identical loose fish beside it — and from there it's ordinary vanilla handling.
     */
    public static void pileFishGoesToTheCursor(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack wanted = bluegill(25f);
        player.getInventory().setItem(0, pileOf(bluegill(20f), wanted.copy(), bluegill(30f)));
        player.getInventory().setItem(1, wanted.copy());
        CompanionMenu menu = open(player);

        helper.assertTrue(menu.takeFromPile(player, wanted.copy()) == CompanionMenu.PileTake.TAKEN, "the take must succeed");
        helper.assertTrue(ItemStack.matches(menu.getCarried(), wanted), "cursor " + menu.getCarried());
        helper.assertTrue(piled(player.getInventory().getItem(0)) == 2, "two fish left in the pile, got " + piled(player.getInventory().getItem(0)));
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(1), wanted), "the loose fish must stay put");

        pickup(menu, player, 1);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 1), wanted), "slot 1 " + CompanionStorage.get(player, 1));
        helper.assertTrue(bluegills(player, menu) == 4, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Taking the second-to-last fish out of a pile leaves the last one loose, as the pile item does. */
    public static void pileLeftWithOneFishBecomesThatFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack wanted = bluegill(25f), other = bluegill(30f);
        player.getInventory().setItem(0, pileOf(wanted.copy(), other.copy()));
        CompanionMenu menu = open(player);

        helper.assertTrue(menu.takeFromPile(player, wanted.copy()) == CompanionMenu.PileTake.TAKEN, "the take must succeed");
        helper.assertTrue(ItemStack.matches(menu.getCarried(), wanted), "cursor " + menu.getCarried());
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), other), "the pile must become its last fish, got "
                + player.getInventory().getItem(0));
        helper.assertTrue(bluegills(player, menu) == 2, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Only with an empty cursor: the server rechecks, and nothing moves. */
    public static void pileTakeNeedsAnEmptyCursor(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack wanted = bluegill(25f), held = bluegill(40f);
        player.getInventory().setItem(0, pileOf(wanted.copy(), bluegill(30f)));
        player.getInventory().setItem(1, held.copy());
        CompanionMenu menu = open(player);
        pickup(menu, player, hotbar(1));

        helper.assertTrue(menu.takeFromPile(player, wanted.copy()) == CompanionMenu.PileTake.CURSOR_BUSY, "a busy cursor must refuse");
        helper.assertTrue(ItemStack.matches(menu.getCarried(), held), "the cursor must keep its fish, got " + menu.getCarried());
        helper.assertTrue(piled(player.getInventory().getItem(0)) == 2, "the pile must be untouched");
        helper.succeed();
    }

    /** A description that matches nothing in a pile (the fish left while the screen was open) changes nothing. */
    public static void pileTakeRefusesAStaleDescription(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack stale = bluegill(25f);
        player.getInventory().setItem(0, pileOf(bluegill(20f), bluegill(30f)));
        player.getInventory().setItem(1, stale.copy());
        CompanionMenu menu = open(player);

        helper.assertTrue(menu.takeFromPile(player, stale.copy()) == CompanionMenu.PileTake.NOT_CARRIED, "a stale description must refuse");
        helper.assertTrue(menu.getCarried().isEmpty(), "cursor " + menu.getCarried());
        helper.assertTrue(piled(player.getInventory().getItem(0)) == 2 && ItemStack.matches(player.getInventory().getItem(1), stale),
                "nothing may move, even the identical loose fish");
        helper.succeed();
    }

    /** Each pencil names its own slot: renaming the second slot leaves the lead's fish alone. */
    public static void renameSecondSlotLeavesTheLead(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack lead = bluegill(20f);
        Companions.equip(player, 0, lead.copy());
        Companions.equip(player, 1, bluegill(25f));
        CompanionMenu menu = open(player);

        helper.assertTrue(Companions.rename(player, 1, "Finn"), "the rename must apply");
        menu.broadcastChanges();

        helper.assertTrue(Component.literal("Finn").equals(CompanionStorage.get(player, 1).get(DataComponents.CUSTOM_NAME)),
                "slot 1 " + CompanionStorage.get(player, 1));
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), lead), "the lead must be untouched, got " + CompanionStorage.get(player, 0));
        helper.assertTrue(ItemStack.matches(menu.getSlot(1).getItem(), CompanionStorage.get(player, 1)), "storage and slot must agree");
        helper.succeed();
    }
}

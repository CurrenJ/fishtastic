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
 * The companion menu (docs/companions-gui-redesign-handoff.md): one persistent companion slot
 * backed by the companion attachment, driven here through real container inputs. Every test checks
 * the same thing underneath — a fish is always in exactly one place, never duplicated, never lost.
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
        CompanionStorage.set(player, ItemStack.EMPTY);
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
        if (CompanionStorage.get(player).is(FishtasticItems.BLUEGILL.value())) total += CompanionStorage.get(player).getCount();
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
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), fish), "click must equip, got " + CompanionStorage.get(player));
        helper.assertTrue(menu.getCarried().isEmpty(), "cursor must be empty after placing");

        pickup(menu, player, COMPANION);
        helper.assertTrue(CompanionStorage.get(player).isEmpty(), "taking the fish out must clear the companion");
        pickup(menu, player, hotbar(0));
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), fish), "the fish must come home unchanged");
        helper.assertTrue(bluegills(player, menu) == 1, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Clicking the slot while carrying another fish swaps the two companions through the cursor. */
    public static void cursorSwapExchangesCompanions(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack first = bluegill(20f), second = bluegill(30f);
        Companions.equip(player, first.copy());
        player.getInventory().setItem(0, second.copy());
        CompanionMenu menu = open(player);

        pickup(menu, player, hotbar(0));
        pickup(menu, player, COMPANION);

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), second), "companion " + CompanionStorage.get(player));
        helper.assertTrue(ItemStack.matches(menu.getCarried(), first), "the old companion must be on the cursor, got " + menu.getCarried());
        helper.assertTrue(menu.getSlot(COMPANION).getItem().getCount() == 1, "exactly one fish in the slot");
        helper.assertTrue(bluegills(player, menu) == 2, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Number-key swaps go both ways, and swapping into an empty hotbar slot unequips. */
    public static void numberKeySwapExchangesAndReturns(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack first = bluegill(20f), second = bluegill(30f);
        Companions.equip(player, first.copy());
        player.getInventory().setItem(2, second.copy());
        CompanionMenu menu = open(player);

        click(menu, player, COMPANION, 2, ContainerInput.SWAP);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), second), "companion " + CompanionStorage.get(player));
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(2), first), "hotbar 2 " + player.getInventory().getItem(2));

        click(menu, player, COMPANION, 3, ContainerInput.SWAP);
        helper.assertTrue(CompanionStorage.get(player).isEmpty(), "swapping with an empty hotbar slot must unequip");
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

        helper.assertTrue(CompanionStorage.get(player).getCount() == 1 && ItemStack.isSameItemSameComponents(CompanionStorage.get(player), fish),
                "companion " + CompanionStorage.get(player));
        helper.assertTrue(bluegills(player, menu) == 3, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** Shift-clicking a stack bonds one fish from it and returns the previous companion, once. */
    public static void shiftClickTakesOneFromAStack(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack previous = bluegill(20f), fish = bluegill(25f);
        Companions.equip(player, previous.copy());
        player.getInventory().setItem(0, fish.copyWithCount(3));
        CompanionMenu menu = open(player);

        click(menu, player, hotbar(0), 0, ContainerInput.QUICK_MOVE);

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), fish), "companion " + CompanionStorage.get(player));
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 2, "exactly one fish must leave the stack, left "
                + player.getInventory().getItem(0).getCount());
        helper.assertTrue(holds(player, previous), "the previous companion must come back");
        helper.assertTrue(bluegills(player, menu) == 4, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** A same-species shift-click swaps exactly once — vanilla would loop it back and forth. */
    public static void shiftClickSameSpeciesSwapsOnce(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack previous = bluegill(20f), fish = bluegill(30f);
        Companions.equip(player, previous.copy());
        player.getInventory().setItem(0, fish.copy());
        CompanionMenu menu = open(player);

        click(menu, player, hotbar(0), 0, ContainerInput.QUICK_MOVE);

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), fish), "companion " + CompanionStorage.get(player));
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), previous),
                "the previous companion must take the fish's place, got " + player.getInventory().getItem(0));
        helper.assertTrue(bluegills(player, menu) == 2, "fish count " + bluegills(player, menu));
        helper.succeed();
    }

    /** With no room for the old companion, shift-clicking a stack or the companion changes nothing. */
    public static void shiftClickWithAFullInventoryChangesNothing(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack previous = bluegill(20f), fish = bluegill(25f);
        Companions.equip(player, previous.copy());
        for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        player.getInventory().setItem(0, fish.copyWithCount(3));
        CompanionMenu menu = open(player);

        click(menu, player, hotbar(0), 0, ContainerInput.QUICK_MOVE);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), previous), "companion " + CompanionStorage.get(player));
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 3, "the stack must be untouched");

        click(menu, player, COMPANION, 0, ContainerInput.QUICK_MOVE);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), previous), "a companion with nowhere to go must stay");
        helper.assertTrue(bluegills(player, menu) == 4, "fish count " + bluegills(player, menu));
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

        helper.assertTrue(CompanionStorage.get(player).isEmpty(), "a locked fish must never bond, got " + CompanionStorage.get(player));
        helper.assertTrue(ItemStack.matches(menu.getCarried(), locked), "the locked fish must stay on the cursor");
        helper.succeed();
    }

    /** Closing the menu with the companion on the cursor puts it back in the inventory. */
    public static void closingWithAFishOnTheCursorKeepsIt(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f);
        Companions.equip(player, fish.copy());
        CompanionMenu menu = open(player);

        pickup(menu, player, COMPANION);
        menu.removed(player);

        helper.assertTrue(CompanionStorage.get(player).isEmpty(), "companion " + CompanionStorage.get(player));
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
        Companions.equip(player, first.copy());
        player.getInventory().setItem(0, second.copy());
        CompanionMenu menu = open(player);

        Companions.bond(player, second.copy());
        pickup(menu, player, COMPANION);

        helper.assertTrue(ItemStack.matches(menu.getCarried(), second), "the click must take the current companion, got " + menu.getCarried());
        helper.assertTrue(CompanionStorage.get(player).isEmpty(), "companion " + CompanionStorage.get(player));
        helper.assertTrue(bluegills(player, menu) == 2, "no fish may be duplicated, count " + bluegills(player, menu));
        helper.succeed();
    }

    /** A rename made outside the menu reaches its slot on the next broadcast, without re-saving. */
    public static void outsideRenameReachesTheSlot(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        Companions.equip(player, bluegill(25f));
        CompanionMenu menu = open(player);

        Companions.rename(player, "Gilbert");
        menu.broadcastChanges();

        ItemStack shown = menu.getSlot(COMPANION).getItem();
        helper.assertTrue(Component.literal("Gilbert").equals(shown.get(DataComponents.CUSTOM_NAME)), "slot shows " + shown);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), shown), "storage and slot must agree");
        helper.succeed();
    }
}

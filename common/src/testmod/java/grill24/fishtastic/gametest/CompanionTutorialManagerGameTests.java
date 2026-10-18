package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.menu.CompanionMenu;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.tutorial.CompanionTutorialManager;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.List;
import java.util.function.Supplier;

/** Real container operations and shortcut operations must teach the same lesson without losing fish. */
public final class CompanionTutorialManagerGameTests {
    private CompanionTutorialManagerGameTests() {}
    private static final int HOTBAR_FISH = CompanionMenu.INVENTORY_END - 8;

    private static ServerPlayer start(Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.getInventory().clearContent();
        CompanionStorage.clear(player);
        var data = FishCatchSavedData.getOrCreate(player.level().getServer());
        data.getOrCreateQuestState(player).unlockCompanionBond(Ids.of("fishtastic", "bluegill"));
        CompanionTutorialManager.onBondUnlocked(player);
        player.getInventory().setItem(1, fish());
        return player;
    }

    private static ItemStack fish() { return new ItemStack(FishtasticItems.BLUEGILL.value()); }

    private static CompanionMenu open(ServerPlayer player) {
        CompanionMenu menu = new CompanionMenu(1, player.getInventory());
        player.containerMenu = menu;
        CompanionTutorialManager.onCompanionsTabOpened(player);
        return menu;
    }

    private static void expect(GameTestHelper helper, ServerPlayer player, CompanionTutorialStep step) {
        helper.assertTrue(CompanionTutorialManager.getStep(player) == step,
                "Expected " + step + ", got " + CompanionTutorialManager.getStep(player));
    }

    public static void realMenuWalksAllCompanionLessons(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        expect(helper, player, CompanionTutorialStep.INTRO);
        CompanionMenu menu = open(player);
        expect(helper, player, CompanionTutorialStep.EQUIP);
        menu.clicked(HOTBAR_FISH, 0, ClickType.QUICK_MOVE, player);
        expect(helper, player, CompanionTutorialStep.RETURN);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        expect(helper, player, CompanionTutorialStep.REEQUIP); // Plain pickup counts as removing the companion.
        menu.clicked(HOTBAR_FISH, 0, ClickType.PICKUP, player);
        expect(helper, player, CompanionTutorialStep.REEQUIP);
        menu.clicked(HOTBAR_FISH, 0, ClickType.QUICK_MOVE, player);
        expect(helper, player, CompanionTutorialStep.LEAD);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        expect(helper, player, CompanionTutorialStep.LEAD);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        expect(helper, player, CompanionTutorialStep.RENAME);
        helper.assertTrue(Companions.rename(player, 0, "Bubbles"), "Rename must succeed");
        expect(helper, player, CompanionTutorialStep.GROUPS);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.GROUPS, false);
        expect(helper, player, CompanionTutorialStep.PILES);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.PILES, false);
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        CompanionTutorialManager.onBondUnlocked(player);
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        helper.assertTrue(CompanionStorage.party(player).size() == 1 && menu.getCarried().isEmpty(), "The tour must leave one real companion");
        helper.assertTrue(CompanionStorage.get(player, 0).get(DataComponents.CUSTOM_NAME).getString().equals("Bubbles"), "Keep the chosen name");
        helper.succeed();
    }

    public static void shortcutsAndShiftReturnsAdvanceTheSameLessons(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        helper.assertTrue(Companions.bond(player, fish()) == Companions.BondResult.BONDED, "Inventory shortcut equips a real fish");
        expect(helper, player, CompanionTutorialStep.RETURN);
        CompanionMenu menu = open(player);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        expect(helper, player, CompanionTutorialStep.REEQUIP);
        helper.assertTrue(Companions.bond(player, fish()) == Companions.BondResult.BONDED, "Reequip must find the returned fish");
        expect(helper, player, CompanionTutorialStep.LEAD);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.LEAD, false);
        expect(helper, player, CompanionTutorialStep.RENAME);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.RENAME, false);
        expect(helper, player, CompanionTutorialStep.GROUPS); // A custom name is optional.
        helper.succeed();
    }

    public static void closingWithReturnedFishResumesWithoutLosingIt(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        CompanionMenu menu = open(player);
        menu.clicked(HOTBAR_FISH, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        expect(helper, player, CompanionTutorialStep.REEQUIP);
        menu.removed(player);
        expect(helper, player, CompanionTutorialStep.REEQUIP);
        CompanionTutorialManager.onPlayerJoin(player);
        open(player);
        expect(helper, player, CompanionTutorialStep.REEQUIP);
        helper.assertTrue(Companions.bond(player, fish()) == Companions.BondResult.BONDED, "Closing must return the cursor fish safely");
        expect(helper, player, CompanionTutorialStep.LEAD);
        helper.succeed();
    }

    public static void stalePacketsCannotReplaceCompanionActions(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        CompanionMenu menu = open(player);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.EQUIP, false);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.PILES, false);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.INTRO, true);
        expect(helper, player, CompanionTutorialStep.EQUIP);
        menu.clicked(HOTBAR_FISH, 0, ClickType.QUICK_MOVE, player);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.RETURN, false);
        expect(helper, player, CompanionTutorialStep.RETURN);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.RETURN, true);
        expect(helper, player, CompanionTutorialStep.REEQUIP); // Stale packets cannot skip the next action during a drag.
        menu.clicked(HOTBAR_FISH, 0, ClickType.PICKUP, player);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.REEQUIP, false);
        expect(helper, player, CompanionTutorialStep.REEQUIP);
        helper.succeed();
    }

    public static void skipAndReplayKeepEarnedPartyAndUnlocks(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        CompanionMenu menu = open(player);
        menu.clicked(HOTBAR_FISH, 0, ClickType.QUICK_MOVE, player);
        Companions.rename(player, 0, "Keep me");
        var before = CompanionStorage.party(player);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.RETURN, true);
        CompanionTutorialManager.onPlayerJoin(player);
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        CompanionTutorialManager.reset(player);
        expect(helper, player, CompanionTutorialStep.INTRO);
        open(player);
        expect(helper, player, CompanionTutorialStep.RETURN); // Already equipped: don't demand another fish.
        helper.assertTrue(before.matches(CompanionStorage.party(player)), "Replay must not change fish or names");
        helper.assertTrue(FishCatchSavedData.getOrCreate(player.level().getServer()).getOrCreateQuestState(player)
                .isCompanionBondUnlocked(Ids.of("fishtastic", "bluegill")), "Keep earned unlocks");
        helper.assertTrue(CompanionTutorialStep.COMPLETE.ordinal() == 2, "Keep legacy completion saves complete");
        helper.succeed();
    }

    public static void pileFishCanCompleteTheEquipLesson(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        player.getInventory().setItem(1, ItemStack.EMPTY);
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        FishtasticItemData.setBundleContents(pile, new BundleContents(List.of(fish(),
                fish())));
        player.getInventory().setItem(2, pile);
        CompanionMenu menu = open(player);
        helper.assertTrue(menu.takeFromPile(player, fish()) == CompanionMenu.PileTake.TAKEN, "Pick a carried pile fish");
        expect(helper, player, CompanionTutorialStep.EQUIP);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        expect(helper, player, CompanionTutorialStep.RETURN);
        helper.succeed();
    }
}

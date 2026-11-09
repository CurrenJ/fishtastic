package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.tutorial.CompanionTutorialManager;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import grill24.fishtastic.util.Ids;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.List;
import java.util.function.Supplier;

/**
 * The companion lesson (one equip step) through the companions screen's server operations: the
 * screen's Equip, the inventory hold-to-equip shortcut and the screen's Unequip all teach the same
 * lesson without losing fish (docs/companion-ui-redesign/layout-spec.md §8).
 */
public final class CompanionTutorialManagerGameTests {
    private CompanionTutorialManagerGameTests() {}

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

    /** What OpenCompanionMenuPacket does on the server besides opening the Gelatin menu. */
    private static void open(ServerPlayer player) {
        CompanionTutorialManager.onCompanionsTabOpened(player);
    }

    private static void expect(GameTestHelper helper, ServerPlayer player, CompanionTutorialStep step) {
        helper.assertTrue(CompanionTutorialManager.getStep(player) == step,
                "Expected " + step + ", got " + CompanionTutorialManager.getStep(player));
    }

    public static void firstEquipCompletesCompanionLesson(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        open(player);
        expect(helper, player, CompanionTutorialStep.EQUIP);
        helper.assertTrue(Companions.bond(player, fish(), 0) == Companions.BondResult.BONDED, "The screen's Equip equips a real fish");
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        helper.assertTrue(CompanionStorage.party(player).size() == 1, "First equip is the tutorial payoff");
        helper.assertTrue(Companions.unequipToInventory(player, 0) == Companions.UnequipResult.UNEQUIPPED, "Unequip");
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        helper.assertTrue(CompanionStorage.party(player).isEmpty(), "Removal stays available without another lesson");
        helper.succeed();
    }

    public static void shortcutEquipCompletesCompanionLesson(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        helper.assertTrue(Companions.bond(player, fish()) == Companions.BondResult.BONDED, "Inventory shortcut equips a real fish");
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        open(player);
        Companions.unequipToInventory(player, 0);
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        helper.succeed();
    }

    /** Skipping the lesson touches nothing the player owns (there is no cursor to drop any more). */
    public static void dismissWithCarriedFishPreservesInventory(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        open(player);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.EQUIP, true);
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        CompanionTutorialManager.onPlayerJoin(player);
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        helper.assertTrue(player.getInventory().contains(stack -> stack.is(FishtasticItems.BLUEGILL.value())), "Skipping keeps the fish");
        helper.assertTrue(CompanionStorage.party(player).isEmpty(), "Skipping equips nothing");
        helper.succeed();
    }

    public static void stalePacketsCannotReplaceCompanionActions(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        open(player);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.EQUIP, false);
        CompanionTutorialManager.advanceStep(player, CompanionTutorialStep.PILES, true);
        expect(helper, player, CompanionTutorialStep.EQUIP);
        CompanionTutorialManager.dismiss(player);
        CompanionTutorialManager.onBondUnlocked(player);
        CompanionTutorialManager.onPlayerJoin(player);
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        helper.succeed();
    }

    public static void skipAndReplayKeepEarnedPartyAndUnlocks(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        open(player);
        Companions.bond(player, fish(), 0);
        Companions.rename(player, 0, "Keep me");
        var before = CompanionStorage.party(player);
        CompanionTutorialManager.reset(player);
        open(player);
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        helper.assertTrue(before.matches(CompanionStorage.party(player)), "Replay must preserve equipped fish and names");
        helper.assertTrue(FishCatchSavedData.getOrCreate(player.level().getServer()).getOrCreateQuestState(player)
                .isCompanionBondUnlocked(Ids.of("fishtastic", "bluegill")), "Keep earned unlocks");
        helper.assertTrue(CompanionTutorialStep.COMPLETE.ordinal() == 2, "Keep legacy completion saves complete");
        for (CompanionTutorialStep legacy : List.of(CompanionTutorialStep.RETURN, CompanionTutorialStep.REEQUIP,
                CompanionTutorialStep.LEAD, CompanionTutorialStep.RENAME, CompanionTutorialStep.GROUPS, CompanionTutorialStep.PILES)) {
            FishCatchSavedData.getOrCreate(player.level().getServer()).setCompanionTutorialStep(player, legacy);
            CompanionTutorialManager.onPlayerJoin(player);
            expect(helper, player, CompanionTutorialStep.COMPLETE);
        }
        helper.succeed();
    }

    /** The screen's list equips straight out of a Pile of Fish. */
    public static void pileFishCanCompleteTheEquipLesson(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = start(mockPlayer);
        player.getInventory().setItem(1, ItemStack.EMPTY);
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        FishtasticItemData.setBundleContents(pile, new BundleContents(List.of(fish(),
                fish())));
        player.getInventory().setItem(2, pile);
        open(player);
        expect(helper, player, CompanionTutorialStep.EQUIP);
        helper.assertTrue(Companions.bond(player, fish(), 0) == Companions.BondResult.BONDED, "Equip a carried pile fish");
        expect(helper, player, CompanionTutorialStep.COMPLETE);
        helper.succeed();
    }
}

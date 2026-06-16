package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.tutorial.TutorialStep;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Server-side game tests for TutorialManager's step-advancement hooks.
 *
 * Each test takes a {@code mockPlayer} supplier rather than calling
 * GameTestHelper.makeMockServerPlayerInLevel() directly, because NeoForge's mock connection
 * needs extra pre-configuration that vanilla's helper doesn't do (see NeoForgeTestPlayers in
 * the neoforge testmod source set for why). Fabric's wrapper passes the vanilla helper method;
 * NeoForge's wrapper passes its own pre-configured variant. Each call to the supplier returns
 * a fresh mock player with its own random UUID, so tests don't need to reset shared state
 * between players.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class TutorialManagerGameTests {

    private TutorialManagerGameTests() {}

    private static void setStep(GameTestHelper helper, ServerPlayer player, TutorialStep step) {
        FishCatchSavedData.getOrCreate(helper.getLevel().getServer()).setTutorialStep(player, step);
    }

    private static ItemStack copperRod() {
        return new ItemStack(FishtasticItems.COPPER_FISHING_ROD.value());
    }

    private static int countItem(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // onItemCrafted
    // -------------------------------------------------------------------------

    /** Crafting the starter rod from the default step grants 8 worms and advances to BAIT_LOAD. */
    public static void craftingRodFromDefaultStepGrantsWormsAndAdvances(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.WAITING_FOR_CAST,
            "An untouched player must default to WAITING_FOR_CAST"
        );

        TutorialManager.onItemCrafted(player, copperRod());

        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD,
            "Crafting the starter rod from WAITING_FOR_CAST must advance to BAIT_LOAD"
        );
        int wormCount = countItem(player, FishtasticItems.WORMS.value());
        helper.assertTrue(wormCount == 8, "Crafting the starter rod must grant exactly 8 worms, got " + wormCount);
        helper.succeed();
    }

    /** Crafting the rod again once already past WAITING_FOR_CAST must not re-grant worms or change the step. */
    public static void craftingRodAgainAfterAdvancingIsNoOp(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        TutorialManager.onItemCrafted(player, copperRod());
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD,
            "Sanity check: first craft must reach BAIT_LOAD"
        );

        TutorialManager.onItemCrafted(player, copperRod());

        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD,
            "Re-crafting the rod once past WAITING_FOR_CAST must not change the step"
        );
        int wormCount = countItem(player, FishtasticItems.WORMS.value());
        helper.assertTrue(wormCount == 8, "Re-crafting the rod must not grant a second batch of worms, got " + wormCount);
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // onBaitLoaded / onHookCast guard clauses
    // -------------------------------------------------------------------------

    /** onBaitLoaded only advances the step when the player is currently in BAIT_LOAD. */
    public static void onBaitLoadedOnlyAdvancesFromBaitLoadStep(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        setStep(helper, player, TutorialStep.HOOK_IN_WATER);

        TutorialManager.onBaitLoaded(player);
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.HOOK_IN_WATER,
            "onBaitLoaded must be a no-op when the current step is not BAIT_LOAD"
        );

        setStep(helper, player, TutorialStep.BAIT_LOAD);
        TutorialManager.onBaitLoaded(player);
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.WAITING_FOR_CAST,
            "onBaitLoaded must advance BAIT_LOAD to WAITING_FOR_CAST"
        );
        helper.succeed();
    }

    /** onHookCast advances from either WAITING_FOR_CAST or BAIT_LOAD, and is a no-op otherwise. */
    public static void onHookCastOnlyAdvancesFromCastableSteps(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer waitingPlayer = mockPlayer.get();
        TutorialManager.onHookCast(waitingPlayer);
        helper.assertTrue(
            TutorialManager.getStep(waitingPlayer) == TutorialStep.HOOK_IN_WATER,
            "onHookCast must advance WAITING_FOR_CAST to HOOK_IN_WATER"
        );

        ServerPlayer baitLoadPlayer = mockPlayer.get();
        setStep(helper, baitLoadPlayer, TutorialStep.BAIT_LOAD);
        TutorialManager.onHookCast(baitLoadPlayer);
        helper.assertTrue(
            TutorialManager.getStep(baitLoadPlayer) == TutorialStep.HOOK_IN_WATER,
            "onHookCast must advance BAIT_LOAD to HOOK_IN_WATER"
        );

        ServerPlayer questPlayer = mockPlayer.get();
        setStep(helper, questPlayer, TutorialStep.QUEST_INTRO);
        TutorialManager.onHookCast(questPlayer);
        helper.assertTrue(
            TutorialManager.getStep(questPlayer) == TutorialStep.QUEST_INTRO,
            "onHookCast must be a no-op from steps other than WAITING_FOR_CAST/BAIT_LOAD"
        );
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // advanceStep — stale/replayed client packet protection
    // -------------------------------------------------------------------------

    /** advanceStep must ignore a client-supplied fromStep that no longer matches the server's current step. */
    public static void advanceStepNoOpWhenFromStepDoesNotMatchCurrent(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        setStep(helper, player, TutorialStep.MINIGAME_INTRO);

        TutorialManager.advanceStep(player, TutorialStep.MINIGAME_CONTROL);

        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.MINIGAME_INTRO,
            "advanceStep must not change the step when fromStep doesn't match the player's actual current step"
        );
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Full documented chain, end to end
    // -------------------------------------------------------------------------

    /** Walks the entire tutorial from the default step through COMPLETE via every public hook. */
    public static void tutorialWalksFullDocumentedChainToCompletion(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();

        TutorialManager.onItemCrafted(player, copperRod());
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD, "Expected BAIT_LOAD after crafting");

        TutorialManager.onBaitLoaded(player);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.WAITING_FOR_CAST, "Expected WAITING_FOR_CAST after bait load");

        TutorialManager.onHookCast(player);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.HOOK_IN_WATER, "Expected HOOK_IN_WATER after hook cast");
        helper.assertTrue(TutorialManager.isTutorialSession(player), "isTutorialSession must be true while HOOK_IN_WATER");

        TutorialManager.onMinigameStarted(player);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.MINIGAME_INTRO, "Expected MINIGAME_INTRO after minigame start");

        TutorialManager.advanceStep(player, TutorialStep.MINIGAME_INTRO);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.MINIGAME_CONTROL, "Expected MINIGAME_CONTROL");

        TutorialManager.advanceStep(player, TutorialStep.MINIGAME_CONTROL);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.MINIGAME_CATCH, "Expected MINIGAME_CATCH");

        TutorialManager.onMinigameComplete(player);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.CATCH_RESULT, "Expected CATCH_RESULT after minigame complete");

        TutorialManager.advanceStep(player, TutorialStep.CATCH_RESULT);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.QUEST_INTRO, "Expected QUEST_INTRO");

        TutorialManager.advanceStep(player, TutorialStep.QUEST_INTRO);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.QUEST_CLAIM, "Expected QUEST_CLAIM");

        TutorialManager.onQuestClaimed(player, TutorialManager.TUTORIAL_QUEST_KEY.identifier());
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.SHOP_BROWSE, "Expected SHOP_BROWSE after claiming the tutorial quest");

        TutorialManager.advanceStep(player, TutorialStep.SHOP_BROWSE);
        helper.assertTrue(TutorialManager.getStep(player) == TutorialStep.COMPLETE, "Expected COMPLETE as the final step");

        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // onQuestClaimed — quest id guard
    // -------------------------------------------------------------------------

    /** onQuestClaimed only advances QUEST_CLAIM to SHOP_BROWSE when the claimed quest id matches the tutorial quest. */
    public static void onQuestClaimedOnlyAdvancesOnMatchingTutorialQuestId(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        setStep(helper, player, TutorialStep.QUEST_CLAIM);

        TutorialManager.onQuestClaimed(player, Identifier.fromNamespaceAndPath("fishtastic", "daily/some_other_quest"));
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.QUEST_CLAIM,
            "Claiming a non-tutorial quest must not advance the tutorial step"
        );

        TutorialManager.onQuestClaimed(player, TutorialManager.TUTORIAL_QUEST_KEY.identifier());
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.SHOP_BROWSE,
            "Claiming the tutorial quest while in QUEST_CLAIM must advance to SHOP_BROWSE"
        );
        helper.succeed();
    }
}

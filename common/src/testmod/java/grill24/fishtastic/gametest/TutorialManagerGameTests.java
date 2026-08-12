package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.tutorial.TutorialStep;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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

        TutorialManager.onItemCrafted(player);

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
        TutorialManager.onItemCrafted(player);
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD,
            "Sanity check: first craft must reach BAIT_LOAD"
        );

        TutorialManager.onItemCrafted(player);

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

    /**
     * advanceStep(player, BAIT_LOAD) is the client-polled path TutorialClientHandler uses to
     * cover creative's Inventory tab, where clicks never reach the server — must produce the same
     * transition as the direct-call path (onBaitLoaded), and stay a no-op from any other step.
     */
    public static void advanceStepBaitLoadTransitionsToWaitingForCast(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        setStep(helper, player, TutorialStep.HOOK_IN_WATER);

        TutorialManager.advanceStep(player, TutorialStep.BAIT_LOAD);
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.HOOK_IN_WATER,
            "advanceStep(BAIT_LOAD) must be a no-op when the current step isn't BAIT_LOAD"
        );

        setStep(helper, player, TutorialStep.BAIT_LOAD);
        TutorialManager.advanceStep(player, TutorialStep.BAIT_LOAD);
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.WAITING_FOR_CAST,
            "advanceStep(BAIT_LOAD) must advance BAIT_LOAD to WAITING_FOR_CAST"
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

        TutorialManager.onItemCrafted(player);
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

    // -------------------------------------------------------------------------
    // Real container-click code paths (PlayerAdvancementsMixin via the vanilla
    // minecraft:recipe_crafted trigger, FishtasticFishingRodItem#overrideOtherStackedOnMe)
    // rather than calling TutorialManager hooks directly — these are what actually run when a
    // player clicks in-game. Sets up a real CraftingMenu with real ingredients (not just the
    // result slot pre-filled) so the recipe is actually computed and RecipeCraftingHolder has
    // real recipe-used data to award, exercising the same path the advancement's
    // minecraft:recipe_crafted criterion relies on.
    // -------------------------------------------------------------------------

    /** Places real copper-fishing-rod ingredients into a real CraftingMenu's craft grid. */
    private static CraftingMenu realCraftingMenuWithRodIngredients(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(level, pos));

        // Matches copper_fishing_rod.json's pattern ("  C" / " CS" / "I S"): craft-grid slots
        // are menu slots 1-9 (CraftingMenu.CRAFT_SLOT_START), row-major from the 3x3 pattern.
        menu.getSlot(3).set(new ItemStack(Items.COPPER_INGOT));
        menu.getSlot(5).set(new ItemStack(Items.COPPER_INGOT));
        menu.getSlot(6).set(new ItemStack(Items.STRING));
        menu.getSlot(7).set(new ItemStack(Items.STICK));
        menu.getSlot(9).set(new ItemStack(Items.STRING));

        helper.assertTrue(
            menu.getSlot(CraftingMenu.RESULT_SLOT).getItem().is(FishtasticItems.COPPER_FISHING_ROD),
            "Sanity check: real ingredients in the craft grid must produce a copper fishing rod in the result slot"
        );
        return menu;
    }

    /** Shift-clicking the crafting result must reach TutorialManager too, not just a normal click (PlayerAdvancementsMixin). */
    public static void craftingRodViaShiftClickFromResultSlotAdvancesToBaitLoad(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        CraftingMenu menu = realCraftingMenuWithRodIngredients(helper, player);

        menu.clicked(CraftingMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);

        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD,
            "Shift-clicking the crafted rod out of a real crafting table's result slot must advance WAITING_FOR_CAST to BAIT_LOAD, got "
                + TutorialManager.getStep(player)
        );
        helper.succeed();
    }

    /** Same as above but via a normal (non-shift) left-click, as a control to confirm the advancement path works either way. */
    public static void craftingRodViaSimpleClickFromResultSlotAdvancesToBaitLoad(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        CraftingMenu menu = realCraftingMenuWithRodIngredients(helper, player);

        menu.clicked(CraftingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);

        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD,
            "Left-clicking the crafted rod out of a real crafting table's result slot must advance WAITING_FOR_CAST to BAIT_LOAD, got "
                + TutorialManager.getStep(player)
        );
        helper.succeed();
    }

    /** Picking up worms in the survival inventory screen and left-clicking them onto the rod sitting in the hotbar must load the bait. */
    public static void loadingBaitViaLeftClickInInventoryScreenAdvancesToWaitingForCast(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        setStep(helper, player, TutorialStep.BAIT_LOAD);
        player.getInventory().setItem(0, copperRod()); // hotbar slot 0

        InventoryMenu menu = player.inventoryMenu;
        menu.setCarried(new ItemStack(FishtasticItems.WORMS.value(), 8));

        int rodMenuSlot = 36; // hotbar slot 0 in InventoryMenu's slot layout (9 + 27 main inv slots offset)
        menu.clicked(rodMenuSlot, 0, ContainerInput.PICKUP, player);

        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.WAITING_FOR_CAST,
            "Left-clicking worms onto the rod in the hotbar must advance BAIT_LOAD to WAITING_FOR_CAST, got "
                + TutorialManager.getStep(player)
        );
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // revokeRodCraftedAdvancement — debug /tutorial reset must un-stick a re-craft
    // -------------------------------------------------------------------------

    /**
     * Without revoking the advancement on reset, the criterion stays granted forever, so
     * re-crafting the rod after a debug reset would never re-fire PlayerAdvancementsMixin and
     * the player would be stuck at WAITING_FOR_CAST. This drives the same two calls
     * TutorialCommand's reset makes (setStep back to WAITING_FOR_CAST, then revoke) and confirms
     * a second grant of the criterion re-advances the tutorial.
     */
    public static void revokingRodAdvancementAllowsReTriggeringAfterReset(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        AdvancementHolder rodAdvancement = helper.getLevel().getServer().getAdvancements().get(TutorialManager.TUTORIAL_ROD_ADVANCEMENT_ID);
        helper.assertTrue(rodAdvancement != null, "Sanity check: the tutorial rod advancement must be loaded");

        player.getAdvancements().award(rodAdvancement, TutorialManager.TUTORIAL_ROD_ADVANCEMENT_CRITERION);
        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD,
            "Sanity check: granting the rod-crafted advancement criterion must advance WAITING_FOR_CAST to BAIT_LOAD"
        );

        // Simulate a debug /tutorial reset, then re-craft the rod.
        setStep(helper, player, TutorialStep.WAITING_FOR_CAST);
        TutorialManager.revokeRodCraftedAdvancement(player);
        player.getAdvancements().award(rodAdvancement, TutorialManager.TUTORIAL_ROD_ADVANCEMENT_CRITERION);

        helper.assertTrue(
            TutorialManager.getStep(player) == TutorialStep.BAIT_LOAD,
            "Re-granting the advancement criterion after a revoke (simulating reset then re-crafting) must advance to BAIT_LOAD again, got "
                + TutorialManager.getStep(player)
        );
        helper.succeed();
    }
}

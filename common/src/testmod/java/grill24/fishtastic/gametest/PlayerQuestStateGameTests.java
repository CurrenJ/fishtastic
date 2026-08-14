package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.util.Utility;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Game tests for PlayerQuestState — pure in-memory quest-progress and shop-purchase
 * bookkeeping. No world/registry/player dependency needed; Quest/QuestObjective/ShopEntry
 * fixtures are built by hand.
 */
public final class PlayerQuestStateGameTests {

    private PlayerQuestStateGameTests() {}

    private static Quest quest(int targetCount) {
        QuestObjective objective = new QuestObjective(
            Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(targetCount),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
        return new Quest(QuestCategory.DAILY, QuestDifficulty.BRONZE, objective, new QuestReward(50, List.of()),
            Optional.empty(), false, "Test Quest", "A test quest");
    }

    private static ResourceKey<Quest> questId(String path) {
        return ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Utility.ft(path));
    }

    private static ShopEntry shopEntry(int cost, int dailyMaxPurchases) {
        return new ShopEntry("Test Item", "A test item", cost, 1.0f, List.of(), dailyMaxPurchases, false, false, List.of());
    }

    private static ResourceKey<ShopEntry> shopEntryId(String path) {
        return ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, Utility.ft(path));
    }

    /**
     * currentCount can exceed the target — a lifetime-count tier freezes wherever it happened to
     * complete (11/10), and a batched catch can overshoot in one go. The log must render a clamped
     * number so a finished tier reads "10/10" instead of "11/10" while a live tier still reports
     * real progress.
     */
    public static void displayCountClampsOvershootToTheTarget(GameTestHelper helper) {
        PlayerQuestState.QuestProgress overshot =
            new PlayerQuestState.QuestProgress(11, 0L, true, false, List.of());
        helper.assertTrue(overshot.displayCount(10) == 10,
            "A completed tier that overshot must display 10/10, got " + overshot.displayCount(10));

        PlayerQuestState.QuestProgress inProgress =
            new PlayerQuestState.QuestProgress(23, 0L, false, false, List.of());
        helper.assertTrue(inProgress.displayCount(25) == 23,
            "An unfinished tier must display its real count, got " + inProgress.displayCount(25));

        helper.assertTrue(inProgress.displayCount(0) == 23,
            "A zero/unknown target must not clamp the count to zero");

        PlayerQuestState.QuestProgress exact =
            new PlayerQuestState.QuestProgress(10, 0L, true, true, List.of());
        helper.assertTrue(exact.displayCount(10) == 10, "An exact completion must display unchanged");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Quest progress
    // -------------------------------------------------------------------------

    /**
     * getProgress on a quest that's never been touched returns the documented default.
     */
    public static void getProgressDefaultsForUntouchedQuest(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        PlayerQuestState.QuestProgress progress = state.getProgress(questId("untouched"));

        helper.assertTrue(progress.currentCount() == 0, "Default count must be 0");
        helper.assertTrue(progress.lastResetGameDay() == -1, "Default lastResetGameDay must be -1");
        helper.assertTrue(!progress.completed(), "Default completed must be false");
        helper.assertTrue(!progress.claimed(), "Default claimed must be false");
        helper.succeed();
    }

    /**
     * incrementCount raises currentCount and flips completed exactly when the target is reached.
     */
    public static void incrementCountRaisesCountAndFlipsCompleted(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        int targetCount = 3;
        ResourceKey<Quest> id = questId("increment_test");

        state.incrementCount(id, targetCount, 10L);
        helper.assertTrue(state.getProgress(id).currentCount() == 1, "Count must be 1 after first increment");
        helper.assertTrue(!state.getProgress(id).completed(), "Must not be completed below target count");

        state.incrementCount(id, targetCount, 10L);
        helper.assertTrue(!state.getProgress(id).completed(), "Must not be completed one below target count");

        state.incrementCount(id, targetCount, 10L);
        helper.assertTrue(state.getProgress(id).currentCount() == 3, "Count must be 3 after third increment");
        helper.assertTrue(state.getProgress(id).completed(), "Must be completed exactly at target count");
        helper.succeed();
    }

    /**
     * canClaim is false before completion, true exactly between completion and claim,
     * and false again after claim() (the claimed guard).
     */
    public static void canClaimTrueOnlyBetweenCompletionAndClaim(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        Quest q = quest(2);
        int targetCount = 2;
        ResourceKey<Quest> id = questId("can_claim_test");

        state.incrementCount(id, targetCount, 1L);
        helper.assertTrue(!state.canClaim(id, targetCount), "Must not be claimable before completion");

        state.incrementCount(id, targetCount, 1L);
        helper.assertTrue(state.canClaim(id, targetCount), "Must be claimable immediately after completion");

        state.claim(id, q.reward().questTokens());
        helper.assertTrue(!state.canClaim(id, targetCount), "Must not be claimable again after claim()");
        helper.succeed();
    }

    /**
     * claim adds the token reward to the balance and sets claimed=true without resetting currentCount.
     */
    public static void claimAddsTokensWithoutResettingCount(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        Quest q = quest(1);
        ResourceKey<Quest> id = questId("claim_test");

        state.incrementCount(id, 1, 1L);
        int balanceBefore = state.getTokenBalance();
        state.claim(id, q.reward().questTokens());

        helper.assertTrue(state.getTokenBalance() == balanceBefore + q.reward().questTokens(),
            "Token balance must increase by the reward amount");
        helper.assertTrue(state.getProgress(id).claimed(), "Progress must be marked claimed");
        helper.assertTrue(state.getProgress(id).currentCount() == 1,
            "claim() must not reset currentCount, got " + state.getProgress(id).currentCount());
        helper.succeed();
    }

    /**
     * resetDailyIfNeeded resets progress to a fresh zero state when called on a later day,
     * and is a no-op when called again on the same day.
     */
    public static void resetDailyIfNeededOnlyOnNewDay(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        ResourceKey<Quest> id = questId("reset_test");

        state.incrementCount(id, 1, 5L);
        state.resetDailyIfNeeded(id, 5L);
        helper.assertTrue(state.getProgress(id).currentCount() == 1,
            "Same-day reset must be a no-op, count was reset unexpectedly");

        state.resetDailyIfNeeded(id, 6L);
        PlayerQuestState.QuestProgress reset = state.getProgress(id);
        helper.assertTrue(reset.currentCount() == 0, "New-day reset must zero the count");
        helper.assertTrue(reset.lastResetGameDay() == 6L, "New-day reset must stamp the new day");
        helper.assertTrue(!reset.completed() && !reset.claimed(), "New-day reset must clear completed/claimed");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Shop / economy (purchase guard)
    // -------------------------------------------------------------------------

    /**
     * purchase() returns false and leaves the balance unchanged when the player can't afford the entry.
     */
    public static void purchaseFailsWhenBalanceTooLow(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        ShopEntry entry = shopEntry(100, 0);
        ResourceKey<ShopEntry> id = shopEntryId("too_expensive");

        boolean result = state.purchase(id, entry);
        helper.assertTrue(!result, "Purchase must fail when balance is 0 and cost is 100");
        helper.assertTrue(state.getTokenBalance() == 0, "Balance must be unchanged after a failed purchase");
        helper.assertTrue(state.getPurchaseCountSnapshot().getOrDefault(id.identifier(), 0) == 0,
            "Purchase count must be unchanged after a failed purchase");
        helper.succeed();
    }

    /**
     * purchase() returns false once the entry's maxPurchases limit has been reached.
     */
    public static void purchaseFailsWhenMaxPurchasesReached(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        ShopEntry entry = shopEntry(10, 1);
        ResourceKey<ShopEntry> id = shopEntryId("limited");
        state.claim(questId("token_grant"), 100);

        helper.assertTrue(state.purchase(id, entry), "First purchase within the limit must succeed");
        boolean second = state.purchase(id, entry);
        helper.assertTrue(!second, "Second purchase must fail once maxPurchases is reached");

        int balanceAfterFirst = 100 - entry.cost();
        helper.assertTrue(state.getTokenBalance() == balanceAfterFirst,
            "Balance must not be deducted again on the rejected second purchase");
        helper.succeed();
    }

    /**
     * maxPurchases == 0 means unlimited — purchase() must keep succeeding past any count.
     */
    public static void purchaseZeroMaxPurchasesIsUnlimited(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        ShopEntry entry = shopEntry(1, 0);
        ResourceKey<ShopEntry> id = shopEntryId("unlimited");
        state.claim(questId("token_grant"), 1000);

        for (int i = 0; i < 10; i++) {
            helper.assertTrue(state.purchase(id, entry),
                "Purchase #" + (i + 1) + " must succeed when maxPurchases is 0 (unlimited)");
        }
        helper.succeed();
    }

    /**
     * resetDailyPurchasesIfNeeded is a no-op within the same day (the cap still blocks further
     * purchases), but clears every purchase count on a later day so the cap can be hit again.
     */
    public static void resetDailyPurchasesIfNeededOnlyOnNewDay(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        ShopEntry entry = shopEntry(10, 1);
        ResourceKey<ShopEntry> id = shopEntryId("daily_limited");
        state.claim(questId("token_grant"), 100);

        // Mirrors production: ServerTickHandler fires the reset once at the start of a day,
        // before any purchases that day, anchoring lastPurchaseResetDay to that day.
        state.resetDailyPurchasesIfNeeded(5L);
        helper.assertTrue(state.purchase(id, entry), "First purchase within the daily limit must succeed");

        state.resetDailyPurchasesIfNeeded(5L);
        helper.assertTrue(!state.purchase(id, entry), "Same-day reset must be a no-op, cap must still block");

        state.resetDailyPurchasesIfNeeded(6L);
        helper.assertTrue(state.purchase(id, entry), "New-day reset must clear the count so the cap can be hit again");
        helper.succeed();
    }

    /**
     * A successful purchase deducts the cost from the balance and increments the purchase count.
     */
    public static void purchaseSucceedsDeductsAndIncrementsCount(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        ShopEntry entry = shopEntry(30, 5);
        ResourceKey<ShopEntry> id = shopEntryId("normal");
        state.claim(questId("token_grant"), 100);

        boolean result = state.purchase(id, entry);
        helper.assertTrue(result, "Purchase must succeed when affordable and under the limit");
        helper.assertTrue(state.getTokenBalance() == 70, "Balance must be 100 - 30 = 70, got " + state.getTokenBalance());
        helper.assertTrue(state.getPurchaseCountSnapshot().get(id.identifier()) == 1,
            "Purchase count must be incremented to 1");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Snapshots
    // -------------------------------------------------------------------------

    /**
     * getProgressSnapshot() and getPurchaseCountSnapshot() reflect prior mutations.
     */
    public static void snapshotsReflectMutations(GameTestHelper helper) {
        PlayerQuestState state = new PlayerQuestState();
        ResourceKey<Quest> qId = questId("snapshot_quest");
        state.incrementCount(qId, 1, 1L);

        ShopEntry entry = shopEntry(0, 0);
        ResourceKey<ShopEntry> sId = shopEntryId("snapshot_entry");
        state.purchase(sId, entry);

        Map<Identifier, PlayerQuestState.QuestProgress> progressSnapshot = state.getProgressSnapshot();
        helper.assertTrue(progressSnapshot.containsKey(qId.identifier()),
            "Progress snapshot must contain the mutated quest id");
        helper.assertTrue(progressSnapshot.get(qId.identifier()).currentCount() == 1,
            "Progress snapshot must reflect the incremented count");

        Map<Identifier, Integer> purchaseSnapshot = state.getPurchaseCountSnapshot();
        helper.assertTrue(purchaseSnapshot.getOrDefault(sId.identifier(), 0) == 1,
            "Purchase count snapshot must reflect the successful purchase");
        helper.succeed();
    }
}

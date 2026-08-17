package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Game tests for FishCatchSavedData — recording, leaderboards, guard clauses.
 */
public final class FishCatchDataGameTests {

    private FishCatchDataGameTests() {}

    private static FishCatchSavedData freshData() {
        return new FishCatchSavedData();
    }

    private static ItemStack bluegillWithSizeAndQuality(float size, FishQuality.Quality quality) {
        ItemStack stack = new ItemStack(FishtasticItems.BLUEGILL.value());
        ItemSizeHelper.setSize(stack, size);
        FishQualityHelper.setQuality(stack, quality);
        return stack;
    }

    // -------------------------------------------------------------------------
    // Guard clauses
    // -------------------------------------------------------------------------

    /**
     * Recording a non-fish item (e.g. dirt) must not appear in any leaderboard.
     */
    public static void recordingNonFishIsIgnored(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemSizeHelper.setSize(dirt, 50f);
        data.recordCatch(player, "TestPlayer", dirt);

        List<FishCatchSavedData.PersonalBestSizeEntry> entries =
            data.getPersonalBestSizes(player, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
        helper.assertTrue(entries.isEmpty(), "Non-fish item must not appear in personal best sizes");
        helper.succeed();
    }

    /**
     * Recording a fish with size = 0 must not appear in any leaderboard.
     */
    public static void recordingZeroSizeIsIgnored(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();
        ItemStack fish = new ItemStack(FishtasticItems.BLUEGILL.value());
        ItemSizeHelper.setSize(fish, 0f);
        FishQualityHelper.setQuality(fish, FishQuality.Quality.COMMON);
        data.recordCatch(player, "TestPlayer", fish);

        List<FishCatchSavedData.PersonalBestSizeEntry> entries =
            data.getPersonalBestSizes(player, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
        helper.assertTrue(entries.isEmpty(), "Zero-size fish must not appear in personal best sizes");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Catch counting
    // -------------------------------------------------------------------------

    /**
     * Each call to recordCatch increments total catch count by 1.
     */
    public static void catchCountIncrements(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();

        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(40f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(45f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(50f, FishQuality.Quality.COMMON));

        List<FishCatchSavedData.PersonalCatchCountEntry> entries =
            data.getPersonalCatchCounts(player, FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC);
        helper.assertTrue(entries.size() == 1, "Must have exactly 1 fish type entry");
        helper.assertTrue(entries.get(0).totalCatches() == 3, "Catch count must be 3, got " + entries.get(0).totalCatches());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Best size tracking
    // -------------------------------------------------------------------------

    /**
     * Best size only updates when a larger fish is caught.
     */
    public static void bestSizeOnlyUpdatesOnImprovement(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();

        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(40f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(60f, FishQuality.Quality.RARE));
        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(55f, FishQuality.Quality.EPIC));

        List<FishCatchSavedData.PersonalBestSizeEntry> entries =
            data.getPersonalBestSizes(player, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
        helper.assertTrue(entries.size() == 1, "Must have exactly 1 entry for bluegill");

        FishCatchSavedData.PersonalBestSizeEntry best = entries.get(0);
        helper.assertTrue(best.bestSize() == 60f, "Best size must be 60, got " + best.bestSize());
        helper.assertTrue(best.bestQuality() == FishQuality.Quality.RARE,
            "Best quality must be RARE (the catch that produced the best size)");
        helper.succeed();
    }

    /**
     * A smaller catch after a larger one does not overwrite the best size.
     */
    public static void smallerCatchDoesNotOverrideBest(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();

        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(80f, FishQuality.Quality.LEGENDARY));
        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(30f, FishQuality.Quality.COMMON));

        List<FishCatchSavedData.PersonalBestSizeEntry> entries =
            data.getPersonalBestSizes(player, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
        helper.assertTrue(entries.get(0).bestSize() == 80f,
            "Best size must remain 80 after smaller catch, got " + entries.get(0).bestSize());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Personal vs global isolation
    // -------------------------------------------------------------------------

    /**
     * Personal leaderboards are isolated per UUID — PlayerA cannot see PlayerB's data.
     */
    public static void personalLeaderboardsAreIsolatedByUuid(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        data.recordCatch(playerA, "PlayerA", bluegillWithSizeAndQuality(50f, FishQuality.Quality.COMMON));
        data.recordCatch(playerB, "PlayerB", bluegillWithSizeAndQuality(70f, FishQuality.Quality.RARE));

        List<FishCatchSavedData.PersonalBestSizeEntry> aEntries =
            data.getPersonalBestSizes(playerA, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
        helper.assertTrue(aEntries.get(0).bestSize() == 50f,
            "PlayerA best must be 50, got " + aEntries.get(0).bestSize());

        List<FishCatchSavedData.PersonalBestSizeEntry> bEntries =
            data.getPersonalBestSizes(playerB, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
        helper.assertTrue(bEntries.get(0).bestSize() == 70f,
            "PlayerB best must be 70, got " + bEntries.get(0).bestSize());
        helper.succeed();
    }

    /**
     * Global best size leaderboard tracks the highest per-fish entry across all players.
     */
    public static void globalBestSizeTracksHighestAcrossPlayers(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        data.recordCatch(playerA, "PlayerA", bluegillWithSizeAndQuality(50f, FishQuality.Quality.COMMON));
        data.recordCatch(playerB, "PlayerB", bluegillWithSizeAndQuality(90f, FishQuality.Quality.LEGENDARY));

        List<FishCatchSavedData.GlobalBestSizeEntry> global =
            data.getGlobalBestSizes(FishCatchSavedData.GLOBAL_BEST_SIZE_DESC);
        helper.assertTrue(global.size() == 1, "Must have 1 entry for bluegill in global");

        FishCatchSavedData.GlobalBestSizeEntry top = global.get(0);
        helper.assertTrue(top.bestSize() == 90f, "Global best must be 90 (PlayerB), got " + top.bestSize());
        helper.assertTrue(top.playerUuid().equals(playerB), "Global best must be held by PlayerB");
        helper.assertTrue("PlayerB".equals(top.playerName()), "Global player name must be PlayerB");
        helper.succeed();
    }

    /**
     * Global catch count leaderboard sums all catches per player across all fish types.
     */
    public static void globalCatchCountSumsAllFishTypes(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();

        ItemStack northern = new ItemStack(FishtasticItems.NORTHERN_PIKE.value());
        ItemSizeHelper.setSize(northern, 55f);
        FishQualityHelper.setQuality(northern, FishQuality.Quality.UNCOMMON);

        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(40f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(45f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", northern);

        List<FishCatchSavedData.GlobalCatchCountEntry> global =
            data.getGlobalCatchCounts(FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC);
        helper.assertTrue(global.size() == 1, "Must have 1 player entry in global count");
        helper.assertTrue(global.get(0).totalCatches() == 3,
            "Total catches across fish types must be 3, got " + global.get(0).totalCatches());
        helper.succeed();
    }

    /**
     * The global best-size board tracks each fish species independently — one player leading on
     * bluegill must not affect who leads on northern pike.
     */
    public static void globalBestSizeTracksIndependentlyPerFishType(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        ItemStack pikeA = new ItemStack(FishtasticItems.NORTHERN_PIKE.value());
        ItemSizeHelper.setSize(pikeA, 40f);
        FishQualityHelper.setQuality(pikeA, FishQuality.Quality.COMMON);
        ItemStack pikeB = new ItemStack(FishtasticItems.NORTHERN_PIKE.value());
        ItemSizeHelper.setSize(pikeB, 95f);
        FishQualityHelper.setQuality(pikeB, FishQuality.Quality.LEGENDARY);

        // PlayerA leads on bluegill; PlayerB leads on northern pike.
        data.recordCatch(playerA, "PlayerA", bluegillWithSizeAndQuality(80f, FishQuality.Quality.EPIC));
        data.recordCatch(playerB, "PlayerB", bluegillWithSizeAndQuality(30f, FishQuality.Quality.COMMON));
        data.recordCatch(playerA, "PlayerA", pikeA);
        data.recordCatch(playerB, "PlayerB", pikeB);

        List<FishCatchSavedData.GlobalBestSizeEntry> global =
            data.getGlobalBestSizes(FishCatchSavedData.GLOBAL_BEST_SIZE_DESC);
        helper.assertTrue(global.size() == 2, "Must have 2 entries, one per fish type, got " + global.size());

        Identifier bluegillId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(FishtasticItems.BLUEGILL.value());
        for (FishCatchSavedData.GlobalBestSizeEntry entry : global) {
            if (entry.fishType().equals(bluegillId)) {
                helper.assertTrue(entry.bestSize() == 80f && entry.playerUuid().equals(playerA),
                    "Bluegill leader must be PlayerA at 80, got " + entry.playerName() + " at " + entry.bestSize());
            } else {
                helper.assertTrue(entry.bestSize() == 95f && entry.playerUuid().equals(playerB),
                    "Northern pike leader must be PlayerB at 95, got " + entry.playerName() + " at " + entry.bestSize());
            }
        }
        helper.succeed();
    }

    /**
     * A tie on the global best-size board must not duplicate the entry — whichever of the two
     * tied players is picked, exactly one entry survives and it reports the tied size. (Which
     * player wins a true tie is an implementation detail of map iteration order and deliberately
     * not asserted here — only that the dedup and size reporting stay correct.)
     */
    public static void globalBestSizeTieKeepsSingleEntry(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        data.recordCatch(playerA, "PlayerA", bluegillWithSizeAndQuality(60f, FishQuality.Quality.RARE));
        data.recordCatch(playerB, "PlayerB", bluegillWithSizeAndQuality(60f, FishQuality.Quality.RARE));

        List<FishCatchSavedData.GlobalBestSizeEntry> global =
            data.getGlobalBestSizes(FishCatchSavedData.GLOBAL_BEST_SIZE_DESC);
        helper.assertTrue(global.size() == 1, "A tie must still collapse to 1 entry, got " + global.size());
        helper.assertTrue(global.get(0).bestSize() == 60f,
            "Tied entry must report the tied size 60, got " + global.get(0).bestSize());
        helper.assertTrue(global.get(0).playerUuid().equals(playerA) || global.get(0).playerUuid().equals(playerB),
            "Tied entry must belong to one of the two tied players");
        helper.succeed();
    }

    /**
     * The global catch-count board ranks multiple players against each other, not just one
     * player's own total.
     */
    public static void globalCatchCountRanksMultiplePlayers(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        UUID playerC = UUID.randomUUID();

        data.recordCatch(playerA, "PlayerA", bluegillWithSizeAndQuality(10f, FishQuality.Quality.COMMON));
        for (int i = 0; i < 5; i++) {
            data.recordCatch(playerB, "PlayerB", bluegillWithSizeAndQuality(10f, FishQuality.Quality.COMMON));
        }
        for (int i = 0; i < 3; i++) {
            data.recordCatch(playerC, "PlayerC", bluegillWithSizeAndQuality(10f, FishQuality.Quality.COMMON));
        }

        List<FishCatchSavedData.GlobalCatchCountEntry> desc =
            data.getGlobalCatchCounts(FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC);
        helper.assertTrue(desc.size() == 3, "Must have 3 player entries, got " + desc.size());
        helper.assertTrue(desc.get(0).playerUuid().equals(playerB) && desc.get(0).totalCatches() == 5,
            "DESC: PlayerB (5 catches) must rank first, got " + desc.get(0).playerName() + "=" + desc.get(0).totalCatches());
        helper.assertTrue(desc.get(2).playerUuid().equals(playerA) && desc.get(2).totalCatches() == 1,
            "DESC: PlayerA (1 catch) must rank last, got " + desc.get(2).playerName() + "=" + desc.get(2).totalCatches());

        List<FishCatchSavedData.GlobalCatchCountEntry> asc =
            data.getGlobalCatchCounts(FishCatchSavedData.GLOBAL_CATCH_COUNT_ASC);
        helper.assertTrue(asc.get(0).playerUuid().equals(playerA),
            "ASC: PlayerA (1 catch) must rank first, got " + asc.get(0).playerName());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Sorting
    // -------------------------------------------------------------------------

    /**
     * DESC/ASC ordering on the personal catch-count board, across multiple fish types for the
     * same player.
     */
    public static void personalCatchCountSortOrder(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();

        ItemStack pike = new ItemStack(FishtasticItems.NORTHERN_PIKE.value());
        ItemSizeHelper.setSize(pike, 50f);
        FishQualityHelper.setQuality(pike, FishQuality.Quality.COMMON);

        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(40f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(41f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(42f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", pike);

        List<FishCatchSavedData.PersonalCatchCountEntry> desc =
            data.getPersonalCatchCounts(player, FishCatchSavedData.PERSONAL_CATCH_COUNT_DESC);
        helper.assertTrue(desc.get(0).totalCatches() == 3 && desc.get(1).totalCatches() == 1,
            "DESC: bluegill (3 catches) must lead pike (1 catch), got " + desc.get(0).totalCatches() + ", " + desc.get(1).totalCatches());

        List<FishCatchSavedData.PersonalCatchCountEntry> asc =
            data.getPersonalCatchCounts(player, FishCatchSavedData.PERSONAL_CATCH_COUNT_ASC);
        helper.assertTrue(asc.get(0).totalCatches() == 1 && asc.get(1).totalCatches() == 3,
            "ASC: pike (1 catch) must lead bluegill (3 catches), got " + asc.get(0).totalCatches() + ", " + asc.get(1).totalCatches());
        helper.succeed();
    }

    /**
     * DESC/ASC ordering on the global best-size board, across multiple fish-type entries.
     */
    public static void globalBestSizeSortOrder(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();

        ItemStack pike = new ItemStack(FishtasticItems.NORTHERN_PIKE.value());
        ItemSizeHelper.setSize(pike, 90f);
        FishQualityHelper.setQuality(pike, FishQuality.Quality.RARE);

        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(50f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", pike);

        List<FishCatchSavedData.GlobalBestSizeEntry> desc =
            data.getGlobalBestSizes(FishCatchSavedData.GLOBAL_BEST_SIZE_DESC);
        helper.assertTrue(desc.get(0).bestSize() > desc.get(1).bestSize(),
            "DESC sort: first global entry must have larger best size");

        List<FishCatchSavedData.GlobalBestSizeEntry> asc =
            data.getGlobalBestSizes(FishCatchSavedData.GLOBAL_BEST_SIZE_ASC);
        helper.assertTrue(asc.get(0).bestSize() < asc.get(1).bestSize(),
            "ASC sort: first global entry must have smaller best size");
        helper.succeed();
    }

    /**
     * DESC comparator returns largest best size first; ASC returns smallest first.
     */
    public static void personalBestSizeSortOrder(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID player = UUID.randomUUID();

        ItemStack pike = new ItemStack(FishtasticItems.NORTHERN_PIKE.value());
        ItemSizeHelper.setSize(pike, 90f);
        FishQualityHelper.setQuality(pike, FishQuality.Quality.RARE);

        data.recordCatch(player, "TestPlayer", bluegillWithSizeAndQuality(50f, FishQuality.Quality.COMMON));
        data.recordCatch(player, "TestPlayer", pike);

        List<FishCatchSavedData.PersonalBestSizeEntry> desc =
            data.getPersonalBestSizes(player, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
        helper.assertTrue(desc.get(0).bestSize() > desc.get(1).bestSize(),
            "DESC sort: first entry must have larger best size");

        List<FishCatchSavedData.PersonalBestSizeEntry> asc =
            data.getPersonalBestSizes(player, FishCatchSavedData.PERSONAL_BEST_SIZE_ASC);
        helper.assertTrue(asc.get(0).bestSize() < asc.get(1).bestSize(),
            "ASC sort: first entry must have smaller best size");

        helper.succeed();
    }

    /**
     * Unknown player UUID returns empty list without throwing.
     */
    public static void unknownPlayerReturnsEmpty(GameTestHelper helper) {
        FishCatchSavedData data = freshData();
        UUID nobody = UUID.randomUUID();

        List<FishCatchSavedData.PersonalBestSizeEntry> entries =
            data.getPersonalBestSizes(nobody, FishCatchSavedData.PERSONAL_BEST_SIZE_DESC);
        helper.assertTrue(entries.isEmpty(), "Unknown player must return empty list");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Global cleanup goal — shared weekly counter, contribution tracking, payouts
    // -------------------------------------------------------------------------

    /** The threshold is fixed at 200 — verified here so tests below can rely on it without re-deriving it. */
    private static final int CLEANUP_GOAL_THRESHOLD = 200;

    /**
     * A contribution under the threshold accumulates but reports no crossed threshold;
     * a follow-up contribution that reaches exactly 200 reports that threshold.
     */
    public static void recordTrashContributionAccumulatesTotal(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishCatchSavedData data = freshData();
        ServerPlayer player = mockPlayer.get();

        List<Integer> firstCrossed = data.recordTrashContribution(player, 50);
        helper.assertTrue(firstCrossed.isEmpty(), "50 trash must not cross the 200 threshold yet, got " + firstCrossed);
        helper.assertTrue(data.getCleanupGoalTotal() == 50, "Total must be 50 after first contribution, got " + data.getCleanupGoalTotal());

        List<Integer> secondCrossed = data.recordTrashContribution(player, 150);
        helper.assertTrue(secondCrossed.equals(List.of(CLEANUP_GOAL_THRESHOLD)),
            "Reaching exactly 200 must report threshold 200, got " + secondCrossed);
        helper.assertTrue(data.getCleanupGoalTotal() == 200, "Total must be 200 after second contribution, got " + data.getCleanupGoalTotal());
        helper.succeed();
    }

    /**
     * A single large contribution that spans multiple thresholds must report every threshold crossed,
     * not just the latest one — each represents a separate payout.
     */
    public static void recordTrashContributionCanCrossMultipleThresholdsAtOnce(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishCatchSavedData data = freshData();
        ServerPlayer player = mockPlayer.get();

        List<Integer> crossed = data.recordTrashContribution(player, 450);
        helper.assertTrue(crossed.equals(List.of(200, 400)),
            "A 450-trash contribution from 0 must cross both 200 and 400, got " + crossed);
        helper.succeed();
    }

    /**
     * Zero (or negative) amounts must be a safe no-op — guards against trash-tagged stacks
     * with a zero count ever nudging the shared counter.
     */
    public static void recordTrashContributionIgnoresNonPositiveAmounts(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishCatchSavedData data = freshData();
        ServerPlayer player = mockPlayer.get();

        helper.assertTrue(data.recordTrashContribution(player, 0).isEmpty(), "Zero amount must report no crossed thresholds");
        helper.assertTrue(data.recordTrashContribution(player, -5).isEmpty(), "Negative amount must report no crossed thresholds");
        helper.assertTrue(data.getCleanupGoalTotal() == 0, "Total must remain 0 after non-positive contributions, got " + data.getCleanupGoalTotal());
        helper.succeed();
    }

    /**
     * Crossing a threshold splits the reward pool across every contributor that period,
     * proportional to their share — the bigger contributor must end up with more tokens.
     */
    public static void crossingThresholdPaysOutTokensProportionally(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishCatchSavedData data = freshData();
        ServerPlayer playerA = mockPlayer.get();
        ServerPlayer playerB = mockPlayer.get();

        data.recordTrashContribution(playerA, 120); // 60% of the 200 needed to cross
        data.recordTrashContribution(playerB, 80);  // 40% of the 200 needed to cross — crosses the threshold

        int tokensA = data.getOrCreateQuestState(playerA).getTokenBalance();
        int tokensB = data.getOrCreateQuestState(playerB).getTokenBalance();
        helper.assertTrue(tokensA > 0 && tokensB > 0, "Both contributors must receive some tokens, got A=" + tokensA + " B=" + tokensB);
        helper.assertTrue(tokensA > tokensB, "The 60% contributor must receive more tokens than the 40% contributor, got A=" + tokensA + " B=" + tokensB);
        helper.assertTrue(tokensA + tokensB == 50, "The full 50-token reward pool for one threshold crossing must be fully distributed, got " + (tokensA + tokensB));
        helper.succeed();
    }

    /**
     * resetCleanupGoalIfNeeded must be idempotent within the same week, and must wipe
     * contributions back to zero once a new week starts.
     */
    public static void resetCleanupGoalIfNeededWipesContributionsOnNewWeek(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishCatchSavedData data = freshData();
        ServerPlayer player = mockPlayer.get();

        data.resetCleanupGoalIfNeeded(0);
        data.recordTrashContribution(player, 80);
        helper.assertTrue(data.getCleanupGoalTotal() == 80, "Sanity check: total must be 80 before any rollover");

        data.resetCleanupGoalIfNeeded(0);
        helper.assertTrue(data.getCleanupGoalTotal() == 80, "Calling reset again within the same week must not wipe progress");

        data.resetCleanupGoalIfNeeded(1);
        helper.assertTrue(data.getCleanupGoalTotal() == 0, "Rolling into a new week must wipe the total back to 0, got " + data.getCleanupGoalTotal());
        helper.succeed();
    }

    /**
     * getCleanupGoalContributors must list every contributor with their raw contribution count,
     * sortable the same way the global catch-count leaderboard is.
     */
    public static void getCleanupGoalContributorsListsAllContributors(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        FishCatchSavedData data = freshData();
        ServerPlayer playerA = mockPlayer.get();
        ServerPlayer playerB = mockPlayer.get();

        data.recordTrashContribution(playerA, 30);
        data.recordTrashContribution(playerB, 70);

        List<FishCatchSavedData.GlobalCatchCountEntry> contributors =
            data.getCleanupGoalContributors(FishCatchSavedData.GLOBAL_CATCH_COUNT_DESC);
        helper.assertTrue(contributors.size() == 2, "Must list both contributors, got " + contributors.size());
        helper.assertTrue(contributors.get(0).totalCatches() == 70,
            "DESC order must put the larger contributor first, got " + contributors.get(0).totalCatches());
        helper.succeed();
    }
}

package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

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

    // -------------------------------------------------------------------------
    // Sorting
    // -------------------------------------------------------------------------

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
}

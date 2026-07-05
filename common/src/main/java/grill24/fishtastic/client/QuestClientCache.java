package grill24.fishtastic.client;

import grill24.fishtastic.client.util.ClientTickHandler;
import grill24.fishtastic.network.CleanupGoalProgress;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class QuestClientCache {
    /** Must match the day length {@code ServerTickHandler}/{@code QuestTracker} use to compute {@code currentDay}. */
    private static final long DAY_TICKS = 24000L;
    /** Must match the period length {@code FishCatchSavedData#resetCleanupGoalIfNeeded} anchors to. */
    private static final long CLEANUP_GOAL_PERIOD_TICKS = DAY_TICKS * 7L;

    private static Map<Identifier, PlayerQuestState.QuestProgress> questProgress = new HashMap<>();
    private static int tokenBalance = 0;
    private static Map<Identifier, Integer> purchaseCounts = new HashMap<>();
    private static CleanupGoalProgress cleanupGoal = CleanupGoalProgress.EMPTY;
    private static boolean isInitialSync = true;
    private static QuestProgressListener listener;
    private static CleanupGoalMilestoneListener milestoneListener;

    // Countdown extrapolation: the server's overworld game time as of the last sync, paired
    // with the client's own tick counter at that moment. Since both client and server tick at
    // 20/s absent lag, the current server time can be estimated between syncs by adding elapsed
    // client ticks — avoiding the need for a dedicated per-tick sync packet just for a countdown.
    private static long syncedServerGameTime = -1;
    private static float syncedClientTick = 0f;

    @FunctionalInterface
    public interface QuestProgressListener {
        void onProgress(Identifier questId, int oldCount, int newCount, int targetCount,
                        boolean completed, ItemStack triggeringItem);
    }

    @FunctionalInterface
    public interface CleanupGoalMilestoneListener {
        void onMilestone(int milestoneReached, int threshold);
    }

    /** Register a listener for quest progress changes. Called once during client init. */
    public static void setListener(QuestProgressListener l) {
        listener = l;
    }

    /** Register a listener fired when the server announces a newly crossed cleanup-goal threshold. */
    public static void setMilestoneListener(CleanupGoalMilestoneListener l) {
        milestoneListener = l;
    }

    /**
     * Update the cache with new progress data.
     * @param triggeringItems map of quest ID → ItemStack for quests that should trigger
     *                        a notification. Only quests present in this map will fire
     *                        the listener. The server populates this only for quests
     *                        that crossed a notification interval or were completed.
     */
    public static void update(Map<Identifier, PlayerQuestState.QuestProgress> progress, int tokens,
                              Map<Identifier, ItemStack> triggeringItems,
                              Map<Identifier, Integer> newPurchaseCounts) {
        update(progress, tokens, triggeringItems, newPurchaseCounts, CleanupGoalProgress.EMPTY, -1L);
    }

    public static void update(Map<Identifier, PlayerQuestState.QuestProgress> progress, int tokens,
                              Map<Identifier, ItemStack> triggeringItems,
                              Map<Identifier, Integer> newPurchaseCounts,
                              CleanupGoalProgress newCleanupGoal,
                              long serverGameTime) {
        // Diff old vs new and fire listener only for quests explicitly flagged for notification
        if (!isInitialSync && listener != null) {
            Map<Identifier, PlayerQuestState.QuestProgress> oldMap = new HashMap<>(questProgress);
            for (Map.Entry<Identifier, PlayerQuestState.QuestProgress> entry : progress.entrySet()) {
                Identifier questId = entry.getKey();
                PlayerQuestState.QuestProgress newProg = entry.getValue();
                PlayerQuestState.QuestProgress oldProg = oldMap.get(questId);
                int oldCount = oldProg != null ? oldProg.currentCount() : 0;
                int newCount = newProg.currentCount();
                // Only fire listener for quests the server explicitly flagged for notification.
                // The server only includes quests that crossed a notification_interval boundary
                // or were just completed.
                if (triggeringItems.containsKey(questId) && newCount > oldCount) {
                    ItemStack triggerItem = triggeringItems.get(questId);
                    listener.onProgress(questId, oldCount, newCount, 0, newProg.completed(), triggerItem);
                }
            }
        }
        if (!isInitialSync && milestoneListener != null && newCleanupGoal.milestoneReached() != 0) {
            milestoneListener.onMilestone(newCleanupGoal.milestoneReached(), newCleanupGoal.threshold());
        }
        isInitialSync = false;

        questProgress = new HashMap<>(progress);
        tokenBalance = tokens;
        purchaseCounts = new HashMap<>(newPurchaseCounts);
        cleanupGoal = newCleanupGoal;
        if (serverGameTime >= 0) {
            syncedServerGameTime = serverGameTime;
            syncedClientTick = ClientTickHandler.totalTicks();
        }
    }

    public static void update(Map<Identifier, PlayerQuestState.QuestProgress> progress, int tokens,
                              Map<Identifier, ItemStack> triggeringItems) {
        update(progress, tokens, triggeringItems, Map.of(), CleanupGoalProgress.EMPTY, -1L);
    }

    /** Backward-compatible overload for callers that don't have triggering items. */
    public static void update(Map<Identifier, PlayerQuestState.QuestProgress> progress, int tokens) {
        update(progress, tokens, Map.of(), Map.of(), CleanupGoalProgress.EMPTY, -1L);
    }

    public static int getCleanupGoalTotal() {
        return cleanupGoal.total();
    }

    public static int getCleanupGoalThreshold() {
        return cleanupGoal.threshold();
    }

    public static Map<Identifier, PlayerQuestState.QuestProgress> getQuestProgress() {
        return questProgress;
    }

    public static int getTokenBalance() {
        return tokenBalance;
    }

    public static PlayerQuestState.QuestProgress getProgress(Identifier questId) {
        return questProgress.getOrDefault(questId, new PlayerQuestState.QuestProgress(0, -1, false, false));
    }

    public static int getPurchaseCount(Identifier entryId) {
        return purchaseCounts.getOrDefault(entryId, 0);
    }

    /** Estimated server overworld game time right now, or -1 if no sync has carried one yet. */
    private static long estimatedServerGameTime() {
        if (syncedServerGameTime < 0) return -1;
        return syncedServerGameTime + (long) (ClientTickHandler.totalTicks() - syncedClientTick);
    }

    /** Ticks remaining until the daily quest board reshuffles, or -1 if unknown. */
    public static long getTicksUntilDailyReset() {
        long time = estimatedServerGameTime();
        if (time < 0) return -1;
        return DAY_TICKS - Math.floorMod(time, DAY_TICKS);
    }

    /** Ticks remaining until the shared cleanup goal period rolls over, or -1 if unknown. */
    public static long getTicksUntilCleanupGoalReset() {
        long time = estimatedServerGameTime();
        if (time < 0) return -1;
        return CLEANUP_GOAL_PERIOD_TICKS - Math.floorMod(time, CLEANUP_GOAL_PERIOD_TICKS);
    }

    /** Reset the cache to a clean state. Call on client disconnect / world exit. */
    public static void reset() {
        questProgress = new HashMap<>();
        tokenBalance = 0;
        purchaseCounts = new HashMap<>();
        cleanupGoal = CleanupGoalProgress.EMPTY;
        isInitialSync = true;
        syncedServerGameTime = -1;
        syncedClientTick = 0f;
    }
}

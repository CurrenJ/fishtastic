package grill24.fishtastic.client;

import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class QuestClientCache {
    private static Map<Identifier, PlayerQuestState.QuestProgress> questProgress = new HashMap<>();
    private static int tokenBalance = 0;
    private static Map<Identifier, Integer> purchaseCounts = new HashMap<>();
    private static boolean isInitialSync = true;
    private static QuestProgressListener listener;

    @FunctionalInterface
    public interface QuestProgressListener {
        void onProgress(Identifier questId, int oldCount, int newCount, int targetCount,
                        boolean completed, ItemStack triggeringItem);
    }

    /** Register a listener for quest progress changes. Called once during client init. */
    public static void setListener(QuestProgressListener l) {
        listener = l;
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
        isInitialSync = false;

        questProgress = new HashMap<>(progress);
        tokenBalance = tokens;
        purchaseCounts = new HashMap<>(newPurchaseCounts);
    }

    public static void update(Map<Identifier, PlayerQuestState.QuestProgress> progress, int tokens,
                              Map<Identifier, ItemStack> triggeringItems) {
        update(progress, tokens, triggeringItems, Map.of());
    }

    /** Backward-compatible overload for callers that don't have triggering items. */
    public static void update(Map<Identifier, PlayerQuestState.QuestProgress> progress, int tokens) {
        update(progress, tokens, Map.of(), Map.of());
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

    /** Reset the cache to a clean state. Call on client disconnect / world exit. */
    public static void reset() {
        questProgress = new HashMap<>();
        tokenBalance = 0;
        purchaseCounts = new HashMap<>();
        isInitialSync = true;
    }
}

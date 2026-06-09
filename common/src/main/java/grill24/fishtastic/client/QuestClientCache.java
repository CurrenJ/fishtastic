package grill24.fishtastic.client;

import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class QuestClientCache {
    private static Map<Identifier, PlayerQuestState.QuestProgress> questProgress = new HashMap<>();
    private static int tokenBalance = 0;

    public static void update(Map<Identifier, PlayerQuestState.QuestProgress> progress, int tokens) {
        questProgress = new HashMap<>(progress);
        tokenBalance = tokens;
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
}

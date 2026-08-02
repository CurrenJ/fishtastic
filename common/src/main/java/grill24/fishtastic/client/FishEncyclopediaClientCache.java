package grill24.fishtastic.client;

import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.network.LeaderboardEntry;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client-side cache of the last {@code FishEncyclopediaSyncPacket} received, mirroring {@link QuestClientCache}. */
public final class FishEncyclopediaClientCache {
    private static Map<Identifier, Integer> personalCatchCounts = new HashMap<>();
    private static Map<Identifier, LeaderboardEntry> personalBestSizes = new HashMap<>();
    private static Map<Identifier, LeaderboardEntry> globalBestSizes = new HashMap<>();
    private static Set<String> claimedRewardKeys = new HashSet<>();

    private FishEncyclopediaClientCache() {}

    public static void update(Map<Identifier, Integer> catchCounts, List<LeaderboardEntry> personalBest,
                               List<LeaderboardEntry> globalBest, List<String> claimedRewards) {
        personalCatchCounts = new HashMap<>(catchCounts);
        personalBestSizes = new HashMap<>();
        for (LeaderboardEntry entry : personalBest) {
            entry.fishType().ifPresent(fishType -> personalBestSizes.put(fishType, entry));
        }
        globalBestSizes = new HashMap<>();
        for (LeaderboardEntry entry : globalBest) {
            entry.fishType().ifPresent(fishType -> globalBestSizes.put(fishType, entry));
        }
        claimedRewardKeys = new HashSet<>(claimedRewards);
    }

    public static int getCatchCount(Identifier fishType) {
        return personalCatchCounts.getOrDefault(fishType, 0);
    }

    public static LeaderboardEntry getPersonalBest(Identifier fishType) {
        return personalBestSizes.get(fishType);
    }

    public static LeaderboardEntry getGlobalBest(Identifier fishType) {
        return globalBestSizes.get(fishType);
    }

    public static boolean isRewardClaimed(Identifier fishId, EncyclopediaRewardSection section) {
        return claimedRewardKeys.contains(section.key(fishId));
    }

    public static void reset() {
        personalCatchCounts = new HashMap<>();
        personalBestSizes = new HashMap<>();
        globalBestSizes = new HashMap<>();
        claimedRewardKeys = new HashSet<>();
    }
}

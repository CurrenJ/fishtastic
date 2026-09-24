package grill24.fishtastic.client;

import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.network.LeaderboardEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client-side cache of the last {@code FishEncyclopediaSyncPacket} received, mirroring {@link QuestClientCache}. */
public final class FishEncyclopediaClientCache {
    private static Map<ResourceLocation, Integer> personalCatchCounts = new HashMap<>();
    private static Map<ResourceLocation, LeaderboardEntry> personalBestSizes = new HashMap<>();
    private static Map<ResourceLocation, LeaderboardEntry> globalBestSizes = new HashMap<>();
    private static Set<String> claimedRewardKeys = new HashSet<>();

    private FishEncyclopediaClientCache() {}

    public static void update(Map<ResourceLocation, Integer> catchCounts, List<LeaderboardEntry> personalBest,
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

    public static int getCatchCount(ResourceLocation fishType) {
        return personalCatchCounts.getOrDefault(fishType, 0);
    }

    public static LeaderboardEntry getPersonalBest(ResourceLocation fishType) {
        return personalBestSizes.get(fishType);
    }

    public static LeaderboardEntry getGlobalBest(ResourceLocation fishType) {
        return globalBestSizes.get(fishType);
    }

    public static boolean isRewardClaimed(ResourceLocation fishId, EncyclopediaRewardSection section) {
        return claimedRewardKeys.contains(section.key(fishId));
    }

    public static void reset() {
        personalCatchCounts = new HashMap<>();
        personalBestSizes = new HashMap<>();
        globalBestSizes = new HashMap<>();
        claimedRewardKeys = new HashSet<>();
    }
}

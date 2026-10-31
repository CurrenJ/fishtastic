package grill24.fishtastic.client;

import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.LeaderboardEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Client-side cache of the last {@code FishEncyclopediaSyncPacket} received, mirroring {@link QuestClientCache}. */
public final class FishEncyclopediaClientCache {
    private static Map<ResourceLocation, Integer> personalCatchCounts = new HashMap<>();
    private static Map<ResourceLocation, LeaderboardEntry> personalBestSizes = new HashMap<>();
    private static Map<ResourceLocation, LeaderboardEntry> globalBestSizes = new HashMap<>();
    private static Set<String> claimedRewardKeys = new HashSet<>();
    /** Species seen through a spyglass (world fish exploration E7): name, portrait and habitat open without a catch. */
    private static Set<ResourceLocation> observedSpecies = new HashSet<>();
    private static Consumer<ResourceLocation> observedListener;

    private FishEncyclopediaClientCache() {}

    /** Applies a whole sync packet, including the spyglass-observed species. */
    public static void update(FishEncyclopediaSyncPacket packet) {
        update(packet.personalCatchCounts(), packet.personalBestSizes(), packet.globalBestSizes(),
                packet.claimedRewardKeys(), packet.observedSpecies(), packet.newlyObserved());
    }

    public static void update(Map<ResourceLocation, Integer> catchCounts, List<LeaderboardEntry> personalBest,
                               List<LeaderboardEntry> globalBest, List<String> claimedRewards) {
        update(catchCounts, personalBest, globalBest, claimedRewards, List.of(), List.of());
    }

    public static void update(Map<ResourceLocation, Integer> catchCounts, List<LeaderboardEntry> personalBest,
                               List<LeaderboardEntry> globalBest, List<String> claimedRewards,
                               List<ResourceLocation> observed, List<ResourceLocation> newlyObserved) {
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
        observedSpecies = new HashSet<>(observed);
        // Only the server says when a species is new: a sync that merely lists it (login, opening the
        // book) must not toast again.
        if (observedListener != null) {
            for (ResourceLocation species : newlyObserved) observedListener.accept(species);
        }
    }

    /** The one listener told of each freshly observed species (the HUD banner). */
    public static void setObservedListener(Consumer<ResourceLocation> listener) {
        observedListener = listener;
    }

    public static int getCatchCount(ResourceLocation fishType) {
        return personalCatchCounts.getOrDefault(fishType, 0);
    }

    public static boolean isObserved(ResourceLocation fishType) {
        return observedSpecies.contains(fishType);
    }

    /**
     * Whether the player knows the species at all: caught it, or seen it through a spyglass. An
     * undiscovered species is a silhouette with a hidden name; a discovered one shows its name,
     * portrait and broad habitat (conditions, lore and records still need catches).
     */
    public static boolean isDiscovered(ResourceLocation fishType) {
        return getCatchCount(fishType) > 0 || isObserved(fishType);
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
        observedSpecies = new HashSet<>();
    }
}

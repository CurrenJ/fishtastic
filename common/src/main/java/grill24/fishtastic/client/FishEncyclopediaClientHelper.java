package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client-side lookups for the fish encyclopedia screen, mirroring the registry-access
 * pattern used by {@link QuestLogScreen} for its own datapack registries.
 */
public final class FishEncyclopediaClientHelper {
    private FishEncyclopediaClientHelper() {}

    /**
     * All fish profiles in registry order. This order determines disc slot index, so it must
     * stay stable for existing fish as new ones are appended.
     */
    public static List<Map.Entry<ResourceKey<FishProfile>, FishProfile>> getAllFishProfilesSorted(RegistryAccess registryAccess) {
        Registry<FishProfile> registry = registryAccess.lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        return new ArrayList<>(registry.entrySet());
    }

    public static FishEncyclopediaEntry getEncyclopediaEntry(RegistryAccess registryAccess, ResourceKey<FishProfile> fishKey) {
        Registry<FishEncyclopediaEntry> registry;
        try {
            registry = registryAccess.lookupOrThrow(FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY);
        } catch (Exception e) {
            return FishEncyclopediaEntry.DEFAULT;
        }
        ResourceKey<FishEncyclopediaEntry> entryKey = ResourceKey.create(
                FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY, fishKey.identifier());
        return registry.getOptional(entryKey).orElse(FishEncyclopediaEntry.DEFAULT);
    }

    /** Whether this fish has at least one unlocked-but-unclaimed reward; drives the green pip. */
    public static boolean fishHasUnclaimedReward(RegistryAccess registryAccess, ResourceKey<FishProfile> fishKey, Identifier fishId) {
        FishEncyclopediaEntry.UnlockThresholds thresholds = EncyclopediaTiers.thresholdsFor(registryAccess, fishKey);
        int catchCount = FishEncyclopediaClientCache.getCatchCount(fishId);
        for (EncyclopediaRewardSection section : EncyclopediaRewardSection.values()) {
            if (catchCount >= section.threshold(thresholds) && !FishEncyclopediaClientCache.isRewardClaimed(fishId, section)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The quest whose completion unlocks this species' companion bond, if one is authored
     * ({@code unlocks_companion}, docs/fish-companions.md §8.3). Client-side lookup off the quest
     * registry so the encyclopedia can name — and link to — the quest without extra sync.
     */
    public static ResourceKey<grill24.fishtastic.data.Quest> findCompanionUnlockQuest(
            RegistryAccess registryAccess, Identifier speciesId) {
        Registry<grill24.fishtastic.data.Quest> registry;
        try {
            registry = registryAccess.lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        } catch (Exception e) {
            return null;
        }
        for (Map.Entry<ResourceKey<grill24.fishtastic.data.Quest>, grill24.fishtastic.data.Quest> entry : registry.entrySet()) {
            if (entry.getValue().unlocksCompanion().filter(speciesId::equals).isPresent()) {
                return entry.getKey();
            }
        }
        return null;
    }
}

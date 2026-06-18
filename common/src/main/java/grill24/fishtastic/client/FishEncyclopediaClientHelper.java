package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
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
}

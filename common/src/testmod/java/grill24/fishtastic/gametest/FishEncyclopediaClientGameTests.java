package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.FishEncyclopediaClientHelper;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.network.LeaderboardEntry;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side game tests for the two new fish-encyclopedia client classes that hold no
 * Minecraft-client-only state: {@link FishEncyclopediaClientCache} (pure static maps) and
 * {@link FishEncyclopediaClientHelper} (registry lookups identical in shape to the already-tested
 * {@code ShopEntry}/{@code QuestTracker} registry fixtures). Both run fine against a dedicated
 * GameTest server's real {@code RegistryAccess} — no client harness needed.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class FishEncyclopediaClientGameTests {

    private FishEncyclopediaClientGameTests() {}

    // -------------------------------------------------------------------------
    // FishEncyclopediaClientCache  (pure in-memory, no world state needed)
    // -------------------------------------------------------------------------

    public static void cacheStartsEmpty(GameTestHelper helper) {
        FishEncyclopediaClientCache.reset();
        Identifier fishType = Identifier.fromNamespaceAndPath("fishtastic", "bluegill");

        helper.assertTrue(FishEncyclopediaClientCache.getCatchCount(fishType) == 0, "an empty cache must report zero catches for any fish type");
        helper.assertTrue(FishEncyclopediaClientCache.getPersonalBest(fishType) == null, "an empty cache must report no personal best");
        helper.assertTrue(FishEncyclopediaClientCache.getGlobalBest(fishType) == null, "an empty cache must report no global best");
        helper.succeed();
    }

    public static void updatePopulatesCatchCountsByFishType(GameTestHelper helper) {
        FishEncyclopediaClientCache.reset();
        Identifier bluegill = Identifier.fromNamespaceAndPath("fishtastic", "bluegill");
        Identifier trout = Identifier.fromNamespaceAndPath("fishtastic", "trout");

        FishEncyclopediaClientCache.update(Map.of(bluegill, 7, trout, 2), List.of(), List.of(), List.of());

        helper.assertTrue(FishEncyclopediaClientCache.getCatchCount(bluegill) == 7, "catch count for bluegill must come from the update() map");
        helper.assertTrue(FishEncyclopediaClientCache.getCatchCount(trout) == 2, "catch count for trout must come from the update() map");
        helper.succeed();
    }

    public static void updateIndexesBestSizesByFishType(GameTestHelper helper) {
        FishEncyclopediaClientCache.reset();
        Identifier bluegill = Identifier.fromNamespaceAndPath("fishtastic", "bluegill");
        UUID otherPlayer = UUID.randomUUID();

        LeaderboardEntry personal = LeaderboardEntry.personalBestSize(bluegill, 18.5f, FishQuality.Quality.RARE);
        LeaderboardEntry global = LeaderboardEntry.globalBestSize(bluegill, otherPlayer, "SomePlayer", 24f, FishQuality.Quality.LEGENDARY);

        FishEncyclopediaClientCache.update(Map.of(), List.of(personal), List.of(global), List.of());

        helper.assertTrue(FishEncyclopediaClientCache.getPersonalBest(bluegill).equals(personal), "personal best must be indexed by fishType");
        helper.assertTrue(FishEncyclopediaClientCache.getGlobalBest(bluegill).equals(global), "global best must be indexed by fishType");
        helper.succeed();
    }

    public static void updateReplacesPriorContentsRatherThanMerging(GameTestHelper helper) {
        FishEncyclopediaClientCache.reset();
        Identifier bluegill = Identifier.fromNamespaceAndPath("fishtastic", "bluegill");
        Identifier trout = Identifier.fromNamespaceAndPath("fishtastic", "trout");

        FishEncyclopediaClientCache.update(Map.of(bluegill, 7), List.of(), List.of(), List.of());
        FishEncyclopediaClientCache.update(Map.of(trout, 3), List.of(), List.of(), List.of());

        helper.assertTrue(FishEncyclopediaClientCache.getCatchCount(trout) == 3, "the second update's data must be present");
        helper.assertTrue(FishEncyclopediaClientCache.getCatchCount(bluegill) == 0, "the first update's data must not survive a later update()");
        helper.succeed();
    }

    public static void resetClearsAllMaps(GameTestHelper helper) {
        FishEncyclopediaClientCache.reset();
        Identifier bluegill = Identifier.fromNamespaceAndPath("fishtastic", "bluegill");
        FishEncyclopediaClientCache.update(
            Map.of(bluegill, 7),
            List.of(LeaderboardEntry.personalBestSize(bluegill, 1f, FishQuality.Quality.COMMON)),
            List.of(LeaderboardEntry.globalBestSize(bluegill, UUID.randomUUID(), "P", 1f, FishQuality.Quality.COMMON)),
            List.of()
        );

        FishEncyclopediaClientCache.reset();

        helper.assertTrue(FishEncyclopediaClientCache.getCatchCount(bluegill) == 0, "reset() must clear catch counts");
        helper.assertTrue(FishEncyclopediaClientCache.getPersonalBest(bluegill) == null, "reset() must clear personal bests");
        helper.assertTrue(FishEncyclopediaClientCache.getGlobalBest(bluegill) == null, "reset() must clear global bests");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaClientHelper  (real RegistryAccess from the GameTest server)
    // -------------------------------------------------------------------------

    public static void getEncyclopediaEntryFallsBackToDefaultForUnregisteredFish(GameTestHelper helper) {
        ResourceKey<FishProfile> unknownFish = ResourceKey.create(
            FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY,
            Identifier.fromNamespaceAndPath("fishtastic", "gametest_nonexistent_fish"));

        FishEncyclopediaEntry entry = FishEncyclopediaClientHelper.getEncyclopediaEntry(helper.getLevel().registryAccess(), unknownFish);

        helper.assertTrue(entry.equals(FishEncyclopediaEntry.DEFAULT), "a fish with no registered encyclopedia entry must fall back to DEFAULT");
        helper.succeed();
    }

    public static void getAllFishProfilesSortedMatchesRegistryEntrySet(GameTestHelper helper) {
        Registry<FishProfile> registry = helper.getLevel().registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        List<Map.Entry<ResourceKey<FishProfile>, FishProfile>> sorted =
            FishEncyclopediaClientHelper.getAllFishProfilesSorted(helper.getLevel().registryAccess());

        helper.assertTrue(sorted.size() == registry.size(), "getAllFishProfilesSorted must return every registered fish profile, got " + sorted.size() + " expected " + registry.size());
        helper.succeed();
    }
}

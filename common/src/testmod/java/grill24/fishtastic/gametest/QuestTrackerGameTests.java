package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.server.QuestTracker;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side game tests for QuestTracker's matching rules and daily-shop-style rotation.
 *
 * matchesObjective() was widened from private to public (QuestTracker.java) specifically so
 * these tests can drive each QuestObjective condition independently with hand-built fixtures —
 * no registry, player, or live quest content needed for this part. This mirrors how
 * WormBinBlockEntity already exposes canDeposit()/canAerate() as public purely for testability.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class QuestTrackerGameTests {

    private QuestTrackerGameTests() {}

    // -------------------------------------------------------------------------
    // QuestObjective fixtures — each sets exactly one condition, rest wildcarded
    // -------------------------------------------------------------------------

    private static QuestObjective wildcardObjective() {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective targetSpeciesObjective(ResourceKey<Item> species) {
        return new QuestObjective(Optional.of(species), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective targetSpeciesTagObjective(TagKey<Item> tag) {
        return new QuestObjective(Optional.empty(), Optional.of(tag), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective minQualityObjective(FishQuality.Quality quality) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.of(quality), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective minSizeObjective(float size) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.of(size), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective biomeObjective(TagKey<Biome> tag) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.of(tag), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective timeObjective(FishProfile.TimeOfDay time) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(time), Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
    }

    private static QuestObjective weatherObjective(FishProfile.WeatherCondition weather) {
        return new QuestObjective(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(weather), Optional.empty(), Optional.empty(), 1, false);
    }

    private static ResourceKey<Item> keyOf(Item item) {
        return item.builtInRegistryHolder().unwrapKey().orElseThrow();
    }

    private static Holder<Biome> biome(GameTestHelper helper, ResourceKey<Biome> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).get(key).orElseThrow();
    }

    // -------------------------------------------------------------------------
    // Each QuestObjective field independently gates the match, ignored when absent
    // -------------------------------------------------------------------------

    public static void targetSpeciesGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);

        helper.assertTrue(
            QuestTracker.matchesObjective(targetSpeciesObjective(keyOf(Items.COD)), cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch matching the required species must match"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(targetSpeciesObjective(keyOf(Items.DIAMOND)), cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch of a different species must not match"
        );
        helper.assertTrue(
            QuestTracker.matchesObjective(wildcardObjective(), cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "An absent targetSpecies condition must not block the match"
        );
        helper.succeed();
    }

    public static void targetSpeciesTagGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective fishesTag = targetSpeciesTagObjective(ItemTags.FISHES);

        helper.assertTrue(
            QuestTracker.matchesObjective(fishesTag, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "Cod must match the vanilla fishes tag condition"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(fishesTag, diamond, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "Diamond must not match the vanilla fishes tag condition"
        );
        helper.succeed();
    }

    /** minQuality is an ordinal floor: one tier below fails, exactly at or above passes. */
    public static void minQualityIsOrdinalFloor(GameTestHelper helper) {
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresRare = minQualityObjective(FishQuality.Quality.RARE);

        ItemStack uncommon = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(uncommon, FishQuality.Quality.UNCOMMON);
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresRare, uncommon, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch one tier below the required quality must not match"
        );

        ItemStack rare = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(rare, FishQuality.Quality.RARE);
        helper.assertTrue(
            QuestTracker.matchesObjective(requiresRare, rare, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch exactly at the required quality must match"
        );

        ItemStack epic = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(epic, FishQuality.Quality.EPIC);
        helper.assertTrue(
            QuestTracker.matchesObjective(requiresRare, epic, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch above the required quality must match"
        );

        ItemStack noQuality = new ItemStack(Items.COD);
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresRare, noQuality, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch with no quality component must not match a minQuality requirement"
        );
        helper.succeed();
    }

    public static void biomeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        QuestObjective requiresOcean = biomeObjective(BiomeTags.IS_OCEAN);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresOcean, cod, biome(helper, Biomes.OCEAN), FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "Catching in an ocean biome must match an is_ocean biome condition"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresOcean, cod, biome(helper, Biomes.PLAINS), FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "Catching in plains must not match an is_ocean biome condition"
        );
        helper.succeed();
    }

    public static void timeConditionGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresDay = timeObjective(FishProfile.TimeOfDay.DAY);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresDay, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "Catching during the required time of day must match"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresDay, cod, anyBiome, FishProfile.TimeOfDay.NIGHT, FishProfile.WeatherCondition.CLEAR),
            "Catching at a different time of day must not match"
        );
        helper.succeed();
    }

    public static void weatherConditionGatesMatchWhenPresent(GameTestHelper helper) {
        ItemStack cod = new ItemStack(Items.COD);
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresClear = weatherObjective(FishProfile.WeatherCondition.CLEAR);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresClear, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "Catching in the required weather must match"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(requiresClear, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.RAIN),
            "Catching in different weather must not match"
        );
        helper.succeed();
    }

    /** minSize is a floor: strictly below fails, exactly at or above passes, missing size data fails. */
    public static void minSizeIsFloor(GameTestHelper helper) {
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requires40 = minSizeObjective(40.0f);

        ItemStack small = new ItemStack(Items.COD);
        ItemSizeHelper.setSize(small, 39.9f);
        helper.assertTrue(
            !QuestTracker.matchesObjective(requires40, small, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch smaller than the required size must not match"
        );

        ItemStack exact = new ItemStack(Items.COD);
        ItemSizeHelper.setSize(exact, 40.0f);
        helper.assertTrue(
            QuestTracker.matchesObjective(requires40, exact, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch exactly at the required size must match"
        );

        ItemStack noSize = new ItemStack(Items.COD);
        helper.assertTrue(
            !QuestTracker.matchesObjective(requires40, noSize, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch with no size component must not match a minSize requirement"
        );
        helper.succeed();
    }

    /**
     * minSessionCatches is a batch-level threshold applied in QuestTracker.onCatchBatch, not a
     * per-stack predicate — matchesObjective must ignore it entirely and match on the other
     * conditions alone, the same as if it were absent.
     */
    public static void minSessionCatchesDoesNotAffectPerStackMatching(GameTestHelper helper) {
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);
        QuestObjective requiresTwoPerSession = new QuestObjective(
            Optional.of(keyOf(Items.COD)), Optional.empty(), Optional.empty(), false, Optional.of(1), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(2), 1, false
        );
        ItemStack cod = new ItemStack(Items.COD);

        helper.assertTrue(
            QuestTracker.matchesObjective(requiresTwoPerSession, cod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A single matching stack must still satisfy matchesObjective regardless of minSessionCatches"
        );
        helper.succeed();
    }

    /** All set conditions must hold together (AND semantics) — failing any single axis fails the whole match. */
    public static void allConditionsMustMatchTogether(GameTestHelper helper) {
        QuestObjective combined = new QuestObjective(
            Optional.of(keyOf(Items.COD)),
            Optional.empty(),
            Optional.empty(),
            false,
            Optional.of(1),
            Optional.of(FishQuality.Quality.RARE),
            Optional.empty(),
            Optional.empty(),
            Optional.of(FishProfile.TimeOfDay.DAY),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            1,
            false
        );
        Holder<Biome> anyBiome = biome(helper, Biomes.PLAINS);

        ItemStack rareCod = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(rareCod, FishQuality.Quality.RARE);

        helper.assertTrue(
            QuestTracker.matchesObjective(combined, rareCod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "A catch satisfying every set condition must match"
        );
        helper.assertTrue(
            !QuestTracker.matchesObjective(combined, rareCod, anyBiome, FishProfile.TimeOfDay.NIGHT, FishProfile.WeatherCondition.CLEAR),
            "Failing just the time-of-day axis must fail the whole AND, even though species/quality still match"
        );

        ItemStack uncommonCod = new ItemStack(Items.COD);
        FishQualityHelper.setQuality(uncommonCod, FishQuality.Quality.UNCOMMON);
        helper.assertTrue(
            !QuestTracker.matchesObjective(combined, uncommonCod, anyBiome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR),
            "Failing just the quality axis must fail the whole AND, even though species/time still match"
        );
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // getActiveDailies — deterministic per day, bounded by ACTIVE_DAILY_COUNT (4)
    // -------------------------------------------------------------------------

    private static Quest dailyQuest() {
        return new Quest(QuestCategory.DAILY, QuestDifficulty.BRONZE, wildcardObjective(), new QuestReward(0, List.of()), Optional.empty(), false, "", "");
    }

    private static Quest tutorialQuest() {
        return new Quest(QuestCategory.TUTORIAL, QuestDifficulty.BRONZE, wildcardObjective(), new QuestReward(0, List.of()), Optional.empty(), false, "", "");
    }

    private static Registry<Quest> buildRegistry(int dailyCount, int tutorialCount) {
        MappedRegistry<Quest> registry = new MappedRegistry<>(FishtasticRegistries.QUEST_REGISTRY_KEY, Lifecycle.stable());
        for (int i = 0; i < dailyCount; i++) {
            ResourceKey<Quest> key = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "daily_" + i));
            registry.register(key, dailyQuest(), RegistrationInfo.BUILT_IN);
        }
        for (int i = 0; i < tutorialCount; i++) {
            ResourceKey<Quest> key = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Identifier.fromNamespaceAndPath("fishtastic", "tutorial_" + i));
            registry.register(key, tutorialQuest(), RegistrationInfo.BUILT_IN);
        }
        return registry;
    }

    public static void getActiveDailiesIsStablePerDay(GameTestHelper helper) {
        Registry<Quest> registry = buildRegistry(10, 3);

        Set<ResourceKey<Quest>> first = QuestTracker.getActiveDailies(registry, 42L);
        Set<ResourceKey<Quest>> second = QuestTracker.getActiveDailies(registry, 42L);

        helper.assertTrue(first.equals(second), "getActiveDailies must be deterministic for the same day");
        helper.succeed();
    }

    public static void getActiveDailiesNeverExceedsCapAndExcludesNonDaily(GameTestHelper helper) {
        Registry<Quest> registry = buildRegistry(10, 5);

        Set<ResourceKey<Quest>> active = QuestTracker.getActiveDailies(registry, 7L);

        helper.assertTrue(active.size() == 4, "With 10 daily quests, exactly ACTIVE_DAILY_COUNT (4) must be selected, got " + active.size());
        for (ResourceKey<Quest> key : active) {
            helper.assertTrue(
                registry.getValue(key).category() == QuestCategory.DAILY,
                "getActiveDailies must never select a non-DAILY quest"
            );
        }
        helper.succeed();
    }

    public static void getActiveDailiesCapsAtRegistrySizeWhenSmallerThanCount(GameTestHelper helper) {
        Registry<Quest> registry = buildRegistry(2, 0);

        Set<ResourceKey<Quest>> active = QuestTracker.getActiveDailies(registry, 1L);

        helper.assertTrue(active.size() == 2, "With fewer daily quests than the cap, all of them must be active, got " + active.size());
        helper.succeed();
    }
}

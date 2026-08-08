package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.server.QuestTracker;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates that every authored quest is actually winnable against the live fish_profile and
 * biome data, rather than only that it parses.
 *
 * <p>This exists because two shipped quests were silently impossible and — being {@code hidden}
 * — could never surface in the log to be reported: {@code golden_catch} required a Nether-only
 * species under {@code minecraft:is_ocean}, and {@code molten_nights} required
 * {@code weather_condition: thunder} on a Nether-only species, which
 * {@link net.minecraft.world.level.Level#canHaveWeather()} makes permanently unreachable in a
 * dimension with a ceiling. Both parsed and registered perfectly happily.
 *
 * <p>Only {@code target_species} objectives are checked: a {@code target_species_tag} objective
 * spans many species whose zones legitimately differ, so no single species has to satisfy the
 * conditions on its own.
 */
public final class QuestSatisfiabilityGameTests {

    private QuestSatisfiabilityGameTests() {}

    /** Y levels probed against each candidate biome, relative to sea level, to exercise
     *  {@link FishProfile.Zone#resolve}'s CAVE / surface / HIGH_ALTITUDE elevation bands. */
    private static final int[] Y_OFFSETS = {-40, 0, 40};

    /**
     * Every quest naming a specific {@code target_species} must have at least one
     * (biome, elevation) pair that simultaneously satisfies its biome condition and grants a
     * zone the species actually spawns in — and, if it demands weather, at least one such biome
     * must live in a dimension that can have weather at all.
     */
    public static void everyTargetSpeciesQuestIsSatisfiable(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Registry<Quest> quests = registries.lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        Registry<FishProfile> profiles = registries.lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        Registry<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        int seaLevel = helper.getLevel().getSeaLevel();

        List<String> failures = new ArrayList<>();

        for (Map.Entry<ResourceKey<Quest>, Quest> entry : quests.entrySet()) {
            String questId = entry.getKey().identifier().toString();
            QuestObjective objective = entry.getValue().objective();

            Optional<ResourceKey<Item>> target = objective.targetSpecies();
            if (target.isEmpty()) continue;

            ResourceKey<FishProfile> profileKey =
                    ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, target.get().identifier());
            FishProfile profile = profiles.getOptional(profileKey).orElse(null);
            if (profile == null) {
                failures.add(questId + ": target_species " + target.get().identifier() + " has no fish_profile");
                continue;
            }

            Set<FishProfile.Zone> declaredZones = profile.zones().isEmpty()
                    ? EnumSet.noneOf(FishProfile.Zone.class)
                    : EnumSet.copyOf(profile.zones());
            if (declaredZones.isEmpty()) {
                failures.add(questId + ": target_species " + target.get().identifier() + " declares no zones");
                continue;
            }

            // zone_condition is a UI-only pip, but a value the species can't spawn in means the
            // pip can never light — still an authoring bug worth failing on.
            if (objective.zoneCondition().isPresent()
                    && !declaredZones.contains(objective.zoneCondition().get())) {
                failures.add(questId + ": zone_condition " + objective.zoneCondition().get()
                        + " is not among the species' zones " + declaredZones);
            }

            boolean anyBiomeWorks = false;
            boolean anyWeatherCapableBiomeWorks = false;

            for (Holder<Biome> biome : biomes.listElements().toList()) {
                if (objective.biomeCondition().isPresent() && !biome.is(objective.biomeCondition().get())) continue;

                boolean zoneMatches = false;
                for (int offset : Y_OFFSETS) {
                    Set<FishProfile.Zone> resolved = FishProfile.Zone.resolve(biome, seaLevel + offset, seaLevel);
                    if (!java.util.Collections.disjoint(resolved, declaredZones)) {
                        zoneMatches = true;
                        break;
                    }
                }
                if (!zoneMatches) continue;

                anyBiomeWorks = true;
                // Mirrors Level#canHaveWeather: a dimension with a ceiling (the Nether) is
                // permanently CLEAR, so WeatherCondition.fromLevel can never report rain/thunder there.
                if (!biome.is(BiomeTags.IS_NETHER)) anyWeatherCapableBiomeWorks = true;
            }

            if (!anyBiomeWorks) {
                failures.add(questId + ": no biome satisfies biome_condition "
                        + objective.biomeCondition().map(t -> t.location().toString()).orElse("<any>")
                        + " while granting one of the species' zones " + declaredZones);
                continue;
            }

            boolean demandsWeather = objective.weatherCondition()
                    .map(w -> w != FishProfile.WeatherCondition.CLEAR)
                    .orElse(false);
            if (demandsWeather && !anyWeatherCapableBiomeWorks) {
                failures.add(questId + ": weather_condition " + objective.weatherCondition().get()
                        + " is unreachable — every biome that grants this species' zones is in a"
                        + " dimension that cannot have weather");
            }
        }

        helper.assertTrue(failures.isEmpty(), "Unsatisfiable quest(s): " + String.join(" | ", failures));
        helper.succeed();
    }

    /**
     * A {@code lifetime_count} objective is evaluated by replaying the player's lifetime catch
     * records, which store species and counts but not the biome, time, weather or quality a fish
     * was landed under. Declaring such a condition alongside {@code lifetime_count} would make it
     * silently ignored, so fail loudly at test time instead.
     */
    public static void lifetimeQuestsCarryNoUnreplayableConditions(GameTestHelper helper) {
        Registry<Quest> quests = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);

        List<String> failures = new ArrayList<>();
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : quests.entrySet()) {
            QuestObjective objective = entry.getValue().objective();
            if (objective.lifetimeCount() && !objective.isLifetimeCompatible()) {
                failures.add(entry.getKey().identifier()
                        + ": lifetime_count is set alongside a condition that lifetime catch records"
                        + " cannot express (quality/size/biome/time/weather/session/distinct)");
            }
        }

        helper.assertTrue(failures.isEmpty(), "Invalid lifetime quest(s): " + String.join(" | ", failures));
        helper.succeed();
    }

    /**
     * The daily pool has to be meaningfully larger than the number drawn each rotation, or
     * "today's dailies" is a fiction. It previously held five quests against
     * {@link PlayerQuestState#ACTIVE_DAILY_COUNT} of 4, so every rotation showed the same four
     * and only ever swapped one out.
     */
    public static void dailyPoolIsLargerThanTheDrawAndActuallyRotates(GameTestHelper helper) {
        Registry<Quest> quests = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);

        long dailyCount = quests.entrySet().stream()
                .filter(e -> e.getValue().category() == QuestCategory.DAILY)
                .count();
        int draw = PlayerQuestState.ACTIVE_DAILY_COUNT;

        helper.assertTrue(dailyCount >= draw * 3L,
                "Daily pool must be at least 3x the per-rotation draw (" + draw + ") so the set "
                        + "actually varies; found " + dailyCount);

        // Across a run of rotations the drawn set must genuinely change, not just re-order.
        Set<Set<ResourceKey<Quest>>> distinctDraws = new java.util.HashSet<>();
        for (long day = 0; day < 30; day++) {
            distinctDraws.add(QuestTracker.getActiveDailies(quests, day));
        }
        helper.assertTrue(distinctDraws.size() >= 10,
                "30 consecutive rotations must yield at least 10 distinct daily sets; found "
                        + distinctDraws.size());
        helper.succeed();
    }

    /** Every {@code prerequisite} must point at a quest that actually exists in the registry. */
    public static void everyPrerequisiteResolves(GameTestHelper helper) {
        Registry<Quest> quests = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);

        List<String> failures = new ArrayList<>();
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : quests.entrySet()) {
            Optional<ResourceKey<Quest>> prereq = entry.getValue().prerequisiteQuestId();
            if (prereq.isPresent() && quests.getOptional(prereq.get()).isEmpty()) {
                failures.add(entry.getKey().identifier() + ": prerequisite "
                        + prereq.get().identifier() + " does not exist");
            }
        }

        helper.assertTrue(failures.isEmpty(), "Dangling prerequisite(s): " + String.join(" | ", failures));
        helper.succeed();
    }

    /**
     * A prerequisite chain must terminate. A cycle would make every quest in it permanently
     * untrackable, since QuestTracker skips a quest whose prerequisite is unclaimed.
     */
    public static void noPrerequisiteCycles(GameTestHelper helper) {
        Registry<Quest> quests = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);

        List<String> failures = new ArrayList<>();
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : quests.entrySet()) {
            Set<ResourceKey<Quest>> seen = new java.util.HashSet<>();
            ResourceKey<Quest> cursor = entry.getKey();
            while (cursor != null && seen.add(cursor)) {
                Quest quest = quests.getOptional(cursor).orElse(null);
                cursor = quest == null ? null : quest.prerequisiteQuestId().orElse(null);
            }
            if (cursor != null) {
                failures.add(entry.getKey().identifier() + ": prerequisite chain cycles at " + cursor.identifier());
            }
        }

        helper.assertTrue(failures.isEmpty(), "Cyclic prerequisite chain(s): " + String.join(" | ", failures));
        helper.succeed();
    }
}

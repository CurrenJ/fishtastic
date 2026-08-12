package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.tutorial.TutorialStep;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

import java.util.*;

public class QuestTracker {
    private static final int ACTIVE_DAILY_COUNT = 4;

    /**
     * Process a single catch for quest tracking. Prefer {@link #onCatchBatch} when
     * multiple catches happen in the same tick (e.g. minigame completion) to avoid
     * sending multiple sync packets.
     */
    public static void onCatch(MinecraftServer server, ServerPlayer player,
            ItemStack caughtStack, Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay, FishProfile.WeatherCondition weather,
            Set<FishProfile.Zone> zones) {

        if (caughtStack.isEmpty()) return;

        Registry<Quest> questRegistry;
        try {
            questRegistry = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        } catch (Exception e) {
            return;
        }

        FishCatchSavedData catchData = FishCatchSavedData.getOrCreate(server);
        PlayerQuestState state = catchData.getOrCreateQuestState(player);

        long currentDay = server.overworld().getGameTime() / 24000L;
        Set<ResourceKey<Quest>> activeDailies = getActiveDailies(questRegistry, currentDay);

        Map<Identifier, ItemStack> triggeringItems = new HashMap<>();

        for (Map.Entry<ResourceKey<Quest>, Quest> entry : questRegistry.entrySet()) {
            ResourceKey<Quest> questKey = entry.getKey();
            Quest quest = entry.getValue();

            if (quest.category() == QuestCategory.DAILY && !activeDailies.contains(questKey)) continue;
            if (quest.category() == QuestCategory.TUTORIAL && !isTutorialQuestActive(player, questKey, state)) continue;

            PlayerQuestState.QuestProgress progress = state.getProgress(questKey);

            if (progress.completed() || progress.claimed()) continue;

            // A lifetime-count quest is exempt from the prerequisite gate: its progress is a
            // running total that was already banked, so gating it here would re-introduce exactly
            // the discarded-catches problem lifetime counting exists to fix.
            if (quest.prerequisiteQuestId().isPresent() && !quest.objective().lifetimeCount()) {
                ResourceKey<Quest> prereq = quest.prerequisiteQuestId().get();
                PlayerQuestState.QuestProgress prereqProgress = state.getProgress(prereq);
                if (!prereqProgress.claimed()) continue;
            }

            if (matchesObjective(quest.objective(), caughtStack, biome, timeOfDay, weather, zones)) {
                int targetCount = quest.objective().effectiveTargetCount(server.registryAccess());
                int oldCount = state.getProgress(questKey).currentCount();
                if (quest.objective().lifetimeCount()) {
                    state.setProgress(questKey, lifetimeProgress(server, catchData,
                            catchData.resolvePlayerKey(player), quest.objective()), targetCount, currentDay);
                } else if (quest.objective().distinctSpecies()) {
                    Identifier caughtId = BuiltInRegistries.ITEM.getKey(caughtStack.getItem());
                    state.incrementDistinctSpecies(questKey, targetCount, currentDay, caughtId);
                } else {
                    state.incrementCount(questKey, targetCount, currentDay);
                }
                int newCount = state.getProgress(questKey).currentCount();

                int interval = quest.objective().notificationInterval();
                int oldBucket = oldCount / interval;
                int newBucket = newCount / interval;
                boolean crossedInterval = newBucket > oldBucket;
                boolean justCompleted = newCount >= targetCount
                        && oldCount < targetCount;
                if ((crossedInterval || justCompleted) && isVisibleForNotification(quest, state, justCompleted)) {
                    triggeringItems.put(questKey.identifier(), caughtStack.copy());
                }
            }
        }

        catchData.setDirty();
        QuestSyncPacket.sendToPlayer(player, catchData, triggeringItems);
    }

    /**
     * Process multiple catches from a single minigame session in one batch.
     * Only sends one QuestSyncPacket at the end, avoiding duplicate notification
     * banners when multiple catches progress the same quest.
     */
    public static void onCatchBatch(MinecraftServer server, ServerPlayer player,
            List<ItemStack> caughtStacks, Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay, FishProfile.WeatherCondition weather,
            Set<FishProfile.Zone> zones) {

        Registry<Quest> questRegistry;
        try {
            questRegistry = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        } catch (Exception e) {
            return;
        }

        FishCatchSavedData catchData = FishCatchSavedData.getOrCreate(server);
        PlayerQuestState state = catchData.getOrCreateQuestState(player);

        long currentDay = server.overworld().getGameTime() / 24000L;
        Set<ResourceKey<Quest>> activeDailies = getActiveDailies(questRegistry, currentDay);

        Map<Identifier, ItemStack> triggeringItems = new HashMap<>();
        List<ItemStack> nonEmptyStacks = caughtStacks.stream().filter(s -> !s.isEmpty()).toList();

        for (Map.Entry<ResourceKey<Quest>, Quest> entry : questRegistry.entrySet()) {
            ResourceKey<Quest> questKey = entry.getKey();
            Quest quest = entry.getValue();

            if (quest.category() == QuestCategory.DAILY && !activeDailies.contains(questKey)) continue;
            if (quest.category() == QuestCategory.TUTORIAL && !isTutorialQuestActive(player, questKey, state)) continue;

            PlayerQuestState.QuestProgress progress = state.getProgress(questKey);

            if (progress.completed() || progress.claimed()) continue;

            // See onCatch: lifetime-count quests skip the prerequisite gate so banked catches
            // aren't discarded while the previous tier sits unclaimed.
            if (quest.prerequisiteQuestId().isPresent() && !quest.objective().lifetimeCount()) {
                ResourceKey<Quest> prereq = quest.prerequisiteQuestId().get();
                PlayerQuestState.QuestProgress prereqProgress = state.getProgress(prereq);
                if (!prereqProgress.claimed()) continue;
            }

            List<ItemStack> matchingStacks = nonEmptyStacks.stream()
                    .filter(stack -> matchesObjective(quest.objective(), stack, biome, timeOfDay, weather, zones))
                    .toList();
            if (matchingStacks.isEmpty()) continue;

            int targetCount = quest.objective().effectiveTargetCount(server.registryAccess());
            int oldCount = state.getProgress(questKey).currentCount();
            if (quest.objective().lifetimeCount()) {
                // One read of the running total covers the whole batch — FishingMinigameManager
                // calls catchDb.recordCatch() for every reward before it calls onCatchBatch, so
                // this session's fish are already reflected here.
                state.setProgress(questKey, lifetimeProgress(server, catchData,
                        catchData.resolvePlayerKey(player), quest.objective()), targetCount, currentDay);
            } else if (quest.objective().distinctSpecies()) {
                // Completionist-style objectives credit each newly-seen species once, ignoring
                // minSessionCatches — a batch just offers however many distinct species it contains.
                for (ItemStack stack : matchingStacks) {
                    Identifier caughtId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    state.incrementDistinctSpecies(questKey, targetCount, currentDay, caughtId);
                }
            } else {
                // A session-catch quest (e.g. "catch 2 fish in one minigame") only progresses once
                // per qualifying session, not once per matching fish — anything short of the
                // threshold in this batch earns no partial credit. The threshold counts distinct
                // fish species, so multiple catches of the same species don't satisfy it alone.
                int increments = quest.objective().minSessionCatches()
                        .map(threshold -> {
                            long distinctSpecies = matchingStacks.stream().map(ItemStack::getItem).distinct().count();
                            return distinctSpecies >= threshold ? 1 : 0;
                        })
                        .orElse(matchingStacks.size());
                if (increments == 0) continue;

                for (int i = 0; i < increments; i++) {
                    state.incrementCount(questKey, targetCount, currentDay);
                }
            }
            int newCount = state.getProgress(questKey).currentCount();

            // Only notify at interval multiples or on completion
            int interval = quest.objective().notificationInterval();
            int oldBucket = oldCount / interval;
            int newBucket = newCount / interval;
            boolean crossedInterval = newBucket > oldBucket;
            boolean justCompleted = newCount >= targetCount
                    && oldCount < targetCount;
            if ((crossedInterval || justCompleted) && isVisibleForNotification(quest, state, justCompleted)) {
                triggeringItems.put(questKey.identifier(), matchingStacks.get(matchingStacks.size() - 1).copy());
            }
        }

        catchData.setDirty();
        QuestSyncPacket.sendToPlayer(player, catchData, triggeringItems);
    }

    /**
     * Resolves a {@link QuestObjective#lifetimeCount()} objective's progress by reading the
     * player's lifetime catch records instead of an incrementing per-quest counter.
     *
     * <p>This is what makes the mastery chains' "Catch 50 X total" honest. With incrementing
     * counters, tier 3 started from 0 once tier 2 was claimed, so the chain actually cost
     * 10 + 25 + 50 = 85 catches; worse, {@code QuestTracker} skips a quest whose prerequisite is
     * unclaimed, so every catch made between finishing a tier and getting round to claiming it was
     * silently discarded. Reading a lifetime total removes both problems: tiers overlap correctly
     * and claim timing stops mattering.
     *
     * <p>Scoped by the objective's species filter — a specific {@code target_species}, else the
     * members of {@code target_species_tag} minus {@code exclude_species_tag}, else every fish.
     */
    private static int lifetimeProgress(MinecraftServer server, FishCatchSavedData catchData,
            UUID playerKey, QuestObjective objective) {
        if (objective.targetSpecies().isPresent()) {
            return catchData.getCatchCount(playerKey, objective.targetSpecies().get().identifier());
        }

        Registry<net.minecraft.world.item.Item> items =
                server.registryAccess().lookupOrThrow(BuiltInRegistries.ITEM.key());
        Optional<TagKey<net.minecraft.world.item.Item>> includeTag = objective.targetSpeciesTag();
        Optional<TagKey<net.minecraft.world.item.Item>> excludeTag = objective.excludeSpeciesTag();
        if (includeTag.isEmpty() && excludeTag.isEmpty()) {
            return catchData.getTotalCatchCount(playerKey);
        }

        return catchData.getCatchCountMatching(playerKey, speciesId ->
                items.getOptional(speciesId)
                        .map(item -> {
                            Holder<net.minecraft.world.item.Item> holder = item.builtInRegistryHolder();
                            if (includeTag.isPresent() && !holder.is(includeTag.get())) return false;
                            return excludeTag.isEmpty() || !holder.is(excludeTag.get());
                        })
                        .orElse(false));
    }

    /**
     * Whether a quest that just crossed a notification interval or completed should actually
     * surface a banner. A quest still hidden from the log (see {@link Quest#isHiddenFromLog}) —
     * e.g. an unclaimed mastery tier progressing in the background via lifetime counting — stays
     * silent until it becomes visible, so a single catch can't fire a banner for every tier of a
     * chain at once. Completion always overrides hidden, so a tier that finishes while still
     * locked still announces itself immediately.
     */
    private static boolean isVisibleForNotification(Quest quest, PlayerQuestState state, boolean justCompleted) {
        boolean prerequisiteClaimed = quest.prerequisiteQuestId()
                .map(prereq -> state.getProgress(prereq).claimed())
                .orElse(false);
        return !quest.isHiddenFromLog(justCompleted, prerequisiteClaimed);
    }

    public static boolean matchesObjective(QuestObjective obj, ItemStack stack, Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay, FishProfile.WeatherCondition weather,
            Set<FishProfile.Zone> zones) {
        if (obj.targetSpecies().isPresent()) {
            Optional<ResourceKey<net.minecraft.world.item.Item>> stackKey =
                    stack.getItem().builtInRegistryHolder().unwrapKey();
            if (stackKey.isEmpty() || !stackKey.get().equals(obj.targetSpecies().get())) return false;
        }

        if (obj.targetSpeciesTag().isPresent()) {
            if (!stack.is(obj.targetSpeciesTag().get())) return false;
        }

        if (obj.excludeSpeciesTag().isPresent()) {
            if (stack.is(obj.excludeSpeciesTag().get())) return false;
        }

        if (obj.minQuality().isPresent()) {
            FishQuality.Quality catchQuality = FishQualityHelper.getQuality(stack);
            if (catchQuality == null || catchQuality.ordinal() < obj.minQuality().get().ordinal()) return false;
        }

        if (obj.minSize().isPresent()) {
            if (!ItemSizeHelper.hasSize(stack) || ItemSizeHelper.getSize(stack) < obj.minSize().get()) return false;
        }

        if (obj.biomeCondition().isPresent()) {
            if (!biome.is(obj.biomeCondition().get())) return false;
        }

        if (obj.timeCondition().isPresent()) {
            if (timeOfDay != obj.timeCondition().get()) return false;
        }

        if (obj.weatherCondition().isPresent()) {
            if (weather != obj.weatherCondition().get()) return false;
        }

        if (obj.zoneCondition().isPresent()) {
            if (!zones.contains(obj.zoneCondition().get())) return false;
        }

        return true;
    }

    private static boolean isTutorialQuestActive(ServerPlayer player, ResourceKey<Quest> questKey,
                                                  PlayerQuestState state) {
        // Tutorial quests only track during the tutorial's minigame/catch phases
        TutorialStep step = TutorialManager.getStep(player);
        return step == TutorialStep.MINIGAME_CATCH || step == TutorialStep.CATCH_RESULT
                || step == TutorialStep.QUEST_INTRO || step == TutorialStep.QUEST_CLAIM;
    }

    /**
     * A daily quest "family" is a base id shared by up to three tier variants — e.g.
     * {@code daily/tidal_catch_bronze}, {@code _silver}, {@code _gold} all belong to family
     * {@code daily/tidal_catch}. Which families are active today, and which tier each one rolls,
     * are both seeded purely off {@code currentDay} (plus the family id for the tier roll), so
     * every player sees the same board on the same server day and it never reshuffles mid-day.
     */
    private static final int SILVER_ROLL_THRESHOLD = 60;
    private static final int GOLD_ROLL_THRESHOLD = 90;

    public static Set<ResourceKey<Quest>> getActiveDailies(Registry<Quest> questRegistry, long currentDay) {
        Map<String, Map<QuestDifficulty, ResourceKey<Quest>>> families = new TreeMap<>();
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : questRegistry.entrySet()) {
            if (entry.getValue().category() != QuestCategory.DAILY) continue;
            ResourceKey<Quest> key = entry.getKey();
            String path = key.identifier().getPath();
            QuestDifficulty tier = tierSuffixOf(path).orElseGet(entry.getValue()::difficulty);
            families.computeIfAbsent(familyOf(path), f -> new EnumMap<>(QuestDifficulty.class)).put(tier, key);
        }

        List<String> familyIds = new ArrayList<>(families.keySet());
        Collections.shuffle(familyIds, new Random(currentDay));

        Set<ResourceKey<Quest>> active = new HashSet<>();
        for (String familyId : familyIds.subList(0, Math.min(ACTIVE_DAILY_COUNT, familyIds.size()))) {
            Map<QuestDifficulty, ResourceKey<Quest>> tiers = families.get(familyId);
            QuestDifficulty rolled = rollTier(new Random(currentDay * 31L + familyId.hashCode()));
            // Falls back to whatever tier the family actually has, for quests with no siblings yet.
            active.add(tiers.getOrDefault(rolled, tiers.values().iterator().next()));
        }
        return active;
    }

    private static QuestDifficulty rollTier(Random random) {
        int roll = random.nextInt(100);
        if (roll < SILVER_ROLL_THRESHOLD) return QuestDifficulty.BRONZE;
        if (roll < GOLD_ROLL_THRESHOLD) return QuestDifficulty.SILVER;
        return QuestDifficulty.GOLD;
    }

    private static String familyOf(String questPath) {
        return tierSuffixOf(questPath)
                .map(tier -> questPath.substring(0, questPath.length() - tierSuffix(tier).length()))
                .orElse(questPath);
    }

    private static Optional<QuestDifficulty> tierSuffixOf(String questPath) {
        for (QuestDifficulty tier : QuestDifficulty.values()) {
            if (questPath.endsWith(tierSuffix(tier))) return Optional.of(tier);
        }
        return Optional.empty();
    }

    private static String tierSuffix(QuestDifficulty tier) {
        return "_" + tier.getSerializedName();
    }
}

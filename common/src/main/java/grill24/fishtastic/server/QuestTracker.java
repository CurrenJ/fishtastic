package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestObjective;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.util.FishQualityHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

import java.util.*;
import java.util.stream.Collectors;

public class QuestTracker {
    private static final int ACTIVE_DAILY_COUNT = 4;

    /**
     * Process a single catch for quest tracking. Prefer {@link #onCatchBatch} when
     * multiple catches happen in the same tick (e.g. minigame completion) to avoid
     * sending multiple sync packets.
     */
    public static void onCatch(MinecraftServer server, ServerPlayer player,
            ItemStack caughtStack, Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay, FishProfile.WeatherCondition weather) {

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

            PlayerQuestState.QuestProgress progress = state.getProgress(questKey);

            if (progress.completed() || progress.claimed()) continue;

            if (quest.prerequisiteQuestId().isPresent()) {
                ResourceKey<Quest> prereq = quest.prerequisiteQuestId().get();
                PlayerQuestState.QuestProgress prereqProgress = state.getProgress(prereq);
                if (!prereqProgress.claimed()) continue;
            }

            if (matchesObjective(quest.objective(), caughtStack, biome, timeOfDay, weather)) {
                int oldCount = state.getProgress(questKey).currentCount();
                state.incrementCount(questKey, quest, currentDay);
                int newCount = state.getProgress(questKey).currentCount();

                int interval = quest.objective().notificationInterval();
                int oldBucket = oldCount / interval;
                int newBucket = newCount / interval;
                boolean crossedInterval = newBucket > oldBucket;
                boolean justCompleted = newCount >= quest.objective().targetCount()
                        && oldCount < quest.objective().targetCount();
                if (crossedInterval || justCompleted) {
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
            FishProfile.TimeOfDay timeOfDay, FishProfile.WeatherCondition weather) {

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

        for (ItemStack caughtStack : caughtStacks) {
            if (caughtStack.isEmpty()) continue;

            for (Map.Entry<ResourceKey<Quest>, Quest> entry : questRegistry.entrySet()) {
                ResourceKey<Quest> questKey = entry.getKey();
                Quest quest = entry.getValue();

                if (quest.category() == QuestCategory.DAILY && !activeDailies.contains(questKey)) continue;

                PlayerQuestState.QuestProgress progress = state.getProgress(questKey);

                if (progress.completed() || progress.claimed()) continue;

                if (quest.prerequisiteQuestId().isPresent()) {
                    ResourceKey<Quest> prereq = quest.prerequisiteQuestId().get();
                    PlayerQuestState.QuestProgress prereqProgress = state.getProgress(prereq);
                    if (!prereqProgress.claimed()) continue;
                }

                if (matchesObjective(quest.objective(), caughtStack, biome, timeOfDay, weather)) {
                    // snapshot old count before incrementing
                    int oldCount = state.getProgress(questKey).currentCount();
                    state.incrementCount(questKey, quest, currentDay);
                    int newCount = state.getProgress(questKey).currentCount();

                    // Only notify at interval multiples or on completion
                    int interval = quest.objective().notificationInterval();
                    int oldBucket = oldCount / interval;
                    int newBucket = newCount / interval;
                    boolean crossedInterval = newBucket > oldBucket;
                    boolean justCompleted = newCount >= quest.objective().targetCount()
                            && oldCount < quest.objective().targetCount();
                    if (crossedInterval || justCompleted) {
                        triggeringItems.put(questKey.identifier(), caughtStack.copy());
                    }
                }
            }
        }

        catchData.setDirty();
        QuestSyncPacket.sendToPlayer(player, catchData, triggeringItems);
    }

    private static boolean matchesObjective(QuestObjective obj, ItemStack stack, Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay, FishProfile.WeatherCondition weather) {
        if (obj.targetSpecies().isPresent()) {
            Optional<ResourceKey<net.minecraft.world.item.Item>> stackKey =
                    stack.getItem().builtInRegistryHolder().unwrapKey();
            if (stackKey.isEmpty() || !stackKey.get().equals(obj.targetSpecies().get())) return false;
        }

        if (obj.targetSpeciesTag().isPresent()) {
            if (!stack.is(obj.targetSpeciesTag().get())) return false;
        }

        if (obj.minQuality().isPresent()) {
            FishQuality.Quality catchQuality = FishQualityHelper.getQuality(stack);
            if (catchQuality == null || catchQuality.ordinal() < obj.minQuality().get().ordinal()) return false;
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

        return true;
    }

    public static Set<ResourceKey<Quest>> getActiveDailies(Registry<Quest> questRegistry, long currentDay) {
        List<ResourceKey<Quest>> dailyKeys = questRegistry.entrySet().stream()
                .filter(e -> e.getValue().category() == QuestCategory.DAILY)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(k -> k.identifier().toString()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(dailyKeys, new Random(currentDay));
        return new HashSet<>(dailyKeys.subList(0, Math.min(ACTIVE_DAILY_COUNT, dailyKeys.size())));
    }
}

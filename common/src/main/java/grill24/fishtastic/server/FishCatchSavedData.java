package grill24.fishtastic.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.tutorial.TutorialStep;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Persistent server-side storage for all-time fish catch statistics.
 */
public class FishCatchSavedData extends SavedData {

    // -------------------------------------------------------------------------
    // Public result record types
    // -------------------------------------------------------------------------

    public record PersonalBestSizeEntry(
            Identifier fishType,
            float bestSize,
            FishQuality.Quality bestQuality
    ) {}

    public record GlobalBestSizeEntry(
            Identifier fishType,
            UUID playerUuid,
            String playerName,
            float bestSize,
            FishQuality.Quality bestQuality
    ) {}

    public record PersonalCatchCountEntry(
            Identifier fishType,
            int totalCatches
    ) {}

    public record GlobalCatchCountEntry(
            UUID playerUuid,
            String playerName,
            int totalCatches
    ) {}

    // -------------------------------------------------------------------------
    // Built-in Comparators
    // -------------------------------------------------------------------------

    public static final Comparator<PersonalBestSizeEntry> PERSONAL_BEST_SIZE_DESC =
            Comparator.comparingDouble(PersonalBestSizeEntry::bestSize).reversed();
    public static final Comparator<PersonalBestSizeEntry> PERSONAL_BEST_SIZE_ASC =
            Comparator.comparingDouble(PersonalBestSizeEntry::bestSize);

    public static final Comparator<GlobalBestSizeEntry> GLOBAL_BEST_SIZE_DESC =
            Comparator.comparingDouble(GlobalBestSizeEntry::bestSize).reversed();
    public static final Comparator<GlobalBestSizeEntry> GLOBAL_BEST_SIZE_ASC =
            Comparator.comparingDouble(GlobalBestSizeEntry::bestSize);

    public static final Comparator<PersonalCatchCountEntry> PERSONAL_CATCH_COUNT_DESC =
            Comparator.comparingInt(PersonalCatchCountEntry::totalCatches).reversed();
    public static final Comparator<PersonalCatchCountEntry> PERSONAL_CATCH_COUNT_ASC =
            Comparator.comparingInt(PersonalCatchCountEntry::totalCatches);

    public static final Comparator<GlobalCatchCountEntry> GLOBAL_CATCH_COUNT_DESC =
            Comparator.comparingInt(GlobalCatchCountEntry::totalCatches).reversed();
    public static final Comparator<GlobalCatchCountEntry> GLOBAL_CATCH_COUNT_ASC =
            Comparator.comparingInt(GlobalCatchCountEntry::totalCatches);

    // -------------------------------------------------------------------------
    // Internal storage
    // -------------------------------------------------------------------------

    private static final class FishTypeData {
        int totalCatches;
        float bestSize;
        FishQuality.Quality bestQuality = FishQuality.Quality.COMMON;

        void record(float size, FishQuality.Quality quality) {
            totalCatches++;
            if (size > bestSize) {
                bestSize = size;
                bestQuality = quality;
            }
        }

        static final Codec<FishTypeData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.fieldOf("catches").forGetter(d -> d.totalCatches),
                Codec.FLOAT.fieldOf("best_size").forGetter(d -> d.bestSize),
                FishQuality.Quality.CODEC.fieldOf("best_quality").forGetter(d -> d.bestQuality)
            ).apply(instance, (catches, size, quality) -> {
                FishTypeData d = new FishTypeData();
                d.totalCatches = catches;
                d.bestSize = size;
                d.bestQuality = quality;
                return d;
            })
        );
    }

    private static final class PlayerCatchData {
        final UUID uuid;
        String lastKnownName;
        final Map<Identifier, FishTypeData> perFish = new HashMap<>();

        PlayerCatchData(UUID uuid, String name) {
            this.uuid = uuid;
            this.lastKnownName = name;
        }

        void record(Identifier fishType, float size, FishQuality.Quality quality) {
            perFish.computeIfAbsent(fishType, k -> new FishTypeData()).record(size, quality);
        }

        int totalCatches() {
            return perFish.values().stream().mapToInt(d -> d.totalCatches).sum();
        }

        static final Codec<PlayerCatchData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(d -> d.uuid),
                Codec.STRING.fieldOf("name").forGetter(d -> d.lastKnownName),
                Codec.unboundedMap(Identifier.CODEC, FishTypeData.CODEC).fieldOf("fish").forGetter(d -> new HashMap<>(d.perFish))
            ).apply(instance, (uuid, name, fish) -> {
                PlayerCatchData d = new PlayerCatchData(uuid, name);
                d.perFish.putAll(fish);
                return d;
            })
        );
    }

    static final class CleanupGoalState {
        long periodStartDay = -1;
        int totalContributed = 0;
        int lastThresholdPaidOut = 0;
        final Map<UUID, Integer> contributions = new HashMap<>();

        static final Codec<CleanupGoalState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.LONG.optionalFieldOf("period_start_day", -1L).forGetter(s -> s.periodStartDay),
                Codec.INT.optionalFieldOf("total_contributed", 0).forGetter(s -> s.totalContributed),
                Codec.INT.optionalFieldOf("last_threshold_paid_out", 0).forGetter(s -> s.lastThresholdPaidOut),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)
                        .optionalFieldOf("contributions", Map.of()).forGetter(s -> new HashMap<>(s.contributions))
            ).apply(instance, (start, total, paid, contrib) -> {
                CleanupGoalState s = new CleanupGoalState();
                s.periodStartDay = start;
                s.totalContributed = total;
                s.lastThresholdPaidOut = paid;
                s.contributions.putAll(contrib);
                return s;
            })
        );
    }

    private final Map<UUID, PlayerCatchData> playerData = new HashMap<>();
    private final Map<UUID, PlayerQuestState> questStates = new HashMap<>();
    private final Map<UUID, TutorialStep> tutorialSteps = new HashMap<>();
    private CleanupGoalState cleanupGoal = new CleanupGoalState();

    // -------------------------------------------------------------------------
    // Codec and SavedDataType
    // -------------------------------------------------------------------------

    public static final Codec<FishCatchSavedData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            PlayerCatchData.CODEC.listOf().fieldOf("players").forGetter(d -> new ArrayList<>(d.playerData.values())),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerQuestState.CODEC)
                    .optionalFieldOf("quest_states", Map.of()).forGetter(d -> new HashMap<>(d.questStates)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, TutorialStep.CODEC)
                    .optionalFieldOf("tutorial_steps", Map.of()).forGetter(d -> new HashMap<>(d.tutorialSteps)),
            CleanupGoalState.CODEC.optionalFieldOf("cleanup_goal", new CleanupGoalState()).forGetter(d -> d.cleanupGoal)
        ).apply(instance, (playerList, questMap, tutorialMap, cleanupGoal) -> {
            FishCatchSavedData data = new FishCatchSavedData();
            playerList.forEach(pd -> data.playerData.put(pd.uuid, pd));
            data.questStates.putAll(questMap);
            data.tutorialSteps.putAll(tutorialMap);
            data.cleanupGoal = cleanupGoal;
            return data;
        })
    );

    public static final SavedDataType<FishCatchSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("fishtastic", "fish_catches"),
        FishCatchSavedData::new,
        CODEC,
        DataFixTypes.LEVEL
    );

    // -------------------------------------------------------------------------
    // Factory / lifecycle
    // -------------------------------------------------------------------------

    public static FishCatchSavedData getOrCreate(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // -------------------------------------------------------------------------
    // Recording API
    // -------------------------------------------------------------------------

    public void recordCatch(UUID playerUuid, String playerName, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.FISHES)) return;

        float size = ItemSizeHelper.getSize(stack);
        if (size <= 0f) return;

        FishQuality.Quality quality = FishQualityHelper.getQuality(stack);
        if (quality == null) quality = FishQuality.Quality.COMMON;

        Identifier fishType = stack.getItem().builtInRegistryHolder().unwrapKey()
                .map(k -> k.identifier())
                .orElse(null);
        if (fishType == null) return;

        PlayerCatchData data = playerData.computeIfAbsent(playerUuid,
                id -> new PlayerCatchData(id, playerName));
        data.lastKnownName = playerName;
        data.record(fishType, size, quality);
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    public List<PersonalBestSizeEntry> getPersonalBestSizes(UUID playerUuid,
                                                            Comparator<PersonalBestSizeEntry> order) {
        PlayerCatchData data = playerData.get(playerUuid);
        if (data == null) return List.of();
        return data.perFish.entrySet().stream()
                .map(e -> new PersonalBestSizeEntry(e.getKey(), e.getValue().bestSize, e.getValue().bestQuality))
                .sorted(order)
                .toList();
    }

    public List<GlobalBestSizeEntry> getGlobalBestSizes(Comparator<GlobalBestSizeEntry> order) {
        Map<Identifier, GlobalBestSizeEntry> bestPerFish = new HashMap<>();
        for (PlayerCatchData pd : playerData.values()) {
            for (Map.Entry<Identifier, FishTypeData> e : pd.perFish.entrySet()) {
                Identifier fishType = e.getKey();
                FishTypeData ftd = e.getValue();
                GlobalBestSizeEntry existing = bestPerFish.get(fishType);
                if (existing == null || ftd.bestSize > existing.bestSize()) {
                    bestPerFish.put(fishType, new GlobalBestSizeEntry(
                            fishType, pd.uuid, pd.lastKnownName, ftd.bestSize, ftd.bestQuality));
                }
            }
        }
        return bestPerFish.values().stream().sorted(order).toList();
    }

    public List<PersonalCatchCountEntry> getPersonalCatchCounts(UUID playerUuid,
                                                                Comparator<PersonalCatchCountEntry> order) {
        PlayerCatchData data = playerData.get(playerUuid);
        if (data == null) return List.of();
        return data.perFish.entrySet().stream()
                .map(e -> new PersonalCatchCountEntry(e.getKey(), e.getValue().totalCatches))
                .sorted(order)
                .toList();
    }

    public List<GlobalCatchCountEntry> getGlobalCatchCounts(Comparator<GlobalCatchCountEntry> order) {
        return playerData.values().stream()
                .map(pd -> new GlobalCatchCountEntry(pd.uuid, pd.lastKnownName, pd.totalCatches()))
                .sorted(order)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Quest state API
    // -------------------------------------------------------------------------

    /** Sentinel UUID used as quest-state key for singleplayer worlds.
     *  In singleplayer there is only one player, and Fabric dev-auth rotates
     *  the player UUID every session, so we key by a constant that never changes. */
    private static final UUID SINGLEPLAYER_QUEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public PlayerQuestState getOrCreateQuestState(UUID uuid) {
        return questStates.computeIfAbsent(uuid, k -> new PlayerQuestState());
    }

    /** Overload that resolves the correct key for singleplayer vs multiplayer.
     *  Uses a stable sentinel UUID for the singleplayer owner so that Fabric
     *  dev-auth UUID rotation doesn't orphan quest progress. LAN guests and
     *  multiplayer players use their own Mojang-auth UUID (which is stable). */
    public PlayerQuestState getOrCreateQuestState(ServerPlayer player) {
        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
        if (server != null && server.isSingleplayerOwner(player.nameAndId())) {
            return getOrCreateQuestState(SINGLEPLAYER_QUEST_UUID);
        }
        return getOrCreateQuestState(player.getUUID());
    }

    /** Wipes quest progress (including token balance and shop purchases) and catch history for one player. */
    public void resetPlayerProgress(ServerPlayer player) {
        questStates.remove(resolvePlayerKey(player));
        playerData.remove(player.getUUID());
        setDirty();
    }

    /** Wipes quest progress and catch history for every player. */
    public void resetAllProgress() {
        questStates.clear();
        playerData.clear();
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Tutorial state API
    // -------------------------------------------------------------------------

    public TutorialStep getTutorialStep(net.minecraft.server.level.ServerPlayer player) {
        UUID key = resolvePlayerKey(player);
        return tutorialSteps.getOrDefault(key, TutorialStep.WAITING_FOR_CAST);
    }

    public void setTutorialStep(net.minecraft.server.level.ServerPlayer player, TutorialStep step) {
        UUID key = resolvePlayerKey(player);
        tutorialSteps.put(key, step);
        setDirty();
    }

    UUID resolvePlayerKey(net.minecraft.server.level.ServerPlayer player) {
        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
        if (server != null && server.isSingleplayerOwner(player.nameAndId())) {
            return SINGLEPLAYER_QUEST_UUID;
        }
        return player.getUUID();
    }

    public void resetDailyQuestsIfNeeded(MinecraftServer server, long currentDay) {
        Registry<Quest> questRegistry;
        try {
            questRegistry = server.registryAccess().lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        } catch (Exception e) {
            return;
        }
        questRegistry.entrySet().stream()
                .filter(e -> e.getValue().category() == QuestCategory.DAILY)
                .forEach(e -> {
                    ResourceKey<Quest> key = e.getKey();
                    questStates.values().forEach(state -> state.resetDailyIfNeeded(key, currentDay));
                });
        setDirty();
    }

    // -------------------------------------------------------------------------
    // Global cleanup goal API
    // -------------------------------------------------------------------------

    private static final int CLEANUP_GOAL_THRESHOLD = 200;
    private static final int CLEANUP_GOAL_REWARD_TOKENS = 50;

    /**
     * Records trash caught by a player toward the shared weekly cleanup goal.
     * Returns the cumulative totals of any thresholds crossed by this contribution
     * (usually empty or one entry), so the caller can broadcast a milestone notification.
     */
    public List<Integer> recordTrashContribution(ServerPlayer player, int amount) {
        if (amount <= 0) return List.of();
        UUID key = resolvePlayerKey(player);
        int before = cleanupGoal.totalContributed;
        cleanupGoal.contributions.merge(key, amount, Integer::sum);
        cleanupGoal.totalContributed += amount;

        List<Integer> crossed = new ArrayList<>();
        int beforeThresholds = before / CLEANUP_GOAL_THRESHOLD;
        int afterThresholds = cleanupGoal.totalContributed / CLEANUP_GOAL_THRESHOLD;
        for (int t = beforeThresholds + 1; t <= afterThresholds; t++) {
            crossed.add(t * CLEANUP_GOAL_THRESHOLD);
        }
        if (!crossed.isEmpty()) {
            payoutCleanupGoalContributors(crossed.size() * CLEANUP_GOAL_REWARD_TOKENS);
        }
        setDirty();
        return crossed;
    }

    /** Splits the reward pool across every contributor this period, proportional to their share. */
    private void payoutCleanupGoalContributors(int totalRewardPool) {
        int totalContrib = cleanupGoal.totalContributed;
        if (totalContrib <= 0) return;
        for (Map.Entry<UUID, Integer> e : cleanupGoal.contributions.entrySet()) {
            int share = Math.max(1, Math.round(totalRewardPool * (e.getValue() / (float) totalContrib)));
            getOrCreateQuestState(e.getKey()).addTokens(share);
        }
    }

    public int getCleanupGoalTotal() {
        return cleanupGoal.totalContributed;
    }

    public int getCleanupGoalThreshold() {
        return CLEANUP_GOAL_THRESHOLD;
    }

    public List<GlobalCatchCountEntry> getCleanupGoalContributors(Comparator<GlobalCatchCountEntry> order) {
        return cleanupGoal.contributions.entrySet().stream()
                .map(e -> {
                    PlayerCatchData pd = playerData.get(e.getKey());
                    String name = pd != null ? pd.lastKnownName : e.getKey().toString();
                    return new GlobalCatchCountEntry(e.getKey(), name, e.getValue());
                })
                .sorted(order)
                .toList();
    }

    /** Weekly rollover: wipes contributions and re-anchors the period once a new week starts. */
    public void resetCleanupGoalIfNeeded(long currentWeek) {
        if (cleanupGoal.periodStartDay < 0) {
            cleanupGoal.periodStartDay = currentWeek * 7;
            setDirty();
            return;
        }
        long goalWeek = cleanupGoal.periodStartDay / 7;
        if (goalWeek < currentWeek) {
            cleanupGoal = new CleanupGoalState();
            cleanupGoal.periodStartDay = currentWeek * 7;
            setDirty();
        }
    }
}

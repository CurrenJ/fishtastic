package grill24.fishtastic.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
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

    private final Map<UUID, PlayerCatchData> playerData = new HashMap<>();
    private final Map<UUID, PlayerQuestState> questStates = new HashMap<>();

    // -------------------------------------------------------------------------
    // Codec and SavedDataType
    // -------------------------------------------------------------------------

    public static final Codec<FishCatchSavedData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            PlayerCatchData.CODEC.listOf().fieldOf("players").forGetter(d -> new ArrayList<>(d.playerData.values())),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerQuestState.CODEC)
                    .optionalFieldOf("quest_states", Map.of()).forGetter(d -> new HashMap<>(d.questStates))
        ).apply(instance, (playerList, questMap) -> {
            FishCatchSavedData data = new FishCatchSavedData();
            playerList.forEach(pd -> data.playerData.put(pd.uuid, pd));
            data.questStates.putAll(questMap);
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
}

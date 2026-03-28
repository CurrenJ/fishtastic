package grill24.fishtastic.server;

import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Persistent server-side storage for all-time fish catch statistics.
 *
 * <p>Data is stored in the overworld's data storage and survives server restarts.
 * Each player's record tracks per-fish-type stats (total catches, personal best size and quality).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FishCatchSavedData db = FishCatchSavedData.getOrCreate(server);
 *
 * // Record a catch (call after item is awarded)
 * db.recordCatch(player.getUUID(), player.getName().getString(), itemStack);
 *
 * // Query leaderboards
 * List<PersonalBestSizeEntry>   personalBest  = db.getPersonalBestSizes(uuid, PERSONAL_BEST_SIZE_DESC);
 * List<GlobalBestSizeEntry>     globalBest    = db.getGlobalBestSizes(GLOBAL_BEST_SIZE_DESC);
 * List<PersonalCatchCountEntry> personalCount = db.getPersonalCatchCounts(uuid, PERSONAL_CATCH_COUNT_DESC);
 * List<GlobalCatchCountEntry>   globalCount   = db.getGlobalCatchCounts(GLOBAL_CATCH_COUNT_DESC);
 * }</pre>
 */
public class FishCatchSavedData extends SavedData {

    private static final String DATA_ID = "fishtastic_fish_catches";

    // -------------------------------------------------------------------------
    // Public result record types
    // -------------------------------------------------------------------------

    /**
     * One row in a <em>personal</em> best-size leaderboard.
     * Represents the largest catch of {@code fishType} ever made by a single player.
     */
    public record PersonalBestSizeEntry(
            ResourceLocation fishType,
            float bestSize,
            FishQuality.Quality bestQuality
    ) {}

    /**
     * One row in a <em>global</em> best-size leaderboard.
     * Represents the largest catch of {@code fishType} across all players.
     * {@code playerUuid} / {@code playerName} identify the holder of that record.
     */
    public record GlobalBestSizeEntry(
            ResourceLocation fishType,
            UUID playerUuid,
            String playerName,
            float bestSize,
            FishQuality.Quality bestQuality
    ) {}

    /**
     * One row in a <em>personal</em> catch-count leaderboard.
     * Represents how many times a player has caught {@code fishType}.
     */
    public record PersonalCatchCountEntry(
            ResourceLocation fishType,
            int totalCatches
    ) {}

    /**
     * One row in a <em>global</em> catch-count leaderboard.
     * Represents the total number of fish (of any type) caught by {@code playerName}.
     */
    public record GlobalCatchCountEntry(
            UUID playerUuid,
            String playerName,
            int totalCatches
    ) {}

    // -------------------------------------------------------------------------
    // Built-in Comparators
    // -------------------------------------------------------------------------

    /** Largest personal best size first. */
    public static final Comparator<PersonalBestSizeEntry> PERSONAL_BEST_SIZE_DESC =
            Comparator.comparingDouble(PersonalBestSizeEntry::bestSize).reversed();
    /** Smallest personal best size first. */
    public static final Comparator<PersonalBestSizeEntry> PERSONAL_BEST_SIZE_ASC =
            Comparator.comparingDouble(PersonalBestSizeEntry::bestSize);

    /** Largest global best size first. */
    public static final Comparator<GlobalBestSizeEntry> GLOBAL_BEST_SIZE_DESC =
            Comparator.comparingDouble(GlobalBestSizeEntry::bestSize).reversed();
    /** Smallest global best size first. */
    public static final Comparator<GlobalBestSizeEntry> GLOBAL_BEST_SIZE_ASC =
            Comparator.comparingDouble(GlobalBestSizeEntry::bestSize);

    /** Most-caught fish type first (personal). */
    public static final Comparator<PersonalCatchCountEntry> PERSONAL_CATCH_COUNT_DESC =
            Comparator.comparingInt(PersonalCatchCountEntry::totalCatches).reversed();
    /** Least-caught fish type first (personal). */
    public static final Comparator<PersonalCatchCountEntry> PERSONAL_CATCH_COUNT_ASC =
            Comparator.comparingInt(PersonalCatchCountEntry::totalCatches);

    /** Most total catches first (global). */
    public static final Comparator<GlobalCatchCountEntry> GLOBAL_CATCH_COUNT_DESC =
            Comparator.comparingInt(GlobalCatchCountEntry::totalCatches).reversed();
    /** Fewest total catches first (global). */
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

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("catches", totalCatches);
            tag.putFloat("best_size", bestSize);
            tag.putString("best_quality", bestQuality.getSerializedName());
            return tag;
        }

        static FishTypeData load(CompoundTag tag) {
            FishTypeData d = new FishTypeData();
            d.totalCatches = tag.getInt("catches");
            d.bestSize = tag.getFloat("best_size");
            String qualityStr = tag.getString("best_quality");
            for (FishQuality.Quality q : FishQuality.Quality.values()) {
                if (q.getSerializedName().equals(qualityStr)) {
                    d.bestQuality = q;
                    break;
                }
            }
            return d;
        }
    }

    private static final class PlayerCatchData {
        final UUID uuid;
        String lastKnownName;
        final Map<ResourceLocation, FishTypeData> perFish = new HashMap<>();

        PlayerCatchData(UUID uuid, String name) {
            this.uuid = uuid;
            this.lastKnownName = name;
        }

        void record(ResourceLocation fishType, float size, FishQuality.Quality quality) {
            perFish.computeIfAbsent(fishType, k -> new FishTypeData()).record(size, quality);
        }

        int totalCatches() {
            return perFish.values().stream().mapToInt(d -> d.totalCatches).sum();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            tag.putString("name", lastKnownName);
            CompoundTag fishTag = new CompoundTag();
            for (Map.Entry<ResourceLocation, FishTypeData> e : perFish.entrySet()) {
                fishTag.put(e.getKey().toString(), e.getValue().save());
            }
            tag.put("fish", fishTag);
            return tag;
        }

        static PlayerCatchData load(CompoundTag tag) {
            UUID uuid = tag.getUUID("uuid");
            String name = tag.getString("name");
            PlayerCatchData d = new PlayerCatchData(uuid, name);
            CompoundTag fishTag = tag.getCompound("fish");
            for (String key : fishTag.getAllKeys()) {
                ResourceLocation fishType = ResourceLocation.tryParse(key);
                if (fishType != null) {
                    d.perFish.put(fishType, FishTypeData.load(fishTag.getCompound(key)));
                }
            }
            return d;
        }
    }

    private final Map<UUID, PlayerCatchData> playerData = new HashMap<>();

    // -------------------------------------------------------------------------
    // Factory / lifecycle
    // -------------------------------------------------------------------------

    /**
     * Retrieves (or creates) the global catch data for this server.
     * Stored in the overworld data directory so it is world-global.
     */
    public static FishCatchSavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FishCatchSavedData::new, FishCatchSavedData::load, null),
                DATA_ID
        );
    }

    // -------------------------------------------------------------------------
    // Recording API
    // -------------------------------------------------------------------------

    /**
     * Records a fish catch for a player.
     * Silently ignores items that are not in the {@code #minecraft:fishes} tag,
     * items with no size component, or empty stacks.
     *
     * @param playerUuid the catching player's UUID
     * @param playerName the catching player's current display name (cached for leaderboards)
     * @param stack      the awarded fish item stack
     */
    public void recordCatch(UUID playerUuid, String playerName, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.FISHES)) return;

        float size = ItemSizeHelper.getSize(stack);
        if (size <= 0f) return;

        FishQuality.Quality quality = FishQualityHelper.getQuality(stack);
        if (quality == null) quality = FishQuality.Quality.COMMON;

        ResourceLocation fishType = stack.getItemHolder().unwrapKey()
                .map(k -> k.location())
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

    /**
     * Returns this player's personal best size for every fish type they have caught,
     * sorted by the supplied comparator.
     *
     * <p>Use {@link #PERSONAL_BEST_SIZE_DESC} / {@link #PERSONAL_BEST_SIZE_ASC}
     * or supply your own {@link Comparator}.
     */
    public List<PersonalBestSizeEntry> getPersonalBestSizes(UUID playerUuid,
                                                            Comparator<PersonalBestSizeEntry> order) {
        PlayerCatchData data = playerData.get(playerUuid);
        if (data == null) return List.of();
        return data.perFish.entrySet().stream()
                .map(e -> new PersonalBestSizeEntry(e.getKey(), e.getValue().bestSize, e.getValue().bestQuality))
                .sorted(order)
                .toList();
    }

    /**
     * Returns one entry per fish type: the player who holds the global size record for that type.
     * Sorted by the supplied comparator.
     *
     * <p>Use {@link #GLOBAL_BEST_SIZE_DESC} / {@link #GLOBAL_BEST_SIZE_ASC}
     * or supply your own {@link Comparator}.
     */
    public List<GlobalBestSizeEntry> getGlobalBestSizes(Comparator<GlobalBestSizeEntry> order) {
        Map<ResourceLocation, GlobalBestSizeEntry> bestPerFish = new HashMap<>();
        for (PlayerCatchData pd : playerData.values()) {
            for (Map.Entry<ResourceLocation, FishTypeData> e : pd.perFish.entrySet()) {
                ResourceLocation fishType = e.getKey();
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

    /**
     * Returns this player's total catch count for every fish type they have caught,
     * sorted by the supplied comparator.
     *
     * <p>Use {@link #PERSONAL_CATCH_COUNT_DESC} / {@link #PERSONAL_CATCH_COUNT_ASC}
     * or supply your own {@link Comparator}.
     */
    public List<PersonalCatchCountEntry> getPersonalCatchCounts(UUID playerUuid,
                                                                Comparator<PersonalCatchCountEntry> order) {
        PlayerCatchData data = playerData.get(playerUuid);
        if (data == null) return List.of();
        return data.perFish.entrySet().stream()
                .map(e -> new PersonalCatchCountEntry(e.getKey(), e.getValue().totalCatches))
                .sorted(order)
                .toList();
    }

    /**
     * Returns one entry per player: their total fish catches across all species,
     * sorted by the supplied comparator.
     *
     * <p>Use {@link #GLOBAL_CATCH_COUNT_DESC} / {@link #GLOBAL_CATCH_COUNT_ASC}
     * or supply your own {@link Comparator}.
     */
    public List<GlobalCatchCountEntry> getGlobalCatchCounts(Comparator<GlobalCatchCountEntry> order) {
        return playerData.values().stream()
                .map(pd -> new GlobalCatchCountEntry(pd.uuid, pd.lastKnownName, pd.totalCatches()))
                .sorted(order)
                .toList();
    }

    // -------------------------------------------------------------------------
    // SavedData serialization
    // -------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (PlayerCatchData pd : playerData.values()) {
            list.add(pd.save());
        }
        tag.put("players", list);
        return tag;
    }

    public static FishCatchSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        FishCatchSavedData data = new FishCatchSavedData();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PlayerCatchData pd = PlayerCatchData.load(list.getCompound(i));
            data.playerData.put(pd.uuid, pd);
        }
        return data;
    }
}

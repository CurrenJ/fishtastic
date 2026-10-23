package grill24.fishtastic.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.ShopEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlayerQuestState {
    public static final int ACTIVE_DAILY_COUNT = 4;

    private final Map<ResourceKey<Quest>, QuestProgress> progress = new HashMap<>();
    private int tokenBalance = 0;
    private final Map<ResourceKey<ShopEntry>, Integer> purchaseCounts = new HashMap<>();
    private long lastPurchaseResetDay = -1;
    // Number of paid manual rerolls this rotation. Used for escalating cost and retained under
    // the existing codec field name for save compatibility.
    private int shopRefreshCount = 0;
    // Every manual reroll, including the free daily one, changes the active draw.
    private int shopRefreshNonce = 0;
    // The free reroll is consumed independently of paid rerolls and resets with the in-game day.
    private boolean freeShopRerollUsed = false;
    // Composite keys from EncyclopediaRewardSection#key — one entry per fish/section reward
    // slot the player has claimed, ever. See claimEncyclopediaReward.
    private final Set<String> claimedEncyclopediaRewards = new HashSet<>();
    // Species whose companion bond this player has unlocked, ever. Once unlocked a bond never
    // re-locks, even if the derived thresholds later move (docs/fish-companions.md, R6.4).
    // Normal species unlock by completing their encyclopedia entry; unlisted ones unlock via a
    // quest carrying `unlocks_companion`. See CompanionBondUnlocks.
    private final Set<ResourceLocation> unlockedCompanionSpecies = new LinkedHashSet<>();
    // Bonds unlocked since the last quest sync, waiting to be announced as banners. Deliberately
    // not persisted: a banner is a live moment, not history — a save that reopens with pending
    // entries simply announces them on the next sync, and one from an old session never does.
    private final Set<ResourceLocation> pendingCompanionBanners = new LinkedHashSet<>();
    // Ever-incrementing count of fish inserted into any tank, across the player's whole lifetime —
    // never decremented, even if the fish is later removed. Backs
    // QuestObjective.TankSnapshotCondition#lifetime objectives the same way FishCatchSavedData's
    // per-species totals back QuestObjective#lifetimeCount for catches.
    private int lifetimeTankPlacements = 0;

    /**
     * {@code caughtSpecies} is only populated for "distinct species" objectives (e.g. the
     * completionist quests) — it tracks which species have already been credited so repeat
     * catches of the same species don't inflate {@code currentCount} further.
     */
    public record QuestProgress(int currentCount, long lastResetGameDay, boolean completed, boolean claimed,
                                 List<ResourceLocation> caughtSpecies) {
        public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("count").forGetter(QuestProgress::currentCount),
                Codec.LONG.fieldOf("last_reset_day").forGetter(QuestProgress::lastResetGameDay),
                Codec.BOOL.fieldOf("completed").forGetter(QuestProgress::completed),
                Codec.BOOL.fieldOf("claimed").forGetter(QuestProgress::claimed),
                Codec.list(ResourceLocation.CODEC).optionalFieldOf("caught_species", List.of()).forGetter(QuestProgress::caughtSpecies)
        ).apply(i, QuestProgress::new));

        /**
         * The count to show in the quest log, clamped to {@code targetCount}.
         *
         * <p>{@link #currentCount} can legitimately exceed the target. A lifetime-count quest reads
         * a running total, so a tier completed at 11 lifetime catches freezes at 11/10 the moment
         * {@code QuestTracker} stops updating completed quests; a batched catch can likewise
         * overshoot ("catch 3 treasure" credited 5 at once). Both produce a bar reading over 100%
         * and, worse, an inconsistency between a finished tier stuck at 11/10 and a live one
         * showing 23/25 — the same underlying number rendered two different ways.
         *
         * <p>Clamping is the right call rather than showing the raw total: once a quest is done the
         * number's job is to say "done", not to keep reporting how far past the line you are. The
         * live lifetime total stays visible on the next unfinished tier, which is where it's
         * actually actionable.
         */
        public int displayCount(int targetCount) {
            return targetCount > 0 ? Math.min(currentCount, targetCount) : currentCount;
        }

        public static final StreamCodec<ByteBuf, QuestProgress> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, QuestProgress::currentCount,
                ByteBufCodecs.VAR_LONG, QuestProgress::lastResetGameDay,
                ByteBufCodecs.BOOL, QuestProgress::completed,
                ByteBufCodecs.BOOL, QuestProgress::claimed,
                ByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC), QuestProgress::caughtSpecies,
                QuestProgress::new
        );
    }

    public static final Codec<PlayerQuestState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC)
                    .fieldOf("progress").forGetter(state -> {
                        Map<ResourceLocation, QuestProgress> map = new HashMap<>();
                        state.progress.forEach((k, v) -> map.put(k.location(), v));
                        return map;
                    }),
            Codec.INT.optionalFieldOf("token_balance", 0).forGetter(state -> state.tokenBalance),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .optionalFieldOf("purchase_counts", Map.of()).forGetter(state -> {
                        Map<ResourceLocation, Integer> map = new HashMap<>();
                        state.purchaseCounts.forEach((k, v) -> map.put(k.location(), v));
                        return map;
                    }),
            Codec.LONG.optionalFieldOf("last_purchase_reset_day", -1L).forGetter(state -> state.lastPurchaseResetDay),
            Codec.STRING.listOf().optionalFieldOf("claimed_encyclopedia_rewards", List.of())
                    .forGetter(state -> new ArrayList<>(state.claimedEncyclopediaRewards)),
            Codec.INT.optionalFieldOf("shop_refresh_count", 0).forGetter(state -> state.shopRefreshCount),
            Codec.INT.optionalFieldOf("shop_refresh_nonce", -1).forGetter(state -> state.shopRefreshNonce),
            Codec.BOOL.optionalFieldOf("free_shop_reroll_used", false).forGetter(state -> state.freeShopRerollUsed),
            Codec.INT.optionalFieldOf("lifetime_tank_placements", 0).forGetter(state -> state.lifetimeTankPlacements),
            ResourceLocation.CODEC.listOf().optionalFieldOf("unlocked_companion_species", List.of())
                    .forGetter(state -> new ArrayList<>(state.unlockedCompanionSpecies))
    ).apply(i, (identMap, tokens, purchaseMap, lastPurchaseResetDay, claimedRewards, shopRefreshCount,
                shopRefreshNonce, freeShopRerollUsed, lifetimeTankPlacements, unlockedCompanions) -> {
        PlayerQuestState s = new PlayerQuestState();
        identMap.forEach((id, prog) ->
                s.progress.put(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, id), prog));
        s.tokenBalance = tokens;
        purchaseMap.forEach((id, count) ->
                s.purchaseCounts.put(ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, id), count));
        s.lastPurchaseResetDay = lastPurchaseResetDay;
        s.claimedEncyclopediaRewards.addAll(claimedRewards);
        s.shopRefreshCount = shopRefreshCount;
        // Older saves only tracked paid rerolls. Preserve their stock draw when adding the new
        // all-rerolls nonce field.
        s.shopRefreshNonce = shopRefreshNonce < 0 ? shopRefreshCount : shopRefreshNonce;
        s.freeShopRerollUsed = freeShopRerollUsed;
        s.lifetimeTankPlacements = lifetimeTankPlacements;
        s.unlockedCompanionSpecies.addAll(unlockedCompanions);
        return s;
    }));

    public QuestProgress getProgress(ResourceKey<Quest> questId) {
        return progress.getOrDefault(questId, new QuestProgress(0, -1, false, false, List.of()));
    }

    public void incrementCount(ResourceKey<Quest> questId, int targetCount, long currentDay) {
        QuestProgress existing = getProgress(questId);
        int newCount = existing.currentCount() + 1;
        boolean completed = newCount >= targetCount;
        // If this quest has never been reset before, anchor it to the current day
        // so that resetDailyIfNeeded won't wipe the progress on the next server start.
        long lastReset = existing.lastResetGameDay() == -1 ? currentDay : existing.lastResetGameDay();
        progress.put(questId, new QuestProgress(newCount, lastReset, completed, existing.claimed(), existing.caughtSpecies()));
    }

    /**
     * Credits a single species toward a "distinct species" objective (e.g. completionist quests).
     * A no-op if this species was already credited, so repeat catches of the same fish don't
     * inflate progress — only new species advance the count.
     */
    public void incrementDistinctSpecies(ResourceKey<Quest> questId, int targetCount, long currentDay, ResourceLocation speciesId) {
        QuestProgress existing = getProgress(questId);
        if (existing.caughtSpecies().contains(speciesId)) return;
        List<ResourceLocation> updated = new ArrayList<>(existing.caughtSpecies());
        updated.add(speciesId);
        boolean completed = updated.size() >= targetCount;
        long lastReset = existing.lastResetGameDay() == -1 ? currentDay : existing.lastResetGameDay();
        progress.put(questId, new QuestProgress(updated.size(), lastReset, completed, existing.claimed(), updated));
    }

    public boolean canClaim(ResourceKey<Quest> questId, int targetCount) {
        QuestProgress p = getProgress(questId);
        return p.completed() && !p.claimed() && p.currentCount() >= targetCount;
    }

    public void claim(ResourceKey<Quest> questId, int tokens) {
        QuestProgress p = getProgress(questId);
        progress.put(questId, new QuestProgress(p.currentCount(), p.lastResetGameDay(), p.completed(), true, p.caughtSpecies()));
        tokenBalance += tokens;
    }

    public void resetProgress(ResourceKey<Quest> questId) {
        progress.remove(questId);
    }

    /** Directly sets a quest's progress count — used by the admin debug/cheat command. */
    public void setProgress(ResourceKey<Quest> questId, int count, int targetCount, long currentDay) {
        QuestProgress existing = getProgress(questId);
        boolean completed = count >= targetCount;
        long lastReset = existing.lastResetGameDay() == -1 ? currentDay : existing.lastResetGameDay();
        progress.put(questId, new QuestProgress(count, lastReset, completed, existing.claimed(), existing.caughtSpecies()));
    }

    public void resetDailyIfNeeded(ResourceKey<Quest> questId, long currentDay) {
        QuestProgress p = getProgress(questId);
        if (p.lastResetGameDay() < currentDay) {
            progress.put(questId, new QuestProgress(0, currentDay, false, false, List.of()));
        }
    }

    public int getTokenBalance() {
        return tokenBalance;
    }

    /** Grants tokens outside of the per-quest claim flow — used by the global cleanup goal payout. */
    public void addTokens(int tokens) {
        tokenBalance += tokens;
    }

    /** Directly sets the token balance — used by the admin debug command. */
    public void setTokenBalance(int tokens) {
        tokenBalance = tokens;
    }

    /**
     * Attempt to purchase a shop entry. Returns false if the player can't afford it
     * or has already hit today's purchase limit for this entry.
     */
    public boolean purchase(ResourceKey<ShopEntry> key, ShopEntry entry) {
        if (tokenBalance < entry.cost()) return false;
        int current = purchaseCounts.getOrDefault(key, 0);
        if (entry.dailyMaxPurchases() > 0 && current >= entry.dailyMaxPurchases()) return false;
        tokenBalance -= entry.cost();
        purchaseCounts.put(key, current + 1);
        return true;
    }

    /** Clears every entry's purchase count once per in-game day, called alongside the daily quest reset. */
    public void resetDailyPurchasesIfNeeded(long currentDay) {
        if (lastPurchaseResetDay < currentDay) {
            purchaseCounts.clear();
            lastPurchaseResetDay = currentDay;
            shopRefreshCount = 0;
            shopRefreshNonce = 0;
            freeShopRerollUsed = false;
        }
    }

    /** Number of paid rerolls this in-game day, used to calculate the next paid price. */
    public int getShopRefreshCount() {
        return shopRefreshCount;
    }

    /** Nonce for the active shop draw; includes both free and paid rerolls. */
    public int getShopRefreshNonce() {
        return shopRefreshNonce;
    }

    public boolean isFreeShopRerollAvailable() {
        return !freeShopRerollUsed;
    }

    /**
     * Reroll today's active shop entries, granting one free use per in-game day before paid
     * rerolls. Paid reroll pricing escalates independently of the free use.
     * Returns false if the player can't afford the paid reroll.
     */
    public boolean refreshShop(int cost) {
        if (!freeShopRerollUsed) {
            freeShopRerollUsed = true;
            shopRefreshNonce++;
            return true;
        }
        if (tokenBalance < cost) return false;
        tokenBalance -= cost;
        shopRefreshCount++;
        shopRefreshNonce++;
        return true;
    }

    /** Force-clears purchase counts regardless of the current day — used by the admin refresh command. */
    public void forceRefreshPurchases() {
        purchaseCounts.clear();
        lastPurchaseResetDay = -1;
    }

    /** Force-resets a daily quest's progress regardless of the current day — used by the admin refresh command. */
    public void forceResetDaily(ResourceKey<Quest> questId, long currentDay) {
        progress.put(questId, new QuestProgress(0, currentDay, false, false, List.of()));
    }

    public Map<ResourceLocation, QuestProgress> getProgressSnapshot() {
        Map<ResourceLocation, QuestProgress> snap = new HashMap<>();
        progress.forEach((k, v) -> snap.put(k.location(), v));
        return snap;
    }

    public Map<ResourceLocation, Integer> getPurchaseCountSnapshot() {
        Map<ResourceLocation, Integer> snap = new HashMap<>();
        purchaseCounts.forEach((k, v) -> snap.put(k.location(), v));
        return snap;
    }

    // -------------------------------------------------------------------------
    // Encyclopedia reward claim API
    // -------------------------------------------------------------------------

    public boolean isEncyclopediaRewardClaimed(ResourceLocation fishId, EncyclopediaRewardSection section) {
        return claimedEncyclopediaRewards.contains(section.key(fishId));
    }

    /** @return false if this reward was already claimed (no-op), true if it was newly granted. */
    public boolean claimEncyclopediaReward(ResourceLocation fishId, EncyclopediaRewardSection section) {
        if (!claimedEncyclopediaRewards.add(section.key(fishId))) return false;
        tokenBalance += section.coinReward();
        return true;
    }

    public Set<String> getClaimedEncyclopediaRewardsSnapshot() {
        return new HashSet<>(claimedEncyclopediaRewards);
    }

    // -------------------------------------------------------------------------
    // Companion bond unlock API
    // -------------------------------------------------------------------------

    /** Whether this player's bond for {@code species} is unlocked and stored (never re-locks). */
    public boolean isCompanionBondUnlocked(ResourceLocation species) {
        return unlockedCompanionSpecies.contains(species);
    }

    /**
     * Stores a bond unlock. {@code true} if it was newly added — i.e. this call is the
     * transition to announce; a repeat call is a no-op and returns {@code false}. New unlocks
     * also queue a banner, drained by the next {@code QuestSyncPacket}.
     */
    public boolean unlockCompanionBond(ResourceLocation species) {
        if (!unlockedCompanionSpecies.add(species)) return false;
        pendingCompanionBanners.add(species);
        return true;
    }

    /** Every unlocked species, for the sync and the Companions tab. */
    public Set<ResourceLocation> getUnlockedCompanionSnapshot() {
        return new LinkedHashSet<>(unlockedCompanionSpecies);
    }

    /** Dev-only: forgets every stored unlock, so a self-test scene can start from locked. */
    public void clearCompanionBonds() {
        unlockedCompanionSpecies.clear();
        pendingCompanionBanners.clear();
    }

    /** Whether any unlock is waiting to be announced by the next quest sync. */
    public boolean hasPendingCompanionBanners() {
        return !pendingCompanionBanners.isEmpty();
    }

    /** Takes the queued banner species; the caller's sync packet announces them. */
    public List<ResourceLocation> drainPendingCompanionBanners() {
        List<ResourceLocation> drained = new ArrayList<>(pendingCompanionBanners);
        pendingCompanionBanners.clear();
        return drained;
    }

    // -------------------------------------------------------------------------
    // Lifetime tank placement API
    // -------------------------------------------------------------------------

    public int getLifetimeTankPlacements() {
        return lifetimeTankPlacements;
    }

    /** Called once per fish successfully inserted into any tank — see {@code QuestTracker#onTankChanged}. */
    public void incrementLifetimeTankPlacements() {
        lifetimeTankPlacements++;
    }
}

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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlayerQuestState {
    public static final int ACTIVE_DAILY_COUNT = 4;

    private final Map<ResourceKey<Quest>, QuestProgress> progress = new HashMap<>();
    private int tokenBalance = 0;
    private final Map<ResourceKey<ShopEntry>, Integer> purchaseCounts = new HashMap<>();
    private long lastPurchaseResetDay = -1;
    // Incremented each time the player spends tokens to manually reroll the shop (see refreshShop) -
    // mixed into ShopEntry#getActiveDailyShop's seed so the drawn set changes without waiting for
    // the next in-game day. Reset alongside the daily purchase-count rotation.
    private int shopRefreshCount = 0;
    // Composite keys from EncyclopediaRewardSection#key — one entry per fish/section reward
    // slot the player has claimed, ever. See claimEncyclopediaReward.
    private final Set<String> claimedEncyclopediaRewards = new HashSet<>();

    /**
     * {@code caughtSpecies} is only populated for "distinct species" objectives (e.g. the
     * completionist quests) — it tracks which species have already been credited so repeat
     * catches of the same species don't inflate {@code currentCount} further.
     */
    public record QuestProgress(int currentCount, long lastResetGameDay, boolean completed, boolean claimed,
                                 List<Identifier> caughtSpecies) {
        public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("count").forGetter(QuestProgress::currentCount),
                Codec.LONG.fieldOf("last_reset_day").forGetter(QuestProgress::lastResetGameDay),
                Codec.BOOL.fieldOf("completed").forGetter(QuestProgress::completed),
                Codec.BOOL.fieldOf("claimed").forGetter(QuestProgress::claimed),
                Codec.list(Identifier.CODEC).optionalFieldOf("caught_species", List.of()).forGetter(QuestProgress::caughtSpecies)
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
                ByteBufCodecs.collection(ArrayList::new, Identifier.STREAM_CODEC), QuestProgress::caughtSpecies,
                QuestProgress::new
        );
    }

    public static final Codec<PlayerQuestState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, QuestProgress.CODEC)
                    .fieldOf("progress").forGetter(state -> {
                        Map<Identifier, QuestProgress> map = new HashMap<>();
                        state.progress.forEach((k, v) -> map.put(k.identifier(), v));
                        return map;
                    }),
            Codec.INT.optionalFieldOf("token_balance", 0).forGetter(state -> state.tokenBalance),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .optionalFieldOf("purchase_counts", Map.of()).forGetter(state -> {
                        Map<Identifier, Integer> map = new HashMap<>();
                        state.purchaseCounts.forEach((k, v) -> map.put(k.identifier(), v));
                        return map;
                    }),
            Codec.LONG.optionalFieldOf("last_purchase_reset_day", -1L).forGetter(state -> state.lastPurchaseResetDay),
            Codec.STRING.listOf().optionalFieldOf("claimed_encyclopedia_rewards", List.of())
                    .forGetter(state -> new ArrayList<>(state.claimedEncyclopediaRewards)),
            Codec.INT.optionalFieldOf("shop_refresh_count", 0).forGetter(state -> state.shopRefreshCount)
    ).apply(i, (identMap, tokens, purchaseMap, lastPurchaseResetDay, claimedRewards, shopRefreshCount) -> {
        PlayerQuestState s = new PlayerQuestState();
        identMap.forEach((id, prog) ->
                s.progress.put(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, id), prog));
        s.tokenBalance = tokens;
        purchaseMap.forEach((id, count) ->
                s.purchaseCounts.put(ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, id), count));
        s.lastPurchaseResetDay = lastPurchaseResetDay;
        s.claimedEncyclopediaRewards.addAll(claimedRewards);
        s.shopRefreshCount = shopRefreshCount;
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
    public void incrementDistinctSpecies(ResourceKey<Quest> questId, int targetCount, long currentDay, Identifier speciesId) {
        QuestProgress existing = getProgress(questId);
        if (existing.caughtSpecies().contains(speciesId)) return;
        List<Identifier> updated = new ArrayList<>(existing.caughtSpecies());
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
        }
    }

    public int getShopRefreshCount() {
        return shopRefreshCount;
    }

    /**
     * Attempt to spend {@code cost} tokens to reroll today's active shop entries.
     * Returns false if the player can't afford it.
     */
    public boolean refreshShop(int cost) {
        if (tokenBalance < cost) return false;
        tokenBalance -= cost;
        shopRefreshCount++;
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

    public Map<Identifier, QuestProgress> getProgressSnapshot() {
        Map<Identifier, QuestProgress> snap = new HashMap<>();
        progress.forEach((k, v) -> snap.put(k.identifier(), v));
        return snap;
    }

    public Map<Identifier, Integer> getPurchaseCountSnapshot() {
        Map<Identifier, Integer> snap = new HashMap<>();
        purchaseCounts.forEach((k, v) -> snap.put(k.identifier(), v));
        return snap;
    }

    // -------------------------------------------------------------------------
    // Encyclopedia reward claim API
    // -------------------------------------------------------------------------

    public boolean isEncyclopediaRewardClaimed(Identifier fishId, EncyclopediaRewardSection section) {
        return claimedEncyclopediaRewards.contains(section.key(fishId));
    }

    /** @return false if this reward was already claimed (no-op), true if it was newly granted. */
    public boolean claimEncyclopediaReward(Identifier fishId, EncyclopediaRewardSection section) {
        if (!claimedEncyclopediaRewards.add(section.key(fishId))) return false;
        tokenBalance += section.coinReward();
        return true;
    }

    public Set<String> getClaimedEncyclopediaRewardsSnapshot() {
        return new HashSet<>(claimedEncyclopediaRewards);
    }
}

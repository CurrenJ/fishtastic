package grill24.fishtastic.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.ShopEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.Map;

public class PlayerQuestState {
    public static final int ACTIVE_DAILY_COUNT = 4;

    private final Map<ResourceKey<Quest>, QuestProgress> progress = new HashMap<>();
    private int tokenBalance = 0;
    private final Map<ResourceKey<ShopEntry>, Integer> purchaseCounts = new HashMap<>();
    private long lastPurchaseResetDay = -1;

    public record QuestProgress(int currentCount, long lastResetGameDay, boolean completed, boolean claimed) {
        public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("count").forGetter(QuestProgress::currentCount),
                Codec.LONG.fieldOf("last_reset_day").forGetter(QuestProgress::lastResetGameDay),
                Codec.BOOL.fieldOf("completed").forGetter(QuestProgress::completed),
                Codec.BOOL.fieldOf("claimed").forGetter(QuestProgress::claimed)
        ).apply(i, QuestProgress::new));

        public static final StreamCodec<ByteBuf, QuestProgress> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, QuestProgress::currentCount,
                ByteBufCodecs.VAR_LONG, QuestProgress::lastResetGameDay,
                ByteBufCodecs.BOOL, QuestProgress::completed,
                ByteBufCodecs.BOOL, QuestProgress::claimed,
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
            Codec.LONG.optionalFieldOf("last_purchase_reset_day", -1L).forGetter(state -> state.lastPurchaseResetDay)
    ).apply(i, (identMap, tokens, purchaseMap, lastPurchaseResetDay) -> {
        PlayerQuestState s = new PlayerQuestState();
        identMap.forEach((id, prog) ->
                s.progress.put(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, id), prog));
        s.tokenBalance = tokens;
        purchaseMap.forEach((id, count) ->
                s.purchaseCounts.put(ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, id), count));
        s.lastPurchaseResetDay = lastPurchaseResetDay;
        return s;
    }));

    public QuestProgress getProgress(ResourceKey<Quest> questId) {
        return progress.getOrDefault(questId, new QuestProgress(0, -1, false, false));
    }

    public void incrementCount(ResourceKey<Quest> questId, Quest quest, long currentDay) {
        QuestProgress existing = getProgress(questId);
        int newCount = existing.currentCount() + 1;
        boolean completed = newCount >= quest.objective().targetCount();
        // If this quest has never been reset before, anchor it to the current day
        // so that resetDailyIfNeeded won't wipe the progress on the next server start.
        long lastReset = existing.lastResetGameDay() == -1 ? currentDay : existing.lastResetGameDay();
        progress.put(questId, new QuestProgress(newCount, lastReset, completed, existing.claimed()));
    }

    public boolean canClaim(ResourceKey<Quest> questId, Quest quest) {
        QuestProgress p = getProgress(questId);
        return p.completed() && !p.claimed() && p.currentCount() >= quest.objective().targetCount();
    }

    public void claim(ResourceKey<Quest> questId, int tokens) {
        QuestProgress p = getProgress(questId);
        progress.put(questId, new QuestProgress(p.currentCount(), p.lastResetGameDay(), p.completed(), true));
        tokenBalance += tokens;
    }

    public void resetProgress(ResourceKey<Quest> questId) {
        progress.remove(questId);
    }

    /** Directly sets a quest's progress count — used by the admin debug/cheat command. */
    public void setProgress(ResourceKey<Quest> questId, int count, Quest quest, long currentDay) {
        QuestProgress existing = getProgress(questId);
        boolean completed = count >= quest.objective().targetCount();
        long lastReset = existing.lastResetGameDay() == -1 ? currentDay : existing.lastResetGameDay();
        progress.put(questId, new QuestProgress(count, lastReset, completed, existing.claimed()));
    }

    public void resetDailyIfNeeded(ResourceKey<Quest> questId, long currentDay) {
        QuestProgress p = getProgress(questId);
        if (p.lastResetGameDay() < currentDay) {
            progress.put(questId, new QuestProgress(0, currentDay, false, false));
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
        }
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
}

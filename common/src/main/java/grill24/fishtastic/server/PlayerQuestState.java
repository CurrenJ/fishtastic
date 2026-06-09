package grill24.fishtastic.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.Quest;
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
            Codec.INT.optionalFieldOf("token_balance", 0).forGetter(state -> state.tokenBalance)
    ).apply(i, (identMap, tokens) -> {
        PlayerQuestState s = new PlayerQuestState();
        identMap.forEach((id, prog) ->
                s.progress.put(ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, id), prog));
        s.tokenBalance = tokens;
        return s;
    }));

    public QuestProgress getProgress(ResourceKey<Quest> questId) {
        return progress.getOrDefault(questId, new QuestProgress(0, -1, false, false));
    }

    public void incrementCount(ResourceKey<Quest> questId, Quest quest) {
        QuestProgress existing = getProgress(questId);
        int newCount = existing.currentCount() + 1;
        boolean completed = newCount >= quest.objective().targetCount();
        progress.put(questId, new QuestProgress(newCount, existing.lastResetGameDay(), completed, existing.claimed()));
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

    public void resetDailyIfNeeded(ResourceKey<Quest> questId, long currentDay) {
        QuestProgress p = getProgress(questId);
        if (p.lastResetGameDay() < currentDay) {
            progress.put(questId, new QuestProgress(0, currentDay, false, false));
        }
    }

    public int getTokenBalance() {
        return tokenBalance;
    }

    public Map<Identifier, QuestProgress> getProgressSnapshot() {
        Map<Identifier, QuestProgress> snap = new HashMap<>();
        progress.forEach((k, v) -> snap.put(k.identifier(), v));
        return snap;
    }
}

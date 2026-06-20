package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record FishEncyclopediaEntry(Optional<String> lore, UnlockThresholds thresholds) {
    public record UnlockThresholds(int nameRevealCatches, int statsCatches, int typesCatches,
                                    int spawnConditionsCatches, int loreCatches) {
        public static final UnlockThresholds DEFAULT = new UnlockThresholds(1, 1, 5, 1, 25);
        public static final Codec<UnlockThresholds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("name_reveal_catches", 1).forGetter(UnlockThresholds::nameRevealCatches),
                Codec.INT.optionalFieldOf("stats_catches", 1).forGetter(UnlockThresholds::statsCatches),
                Codec.INT.optionalFieldOf("types_catches", 5).forGetter(UnlockThresholds::typesCatches),
                Codec.INT.optionalFieldOf("spawn_conditions_catches", 1).forGetter(UnlockThresholds::spawnConditionsCatches),
                Codec.INT.optionalFieldOf("lore_catches", 25).forGetter(UnlockThresholds::loreCatches)
        ).apply(i, UnlockThresholds::new));
    }

    public static final FishEncyclopediaEntry DEFAULT = new FishEncyclopediaEntry(Optional.empty(), UnlockThresholds.DEFAULT);

    public static final Codec<FishEncyclopediaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("lore").forGetter(FishEncyclopediaEntry::lore),
            UnlockThresholds.CODEC.optionalFieldOf("thresholds", UnlockThresholds.DEFAULT).forGetter(FishEncyclopediaEntry::thresholds)
    ).apply(i, FishEncyclopediaEntry::new));
}

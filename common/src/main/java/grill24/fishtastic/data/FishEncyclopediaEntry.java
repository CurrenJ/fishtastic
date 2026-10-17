package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Per-fish encyclopedia content. Unlock thresholds are not authored here — every species' are
 * derived from its rarity by {@link EncyclopediaTiers}; a legacy {@code thresholds} block in a
 * datapack entry is ignored.
 */
public record FishEncyclopediaEntry(Optional<String> lore) {
    /** Catch counts at which each gated section unlocks, for one species. */
    public record UnlockThresholds(int nameRevealCatches, int statsCatches, int typesCatches,
                                    int spawnConditionsCatches, int loreCatches) {}

    public static final FishEncyclopediaEntry DEFAULT = new FishEncyclopediaEntry(Optional.empty());

    public static final Codec<FishEncyclopediaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("lore").forGetter(FishEncyclopediaEntry::lore)
    ).apply(i, FishEncyclopediaEntry::new));
}

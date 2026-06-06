package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.util.FishingTarget;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Defines the minigame personality for a species of fish.
 * Referenced by ID from a {@link FishProfile} and looked up at session start.
 */
public record Temperament(
        float difficultyMin,
        float difficultyMax,
        List<FishingTarget.MovementPattern> patterns
) {
    public static final Codec<Temperament> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("difficulty_min").forGetter(Temperament::difficultyMin),
            Codec.FLOAT.fieldOf("difficulty_max").forGetter(Temperament::difficultyMax),
            FishingTarget.MovementPattern.CODEC.listOf().fieldOf("patterns").forGetter(Temperament::patterns)
    ).apply(i, Temperament::new));

    public float sampleDifficulty(RandomSource random) {
        return difficultyMin + random.nextFloat() * (difficultyMax - difficultyMin);
    }

    public FishingTarget.MovementPattern samplePattern(RandomSource random) {
        if (patterns.isEmpty()) return FishingTarget.MovementPattern.DRIFT;
        return patterns.get(random.nextInt(patterns.size()));
    }
}

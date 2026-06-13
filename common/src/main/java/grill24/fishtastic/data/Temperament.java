package grill24.fishtastic.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/**
 * Defines the minigame personality for a species of fish.
 * Referenced by ID from a {@link FishProfile} and looked up at session start.
 *
 * <p>Phases are evaluated in ascending threshold order; the active phase is the one
 * with the highest {@code threshold} that is still &le; the target's current catch progress.
 * Global {@link MovementParams} defined on the temperament act as defaults for every phase —
 * per-phase params override them where present.
 */
public record Temperament(
        float difficultyMin,
        float difficultyMax,
        List<PhaseRule> phases,
        Optional<MovementParams> params
) {
    public static final Codec<Temperament> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("difficulty_min").forGetter(Temperament::difficultyMin),
            Codec.FLOAT.fieldOf("difficulty_max").forGetter(Temperament::difficultyMax),
            listOrSingle(PhaseRule.CODEC).fieldOf("phases").forGetter(Temperament::phases),
            MovementParams.CODEC.optionalFieldOf("params").forGetter(Temperament::params)
    ).apply(i, Temperament::new));

    /** Accepts either a single element or an array; always serialises as an array. */
    private static <T> Codec<List<T>> listOrSingle(Codec<T> element) {
        return Codec.either(element.listOf(), element)
                .xmap(e -> e.map(l -> l, List::of), Either::left);
    }

    public float sampleDifficulty(RandomSource random) {
        return difficultyMin + random.nextFloat() * (difficultyMax - difficultyMin);
    }

    /**
     * Returns the phase list with global {@link #params} merged into each phase as defaults.
     * Phase-specific params still take priority. Safe to call when params is empty.
     */
    public List<PhaseRule> resolvedPhases() {
        if (params.isEmpty()) return phases;
        MovementParams global = params.get();
        return phases.stream().map(p -> p.withMergedParams(global)).toList();
    }
}

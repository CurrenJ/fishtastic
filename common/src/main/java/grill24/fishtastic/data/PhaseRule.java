package grill24.fishtastic.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.util.FishingTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One phase in a temperament's movement timeline.
 * The active phase is the one with the highest {@link #threshold} that is still
 * &le; the target's current catch progress. Phases should be listed in ascending
 * threshold order in JSON.
 */
public record PhaseRule(
        float threshold,
        List<FishingTarget.MovementPattern> patterns,
        Optional<MovementParams> params
) {
    public static final Codec<PhaseRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("threshold").forGetter(PhaseRule::threshold),
            listOrSingle(FishingTarget.MovementPattern.CODEC).fieldOf("patterns").forGetter(PhaseRule::patterns),
            MovementParams.CODEC.optionalFieldOf("params").forGetter(PhaseRule::params)
    ).apply(i, PhaseRule::new));

    /** Accepts either a single element or an array; always serialises as an array. */
    static <T> Codec<List<T>> listOrSingle(Codec<T> element) {
        return Codec.either(element.listOf(), element)
                .xmap(e -> e.map(l -> l, List::of), Either::left);
    }

    public static final StreamCodec<FriendlyByteBuf, PhaseRule> STREAM_CODEC = StreamCodec.of(
            PhaseRule::encode,
            PhaseRule::decode
    );

    private static void encode(FriendlyByteBuf buf, PhaseRule p) {
        buf.writeFloat(p.threshold());
        buf.writeVarInt(p.patterns().size());
        for (FishingTarget.MovementPattern pattern : p.patterns()) {
            buf.writeVarInt(pattern.ordinal());
        }
        buf.writeBoolean(p.params().isPresent());
        if (p.params().isPresent()) {
            MovementParams.STREAM_CODEC.encode(buf, p.params().get());
        }
    }

    private static PhaseRule decode(FriendlyByteBuf buf) {
        float threshold = buf.readFloat();
        int count = buf.readVarInt();
        FishingTarget.MovementPattern[] values = FishingTarget.MovementPattern.values();
        List<FishingTarget.MovementPattern> patterns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            patterns.add(values[buf.readVarInt()]);
        }
        Optional<MovementParams> params = buf.readBoolean()
                ? Optional.of(MovementParams.STREAM_CODEC.decode(buf))
                : Optional.empty();
        return new PhaseRule(threshold, patterns, params);
    }

    /**
     * Returns a new PhaseRule with {@code global} merged in as defaults,
     * but any phase-specific params take priority.
     */
    public PhaseRule withMergedParams(MovementParams global) {
        Optional<MovementParams> merged = params.isPresent()
                ? Optional.of(global.mergedWith(params.get()))
                : Optional.of(global);
        return new PhaseRule(threshold, patterns, merged);
    }
}

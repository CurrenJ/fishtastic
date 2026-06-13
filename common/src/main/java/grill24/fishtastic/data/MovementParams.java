package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Per-fish movement parameter overrides. Every field is optional — absent fields fall back to
 * the difficulty-scaled defaults computed in {@link grill24.fishtastic.util.FishingTarget}.
 * Place these on a {@link PhaseRule} or directly on a {@link Temperament} as global defaults.
 */
public record MovementParams(
        // DRIFT
        Optional<Float> driftIntervalMin,
        Optional<Float> driftIntervalMax,
        Optional<Float> driftSpeedMin,
        Optional<Float> driftSpeedMax,
        // DART
        Optional<Float> dartPauseMin,
        Optional<Float> dartPauseMax,
        Optional<Float> dartBurstTicks,
        // OSCILLATE
        Optional<Float> oscFrequency,
        Optional<Float> oscRePeriodMin,
        Optional<Float> oscRePeriodMax,
        Optional<Float> oscAmplitudeMin,
        Optional<Float> oscAmplitudeMax,
        // FLEE
        Optional<Float> fleeThreshold,
        Optional<Float> fleeSpeed,
        // Catch progress
        Optional<Float> catchProgressLoss,
        Optional<Float> initialCatchProgress
) {
    public static final Codec<MovementParams> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("drift_interval_min").forGetter(MovementParams::driftIntervalMin),
            Codec.FLOAT.optionalFieldOf("drift_interval_max").forGetter(MovementParams::driftIntervalMax),
            Codec.FLOAT.optionalFieldOf("drift_speed_min").forGetter(MovementParams::driftSpeedMin),
            Codec.FLOAT.optionalFieldOf("drift_speed_max").forGetter(MovementParams::driftSpeedMax),
            Codec.FLOAT.optionalFieldOf("dart_pause_min").forGetter(MovementParams::dartPauseMin),
            Codec.FLOAT.optionalFieldOf("dart_pause_max").forGetter(MovementParams::dartPauseMax),
            Codec.FLOAT.optionalFieldOf("dart_burst_ticks").forGetter(MovementParams::dartBurstTicks),
            Codec.FLOAT.optionalFieldOf("osc_frequency").forGetter(MovementParams::oscFrequency),
            Codec.FLOAT.optionalFieldOf("osc_re_period_min").forGetter(MovementParams::oscRePeriodMin),
            Codec.FLOAT.optionalFieldOf("osc_re_period_max").forGetter(MovementParams::oscRePeriodMax),
            Codec.FLOAT.optionalFieldOf("osc_amplitude_min").forGetter(MovementParams::oscAmplitudeMin),
            Codec.FLOAT.optionalFieldOf("osc_amplitude_max").forGetter(MovementParams::oscAmplitudeMax),
            Codec.FLOAT.optionalFieldOf("flee_threshold").forGetter(MovementParams::fleeThreshold),
            Codec.FLOAT.optionalFieldOf("flee_speed").forGetter(MovementParams::fleeSpeed),
            Codec.FLOAT.optionalFieldOf("catch_progress_loss").forGetter(MovementParams::catchProgressLoss),
            Codec.FLOAT.optionalFieldOf("initial_catch_progress").forGetter(MovementParams::initialCatchProgress)
    ).apply(i, MovementParams::new));

    public static final StreamCodec<ByteBuf, MovementParams> STREAM_CODEC = StreamCodec.of(
            MovementParams::encode,
            MovementParams::decode
    );

    private static void encode(ByteBuf buf, MovementParams p) {
        writeOpt(buf, p.driftIntervalMin());
        writeOpt(buf, p.driftIntervalMax());
        writeOpt(buf, p.driftSpeedMin());
        writeOpt(buf, p.driftSpeedMax());
        writeOpt(buf, p.dartPauseMin());
        writeOpt(buf, p.dartPauseMax());
        writeOpt(buf, p.dartBurstTicks());
        writeOpt(buf, p.oscFrequency());
        writeOpt(buf, p.oscRePeriodMin());
        writeOpt(buf, p.oscRePeriodMax());
        writeOpt(buf, p.oscAmplitudeMin());
        writeOpt(buf, p.oscAmplitudeMax());
        writeOpt(buf, p.fleeThreshold());
        writeOpt(buf, p.fleeSpeed());
        writeOpt(buf, p.catchProgressLoss());
        writeOpt(buf, p.initialCatchProgress());
    }

    private static MovementParams decode(ByteBuf buf) {
        return new MovementParams(
                readOpt(buf), readOpt(buf), readOpt(buf), readOpt(buf),
                readOpt(buf), readOpt(buf), readOpt(buf), readOpt(buf),
                readOpt(buf), readOpt(buf), readOpt(buf), readOpt(buf),
                readOpt(buf), readOpt(buf), readOpt(buf), readOpt(buf)
        );
    }

    private static void writeOpt(ByteBuf buf, Optional<Float> opt) {
        buf.writeBoolean(opt.isPresent());
        if (opt.isPresent()) buf.writeFloat(opt.get());
    }

    private static Optional<Float> readOpt(ByteBuf buf) {
        return buf.readBoolean() ? Optional.of(buf.readFloat()) : Optional.empty();
    }

    /**
     * Returns a new MovementParams merging {@code this} as base defaults with {@code override}
     * taking priority wherever it has a value present.
     */
    public MovementParams mergedWith(MovementParams override) {
        return new MovementParams(
                override.driftIntervalMin().or(this::driftIntervalMin),
                override.driftIntervalMax().or(this::driftIntervalMax),
                override.driftSpeedMin().or(this::driftSpeedMin),
                override.driftSpeedMax().or(this::driftSpeedMax),
                override.dartPauseMin().or(this::dartPauseMin),
                override.dartPauseMax().or(this::dartPauseMax),
                override.dartBurstTicks().or(this::dartBurstTicks),
                override.oscFrequency().or(this::oscFrequency),
                override.oscRePeriodMin().or(this::oscRePeriodMin),
                override.oscRePeriodMax().or(this::oscRePeriodMax),
                override.oscAmplitudeMin().or(this::oscAmplitudeMin),
                override.oscAmplitudeMax().or(this::oscAmplitudeMax),
                override.fleeThreshold().or(this::fleeThreshold),
                override.fleeSpeed().or(this::fleeSpeed),
                override.catchProgressLoss().or(this::catchProgressLoss),
                override.initialCatchProgress().or(this::initialCatchProgress)
        );
    }
}

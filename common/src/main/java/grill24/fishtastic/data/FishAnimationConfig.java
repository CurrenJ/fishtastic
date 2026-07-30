package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public sealed interface FishAnimationConfig
        permits FishAnimationConfig.HorizontalSwim, FishAnimationConfig.UprightFloat,
                FishAnimationConfig.FloorSit, FishAnimationConfig.Planted,
                FishAnimationConfig.BellyDown, FishAnimationConfig.UprightSit {

    Codec<FishAnimationConfig> CODEC = Codec.STRING.dispatch(
            "mode",
            FishAnimationConfig::modeName,
            mode -> switch (mode) {
                case "horizontal_swim" -> HorizontalSwim.MAP_CODEC;
                case "upright_float"   -> UprightFloat.MAP_CODEC;
                case "floor_sit"       -> FloorSit.MAP_CODEC;
                case "planted"         -> Planted.MAP_CODEC;
                case "belly_down"      -> BellyDown.MAP_CODEC;
                case "upright_sit"     -> UprightSit.MAP_CODEC;
                default -> throw new IllegalArgumentException("Unknown fish animation mode: " + mode);
            }
    );

    // Force-loads every permit class up front so parallel registry decoding never races to
    // classload one lazily, which can deadlock the classloader in NeoForge dev.
    boolean WARMED_UP = HorizontalSwim.DEFAULT != null && UprightFloat.DEFAULT != null
            && FloorSit.DEFAULT != null && Planted.DEFAULT != null
            && BellyDown.DEFAULT != null && UprightSit.DEFAULT != null;

    String modeName();

    /**
     * Default swimming mode: fish lies horizontal with bobbing, surfing tilt, and organic Y-axis wiggle.
     * Matches pre-existing behaviour for all fish that do not specify an animation block.
     */
    record HorizontalSwim(
            float bobAmplitude,
            float bobHertz,
            float wiggleScale,
            float surfFactor
    ) implements FishAnimationConfig {
        public static final HorizontalSwim DEFAULT = new HorizontalSwim(0.125f, 0.08f, 0.5f, 0.12f);

        static final MapCodec<HorizontalSwim> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("bob_amplitude", 0.125f).forGetter(HorizontalSwim::bobAmplitude),
                Codec.FLOAT.optionalFieldOf("bob_hertz",     0.08f ).forGetter(HorizontalSwim::bobHertz),
                Codec.FLOAT.optionalFieldOf("wiggle_scale",  0.5f  ).forGetter(HorizontalSwim::wiggleScale),
                Codec.FLOAT.optionalFieldOf("surf_factor",   0.12f ).forGetter(HorizontalSwim::surfFactor)
        ).apply(i, HorizontalSwim::new));

        @Override public String modeName() { return "horizontal_swim"; }
    }

    /**
     * Upright floating mode: creature stays vertical, gently bobs, and slowly drifts/spins on Y.
     * Suitable for jellyfish and similar creatures.
     */
    record UprightFloat(
            float bobAmplitude,
            float bobHertz,
            float spinRate,
            boolean diagonalTexture
    ) implements FishAnimationConfig {
        public static final UprightFloat DEFAULT = new UprightFloat(0.05f, 0.03f, 0.4f, true);

        static final MapCodec<UprightFloat> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("bob_amplitude",    0.05f).forGetter(UprightFloat::bobAmplitude),
                Codec.FLOAT.optionalFieldOf("bob_hertz",        0.03f).forGetter(UprightFloat::bobHertz),
                Codec.FLOAT.optionalFieldOf("spin_rate",        0.4f ).forGetter(UprightFloat::spinRate),
                // Most fish textures are painted diagonally (facing the top-right corner, like vanilla
                // Cod/Salmon) so a -45° roll is needed to stand them upright. Set false for a texture
                // that's already painted facing straight up, to render with no extra roll at all.
                Codec.BOOL.optionalFieldOf("diagonal_texture",  true ).forGetter(UprightFloat::diagonalTexture)
        ).apply(i, UprightFloat::new));

        @Override public String modeName() { return "upright_float"; }
    }

    /**
     * Floor-sitting mode: creature lies flat on the tank floor (X-rotated 90°) and occasionally
     * rotates slowly around Y. Suitable for starfish and similar benthic creatures.
     */
    record FloorSit(
            float floorOffset,
            float rotationAmplitude,
            float rotationHertz
    ) implements FishAnimationConfig {
        public static final FloorSit DEFAULT = new FloorSit(0.03f, 8.0f, 0.004f);

        static final MapCodec<FloorSit> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("floor_offset",       0.03f ).forGetter(FloorSit::floorOffset),
                Codec.FLOAT.optionalFieldOf("rotation_amplitude", 8.0f  ).forGetter(FloorSit::rotationAmplitude),
                Codec.FLOAT.optionalFieldOf("rotation_hertz",     0.004f).forGetter(FloorSit::rotationHertz)
        ).apply(i, FloorSit::new));

        @Override public String modeName() { return "floor_sit"; }
    }

    /**
     * Planted mode: creature stays upright with its base fixed at the tank floor, swaying slowly
     * side-to-side with the base as the pivot. Suitable for garden eels.
     */
    record Planted(
            float plantDepth,
            float wiggleAmplitude,
            float wiggleHertz
    ) implements FishAnimationConfig {
        public static final Planted DEFAULT = new Planted(1f / 16f, 3.0f, 0.018f);

        static final MapCodec<Planted> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("plant_depth",      1f / 16f).forGetter(Planted::plantDepth),
                Codec.FLOAT.optionalFieldOf("wiggle_amplitude", 3.0f  ).forGetter(Planted::wiggleAmplitude),
                Codec.FLOAT.optionalFieldOf("wiggle_hertz",     0.018f).forGetter(Planted::wiggleHertz)
        ).apply(i, Planted::new));

        @Override public String modeName() { return "planted"; }
    }

    /**
     * Belly-down glide mode: creature lies flat with its belly facing world-down, gently bobbing
     * and slowly banking its wings. Suitable for manta rays and similar pelagic flat fish.
     */
    record BellyDown(
            float bobAmplitude,
            float bobHertz,
            float bankAmplitude,
            float bankHertz
    ) implements FishAnimationConfig {
        public static final BellyDown DEFAULT = new BellyDown(0.08f, 0.05f, 10.0f, 0.012f);

        static final MapCodec<BellyDown> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("bob_amplitude",  0.08f ).forGetter(BellyDown::bobAmplitude),
                Codec.FLOAT.optionalFieldOf("bob_hertz",      0.05f ).forGetter(BellyDown::bobHertz),
                Codec.FLOAT.optionalFieldOf("bank_amplitude", 10.0f ).forGetter(BellyDown::bankAmplitude),
                Codec.FLOAT.optionalFieldOf("bank_hertz",     0.012f).forGetter(BellyDown::bankHertz)
        ).apply(i, BellyDown::new));

        @Override public String modeName() { return "belly_down"; }
    }

    /**
     * Upright-sitting mode: creature stands vertically anchored to the tank floor (unlike
     * {@link FloorSit}, which lies flat) with gentle Y-axis rotation sway. Suitable for
     * ground-dwelling upright creatures like nudibranchs.
     */
    record UprightSit(
            float floorOffset,
            float rotationAmplitude,
            float rotationHertz,
            boolean diagonalTexture
    ) implements FishAnimationConfig {
        // The renderer separately compensates for this mode's centre-pivot (an upright item's
        // bottom otherwise sits below the floor) scaled to each fish's own per-catch size, so
        // floorOffset here is purely a small manual nudge on top of that, like FloorSit's.
        public static final UprightSit DEFAULT = new UprightSit(0.0f, 8.0f, 0.004f, true);

        static final MapCodec<UprightSit> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("floor_offset",       0.0f  ).forGetter(UprightSit::floorOffset),
                Codec.FLOAT.optionalFieldOf("rotation_amplitude", 8.0f  ).forGetter(UprightSit::rotationAmplitude),
                Codec.FLOAT.optionalFieldOf("rotation_hertz",     0.004f).forGetter(UprightSit::rotationHertz),
                // See UprightFloat.diagonalTexture: false for textures already painted facing straight up.
                Codec.BOOL.optionalFieldOf("diagonal_texture",    true  ).forGetter(UprightSit::diagonalTexture)
        ).apply(i, UprightSit::new));

        @Override public String modeName() { return "upright_sit"; }
    }
}

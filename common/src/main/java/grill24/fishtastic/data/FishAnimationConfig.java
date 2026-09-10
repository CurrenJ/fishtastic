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
            float surfFactor,
            boolean diagonalTexture
    ) implements FishAnimationConfig {
        public static final HorizontalSwim DEFAULT = new HorizontalSwim(0.125f, 0.08f, 0.5f, 0.12f, true);

        static final MapCodec<HorizontalSwim> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("bob_amplitude", 0.125f).forGetter(HorizontalSwim::bobAmplitude),
                Codec.FLOAT.optionalFieldOf("bob_hertz",     0.08f ).forGetter(HorizontalSwim::bobHertz),
                Codec.FLOAT.optionalFieldOf("wiggle_scale",  0.5f  ).forGetter(HorizontalSwim::wiggleScale),
                Codec.FLOAT.optionalFieldOf("surf_factor",   0.12f ).forGetter(HorizontalSwim::surfFactor),
                // See UprightFloat.diagonalTexture: most fish textures are painted diagonally
                // (facing the top-right corner, like vanilla Cod/Salmon), so a +45° roll is needed
                // to lay them horizontal. Set false for a texture already painted facing straight
                // right, to render with no extra roll at all.
                Codec.BOOL.optionalFieldOf("diagonal_texture", true ).forGetter(HorizontalSwim::diagonalTexture)
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
            boolean diagonalTexture,
            float pulseStretch
    ) implements FishAnimationConfig {
        /**
         * How far the bell deforms at the peak of a pulse, as a fraction of the creature's height
         * (docs/fish-sim-locomotion.md §3.6). Positive stretches it vertically and — volume is
         * preserved — narrows it by the same amount of matter, which is what a bell doing its one
         * stroke actually looks like: it pulls in and elongates, then relaxes.
         *
         * <p>Driven by the engine's own pulse ({@code FlockEngine.renderShape}), not by a clock of
         * its own, so the squeeze and the thrust are the same event. Zero opts a species out.
         */
        public static final float DEFAULT_PULSE_STRETCH = 0.10f;

        public static final UprightFloat DEFAULT =
                new UprightFloat(0.05f, 0.03f, 0.4f, true, DEFAULT_PULSE_STRETCH);

        static final MapCodec<UprightFloat> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("bob_amplitude",    0.05f).forGetter(UprightFloat::bobAmplitude),
                Codec.FLOAT.optionalFieldOf("bob_hertz",        0.03f).forGetter(UprightFloat::bobHertz),
                Codec.FLOAT.optionalFieldOf("spin_rate",        0.4f ).forGetter(UprightFloat::spinRate),
                // Most fish textures are painted diagonally (facing the top-right corner, like vanilla
                // Cod/Salmon) so a -45° roll is needed to stand them upright. Set false for a texture
                // that's already painted facing straight up, to render with no extra roll at all.
                Codec.BOOL.optionalFieldOf("diagonal_texture",  true ).forGetter(UprightFloat::diagonalTexture),
                Codec.FLOAT.optionalFieldOf("pulse_stretch", DEFAULT_PULSE_STRETCH).forGetter(UprightFloat::pulseStretch)
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
            float rotationHertz,
            float scuttleSquash
    ) implements FishAnimationConfig {
        /**
         * How far the body deforms as it pushes off into a scuttle, as a fraction of its own size
         * (docs/fish-sim-locomotion.md §3.6), driven by the engine's crawl envelope
         * ({@code FlockEngine.renderShape}).
         *
         * <p>A flat-lying creature deforms <i>in its own plane</i> rather than vertically: it
         * shortens along the body and spreads across it, area preserved. Squashing a sprite that
         * is lying face-up along world Y would deform it through its own zero thickness and show
         * nothing at all.
         *
         * <p>Smaller than a bell's by design — a crawler's push-off should be a hint, not a pump.
         * Zero opts a species out.
         */
        public static final float DEFAULT_SCUTTLE_SQUASH = 0.05f;

        public static final FloorSit DEFAULT = new FloorSit(0.03f, 8.0f, 0.004f, DEFAULT_SCUTTLE_SQUASH);

        static final MapCodec<FloorSit> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("floor_offset",       0.03f ).forGetter(FloorSit::floorOffset),
                Codec.FLOAT.optionalFieldOf("rotation_amplitude", 8.0f  ).forGetter(FloorSit::rotationAmplitude),
                Codec.FLOAT.optionalFieldOf("rotation_hertz",     0.004f).forGetter(FloorSit::rotationHertz),
                Codec.FLOAT.optionalFieldOf("scuttle_squash", DEFAULT_SCUTTLE_SQUASH).forGetter(FloorSit::scuttleSquash)
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
            boolean diagonalTexture,
            float pivotFraction,
            float scuttleSquash
    ) implements FishAnimationConfig {
        /**
         * Distance from the item's centre down to the lowest <i>visible</i> pixel of its texture,
         * as a fraction of the item's own height — how far to lift the creature so it stands on
         * the sand rather than hovering above it or sinking into it.
         *
         * <p>{@link #DEFAULT_PIVOT_FRACTION} (half the item) is what an upright sprite whose art
         * runs all the way to the bottom edge of its canvas needs. Art that stops short needs
         * less, by exactly the transparent margin below it, and using the full half instead floats
         * it by that much: harmless for a texture that nearly fills its canvas, glaring for one
         * that fills half of it (trapania_scurra hovered by roughly its own visible height).
         *
         * <p>Measured from the texture's alpha by {@code tools/fish-render-calibration.ps1}, which
         * also accounts for {@link #diagonalTexture()}'s 45° roll — after that roll the lowest
         * point is a rotated corner, not the bottom edge. Re-run it when a texture changes.
         */
        public static final float DEFAULT_PIVOT_FRACTION = 0.5f;

        /**
         * As {@link FloorSit#DEFAULT_SCUTTLE_SQUASH}, but for a creature standing up: it crouches
         * vertically as it pushes off and widens by the volume it loses, then relaxes. The
         * deformation is pivoted about {@link #pivotFraction()} — the point where the creature
         * touches the sand — so the squash sinks nothing into the floor.
         */
        public static final float DEFAULT_SCUTTLE_SQUASH = FloorSit.DEFAULT_SCUTTLE_SQUASH;

        // floorOffset is a small manual nudge on top of the measured pivot, like FloorSit's.
        public static final UprightSit DEFAULT =
                new UprightSit(0.0f, 8.0f, 0.004f, true, DEFAULT_PIVOT_FRACTION, DEFAULT_SCUTTLE_SQUASH);

        static final MapCodec<UprightSit> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.optionalFieldOf("floor_offset",       0.0f  ).forGetter(UprightSit::floorOffset),
                Codec.FLOAT.optionalFieldOf("rotation_amplitude", 8.0f  ).forGetter(UprightSit::rotationAmplitude),
                Codec.FLOAT.optionalFieldOf("rotation_hertz",     0.004f).forGetter(UprightSit::rotationHertz),
                // See UprightFloat.diagonalTexture: false for textures already painted facing straight up.
                Codec.BOOL.optionalFieldOf("diagonal_texture",    true  ).forGetter(UprightSit::diagonalTexture),
                Codec.FLOAT.optionalFieldOf("pivot_fraction", DEFAULT_PIVOT_FRACTION).forGetter(UprightSit::pivotFraction),
                Codec.FLOAT.optionalFieldOf("scuttle_squash", DEFAULT_SCUTTLE_SQUASH).forGetter(UprightSit::scuttleSquash)
        ).apply(i, UprightSit::new));

        @Override public String modeName() { return "upright_sit"; }
    }
}

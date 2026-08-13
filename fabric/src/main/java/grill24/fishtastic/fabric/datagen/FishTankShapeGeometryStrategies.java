package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonObject;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.shapegen.CornerTaperProfile;
import grill24.fishtastic.shapegen.SandGeometryGenerator;
import grill24.fishtastic.shapegen.TaperedFrameGeometryGenerator;
import grill24.fishtastic.shapegen.TaperedGlassGeometryGenerator;

import java.util.function.IntFunction;

/**
 * Maps each {@link FishTankShape} to the {@code tools/tank-shape-gen} calls that produce its
 * 64-permutation frame/sand/glass models — the single place a new shape's datagen wiring gets
 * added, so {@code FishTankFrameModelProvider}/{@code GlassModelProvider}/{@code SandModelProvider}
 * don't each need their own copy of this mapping.
 *
 * <p>Every shape is authored the same way now: a {@link CornerTaperProfile} (per-row corner-post
 * width read off a reference image — see the {@code tank-shape-image-to-datagen} skill), routed
 * through the {@code Tapered*} generators. {@code STANDARD} is simply the uniform-width profile,
 * so there is no separate non-tapered code path to maintain.
 */
final class FishTankShapeGeometryStrategies {

    record Strategy(IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand) {}

    private FishTankShapeGeometryStrategies() {}

    static Strategy forShape(FishTankShape shape) {
        CornerTaperProfile profile = switch (shape) {
            case STANDARD -> CornerTaperProfile.STANDARD;
            case TRIMMED -> CornerTaperProfile.TRIMMED;
            case REINFORCED -> CornerTaperProfile.REINFORCED;
        };
        return new Strategy(
                perm -> TaperedFrameGeometryGenerator.generate(perm, profile),
                perm -> TaperedGlassGeometryGenerator.generate(perm, profile),
                perm -> SandGeometryGenerator.generate(perm, profile)
        );
    }
}

package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonObject;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.shapegen.CornerTaperProfile;
import grill24.fishtastic.shapegen.OrnateFrameGeometryGenerator;
import grill24.fishtastic.shapegen.OrnateGlassGeometryGenerator;
import grill24.fishtastic.shapegen.SandGeometryGenerator;
import grill24.fishtastic.shapegen.ShaggyFrameGeometryGenerator;
import grill24.fishtastic.shapegen.ShaggyGlassGeometryGenerator;
import grill24.fishtastic.shapegen.ShellFrameGeometryGenerator;
import grill24.fishtastic.shapegen.SteppedSandGeometryGenerator;
import grill24.fishtastic.shapegen.TaperedFrameGeometryGenerator;
import grill24.fishtastic.shapegen.TaperedGlassGeometryGenerator;

import java.util.function.IntFunction;

/**
 * Maps each {@link FishTankShape} to the {@code tools/tank-shape-gen} calls that produce its
 * 64-permutation frame/sand/glass models — the single place a new shape's datagen wiring gets
 * added, so {@code FishTankFrameModelProvider}/{@code GlassModelProvider}/{@code SandModelProvider}
 * don't each need their own copy of this mapping.
 *
 * <p>Most shapes are authored from a {@link CornerTaperProfile} (per-row corner-post width read off
 * a reference image — see the {@code tank-shape-image-to-datagen} skill). The stepped-octagon
 * shapes ({@code FACETED}/{@code BASTION}) add a chamfered base ring and a stepped sand; the inlay
 * shapes ({@code ORNATE}/{@code SHAGGY}) have no taper at all and instead pair a per-shape
 * inlay-span frame generator with a glass generator that punches the matching holes.
 */
final class FishTankShapeGeometryStrategies {

    record Strategy(IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand) {}

    private FishTankShapeGeometryStrategies() {}

    static Strategy forShape(FishTankShape shape) {
        return switch (shape) {
            case STANDARD -> cornerTaper(CornerTaperProfile.STANDARD);
            case TRIMMED -> cornerTaper(CornerTaperProfile.TRIMMED);
            case REINFORCED -> cornerTaper(CornerTaperProfile.REINFORCED);
            case FACETED -> stepped(CornerTaperProfile.FACETED);
            case BASTION -> stepped(CornerTaperProfile.BASTION);
            case ORNATE -> new Strategy(
                    OrnateFrameGeometryGenerator::generate,
                    OrnateGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD));
            case SHAGGY -> new Strategy(
                    ShaggyFrameGeometryGenerator::generate,
                    ShaggyGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD));
        };
    }

    /** Plain corner-taper shape: tapered corner-post frame + tapered glass + square sand. */
    private static Strategy cornerTaper(CornerTaperProfile profile) {
        return new Strategy(
                perm -> TaperedFrameGeometryGenerator.generate(perm, profile),
                perm -> TaperedGlassGeometryGenerator.generate(perm, profile),
                perm -> SandGeometryGenerator.generate(perm, profile)
        );
    }

    /** Stepped-octagon shape: chamfered-ring frame (for {@code 16} rows) + tapered glass + stepped sand. */
    private static Strategy stepped(CornerTaperProfile profile) {
        return new Strategy(
                perm -> ShellFrameGeometryGenerator.generate(perm, profile),
                perm -> TaperedGlassGeometryGenerator.generate(perm, profile),
                perm -> SteppedSandGeometryGenerator.generate(perm, profile)
        );
    }
}

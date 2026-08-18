package grill24.fishtastic.shapegen;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.function.IntFunction;

/**
 * The single list of every shipped tank shape's geometry strategy — frame/glass/sand generator
 * calls keyed by the shape's serialized name (matching {@code FishTankShape.getSerializedName()},
 * duplicated as plain strings here since this module has no Minecraft dependency).
 *
 * <p>Fabric datagen's {@code FishTankShapeGeometryStrategies} looks up entries here instead of
 * keeping its own copy of the mapping, and the geometry-safety test ({@code
 * TankShapeConnectivitySafetyTest}) sweeps this same list — so a new shape's datagen wiring and its
 * safety coverage can never drift apart; adding a shape here is the one place both pick it up.
 */
public final class TankShapeGeometryStrategies {

    public record Strategy(String name, IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand) {}

    public static final List<Strategy> ALL = List.of(
            cornerTaper("standard", CornerTaperProfile.STANDARD),
            stepped("sturdy", CornerTaperProfile.STURDY),
            stepped("trimmed", CornerTaperProfile.TRIMMED),
            stepped("reinforced", CornerTaperProfile.REINFORCED),
            stepped("faceted", CornerTaperProfile.FACETED),
            stepped("bastion", CornerTaperProfile.BASTION),
            stepped("honed", CornerTaperProfile.HONED),
            stepped("rampart", CornerTaperProfile.RAMPART),
            new Strategy("ornate",
                    OrnateFrameGeometryGenerator::generate,
                    OrnateGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD)),
            new Strategy("shaggy",
                    ShaggyFrameGeometryGenerator::generate,
                    ShaggyGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD)),
            new Strategy("bramble",
                    BrambleFrameGeometryGenerator::generate,
                    BrambleGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD)),
            new Strategy("tooth",
                    perm -> CombFrameGeometryGenerator.generate(perm, ToothTankSpans.SPEC),
                    perm -> CombGlassGeometryGenerator.generate(perm, ToothTankSpans.SPEC),
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD)),
            new Strategy("film",
                    perm -> CombFrameGeometryGenerator.generate(perm, FilmTankSpans.SPEC),
                    perm -> CombGlassGeometryGenerator.generate(perm, FilmTankSpans.SPEC),
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD)),
            new Strategy("skylight",
                    perm -> TaperedFrameGeometryGenerator.generateSkylight(perm, CornerTaperProfile.STANDARD),
                    perm -> TaperedGlassGeometryGenerator.generateSkylight(perm, CornerTaperProfile.STANDARD),
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD)),
            new Strategy("arch",
                    ArchFrameGeometryGenerator::generate,
                    ArchGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.uniform(2))),
            new Strategy("mullion",
                    MullionFrameGeometryGenerator::generate,
                    MullionGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD)),
            new Strategy("lattice",
                    LatticeFrameGeometryGenerator::generate,
                    LatticeGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.uniform(2))),
            new Strategy("dune",
                    perm -> TaperedFrameGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    perm -> TaperedGlassGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    DuneSandGeometryGenerator::generate)
    );

    private TankShapeGeometryStrategies() {}

    public static Strategy byName(String name) {
        return ALL.stream()
                .filter(strategy -> strategy.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No tank shape geometry strategy named '" + name + "'"));
    }

    /** Plain corner-taper shape: tapered corner-post frame + tapered glass + square sand. */
    private static Strategy cornerTaper(String name, CornerTaperProfile profile) {
        return new Strategy(name,
                perm -> TaperedFrameGeometryGenerator.generate(perm, profile),
                perm -> TaperedGlassGeometryGenerator.generate(perm, profile),
                perm -> SandGeometryGenerator.generate(perm, profile));
    }

    /** Stepped-octagon shape: chamfered-ring frame (for {@code 16} rows) + tapered glass + stepped sand. */
    private static Strategy stepped(String name, CornerTaperProfile profile) {
        return new Strategy(name,
                perm -> ShellFrameGeometryGenerator.generate(perm, profile),
                perm -> TaperedGlassGeometryGenerator.generate(perm, profile),
                perm -> SteppedSandGeometryGenerator.generate(perm, profile));
    }
}

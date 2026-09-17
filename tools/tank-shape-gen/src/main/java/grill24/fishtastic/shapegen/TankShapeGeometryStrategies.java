package grill24.fishtastic.shapegen;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
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

    /**
     * {@code cornerFragment} is null for shapes whose frame generator has no combined-face corner
     * gate to begin with (no diagonal-corner-post bug is possible there) — see
     * {@code TankShapeConnectivitySafetyTest}'s guardrail test for the shapes this covers.
     *
     * <p>{@code edgeFragment} is null for shapes with no taper/plate abstraction to borrow edge
     * geometry from — the same set excluded from corner fragments, plus Ornate/Shaggy/Creeper
     * (deferred: hand-authored, non-{@code Run}-driven corner geometry) — see
     * {@code FishTankShape#hasEdgeDiagonalFragments()}.
     *
     * <p>{@code edgeGlassFillFragment} is non-null for exactly the same shapes as {@code
     * edgeFragment} (both come from the same taper/plate abstraction) — its glass-texture
     * counterpart, restoring the flush glass sliver the base glass bake omits at an eligible edge's
     * cap band, for whichever of the two shapes calling it fails to render the frame beam because
     * the edge-diagonal cell is filled — see {@code TaperedGlassGeometryGenerator#generateEdgeGlassFillFragment}.
     *
     * <p>{@code cornerGlassFillFragment} is non-null only for the four shapes whose ceiling/floor is
     * a frame ring around a glass pane instead of a solid slab (skylight, vitrine, cupola, hutch):
     * their base glass pane now notches out any corner whose two adjacent faces are both open (see
     * {@code TaperedGlassGeometryGenerator#addHorizontalPaneBoxes}), to avoid overlapping/z-fighting
     * the diagonal-aware corner plug {@code cornerFragment} composites back in there when the
     * diagonal neighbor cell is empty. When that cell is filled instead (no plug), this fragment
     * restores the notch as flush glass — see {@code FishTankCompositeModelData#getDiagonalGlassFillMask}.
     */
    public record Strategy(String name, IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand,
                            BiFunction<TankCorner, Integer, JsonObject> cornerFragment, Function<TankEdge, JsonObject> edgeFragment,
                            BiFunction<TankEdge, TankCorner, JsonObject> edgeGlassFillFragment,
                            BiFunction<TankCorner, Integer, JsonObject> cornerGlassFillFragment) {
        public Strategy(String name, IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand) {
            this(name, frame, glass, sand, null, null, null, null);
        }

        public Strategy(String name, IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand,
                         BiFunction<TankCorner, Integer, JsonObject> cornerFragment) {
            this(name, frame, glass, sand, cornerFragment, null, null, null);
        }

        public Strategy(String name, IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand,
                         BiFunction<TankCorner, Integer, JsonObject> cornerFragment, Function<TankEdge, JsonObject> edgeFragment) {
            this(name, frame, glass, sand, cornerFragment, edgeFragment, null, null);
        }

        public Strategy(String name, IntFunction<JsonObject> frame, IntFunction<JsonObject> glass, IntFunction<JsonObject> sand,
                         BiFunction<TankCorner, Integer, JsonObject> cornerFragment, Function<TankEdge, JsonObject> edgeFragment,
                         BiFunction<TankEdge, TankCorner, JsonObject> edgeGlassFillFragment) {
            this(name, frame, glass, sand, cornerFragment, edgeFragment, edgeGlassFillFragment, null);
        }
    }

    private static BiFunction<TankCorner, Integer, JsonObject> taperedCornerFragment(CornerTaperProfile profile) {
        return (corner, capState) -> TaperedFrameGeometryGenerator.generateCornerFragment(
                corner, (capState & 2) != 0, (capState & 1) != 0, profile);
    }

    private static BiFunction<TankCorner, Integer, JsonObject> shellCornerFragment(CornerTaperProfile profile) {
        return (corner, capState) -> ShellFrameGeometryGenerator.generateCornerFragment(
                corner, (capState & 2) != 0, (capState & 1) != 0, profile);
    }

    private static Function<TankEdge, JsonObject> taperedEdgeFragment(CornerTaperProfile profile) {
        return edge -> TaperedFrameGeometryGenerator.generateEdgeFragment(edge, profile);
    }

    private static Function<TankEdge, JsonObject> shellEdgeFragment(CornerTaperProfile profile) {
        return edge -> ShellFrameGeometryGenerator.generateEdgeFragment(edge, profile);
    }

    /** Every shape's glass comes from {@link TaperedGlassGeometryGenerator} regardless of frame
     * family (tapered or shell), so the glass-fill fragment factory needs no shell variant. */
    private static BiFunction<TankEdge, TankCorner, JsonObject> edgeGlassFillFragment(CornerTaperProfile profile) {
        return (edge, corner) -> TaperedGlassGeometryGenerator.generateEdgeGlassFillFragment(edge, corner, profile);
    }

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
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    (corner, capState) -> OrnateFrameGeometryGenerator.generateCornerFragment(corner, (capState & 2) != 0, (capState & 1) != 0),
                    taperedEdgeFragment(CornerTaperProfile.STANDARD), edgeGlassFillFragment(CornerTaperProfile.STANDARD)),
            new Strategy("shaggy",
                    ShaggyFrameGeometryGenerator::generate,
                    ShaggyGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    (corner, capState) -> ShaggyFrameGeometryGenerator.generateCornerFragment(corner, (capState & 2) != 0, (capState & 1) != 0),
                    taperedEdgeFragment(CornerTaperProfile.STANDARD), edgeGlassFillFragment(CornerTaperProfile.STANDARD)),
            new Strategy("bramble",
                    BrambleFrameGeometryGenerator::generate,
                    BrambleGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD)),
            new Strategy("tooth",
                    perm -> CombFrameGeometryGenerator.generate(perm, ToothTankSpans.SPEC),
                    perm -> CombGlassGeometryGenerator.generate(perm, ToothTankSpans.SPEC),
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    null,
                    edge -> CombFrameGeometryGenerator.generateEdgeFragment(edge, ToothTankSpans.SPEC),
                    (edge, corner) -> CombGlassGeometryGenerator.generateEdgeGlassFillFragment(edge, corner, ToothTankSpans.SPEC)),
            new Strategy("film",
                    perm -> CombFrameGeometryGenerator.generate(perm, FilmTankSpans.SPEC),
                    perm -> CombGlassGeometryGenerator.generate(perm, FilmTankSpans.SPEC),
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    null,
                    edge -> CombFrameGeometryGenerator.generateEdgeFragment(edge, FilmTankSpans.SPEC),
                    (edge, corner) -> CombGlassGeometryGenerator.generateEdgeGlassFillFragment(edge, corner, FilmTankSpans.SPEC)),
            new Strategy("skylight",
                    perm -> TaperedFrameGeometryGenerator.generateSkylight(perm, CornerTaperProfile.STANDARD),
                    perm -> TaperedGlassGeometryGenerator.generateSkylight(perm, CornerTaperProfile.STANDARD),
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    (corner, capState) -> TaperedFrameGeometryGenerator.generateSkylightCornerFragment(
                            corner, (capState & 2) != 0, (capState & 1) != 0, CornerTaperProfile.STANDARD),
                    taperedEdgeFragment(CornerTaperProfile.STANDARD),
                    edgeGlassFillFragment(CornerTaperProfile.STANDARD),
                    (corner, capState) -> TaperedGlassGeometryGenerator.generateSkylightGlassFillFragment(
                            corner, (capState & 2) != 0, (capState & 1) != 0, CornerTaperProfile.STANDARD)),
            new Strategy("arch",
                    ArchFrameGeometryGenerator::generate,
                    ArchGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.uniform(2)),
                    null,
                    ArchFrameGeometryGenerator::generateEdgeFragment,
                    ArchGlassGeometryGenerator::generateEdgeGlassFillFragment),
            new Strategy("mullion",
                    MullionFrameGeometryGenerator::generate,
                    MullionGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    null,
                    MullionFrameGeometryGenerator::generateEdgeFragment,
                    MullionGlassGeometryGenerator::generateEdgeGlassFillFragment),
            new Strategy("lattice",
                    LatticeFrameGeometryGenerator::generate,
                    LatticeGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.uniform(2)),
                    null,
                    LatticeFrameGeometryGenerator::generateEdgeFragment,
                    LatticeGlassGeometryGenerator::generateEdgeGlassFillFragment),
            new Strategy("dune",
                    perm -> TaperedFrameGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    perm -> TaperedGlassGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    DuneSandGeometryGenerator::generate,
                    taperedCornerFragment(CornerTaperProfile.STANDARD), taperedEdgeFragment(CornerTaperProfile.STANDARD),
                    edgeGlassFillFragment(CornerTaperProfile.STANDARD)),
            new Strategy("creeper",
                    CreeperFrameGeometryGenerator::generate,
                    CreeperGlassGeometryGenerator::generate,
                    perm -> SandGeometryGenerator.generate(perm, CornerTaperProfile.STANDARD),
                    (corner, capState) -> CreeperFrameGeometryGenerator.generateCornerFragment(corner, (capState & 2) != 0, (capState & 1) != 0),
                    taperedEdgeFragment(CornerTaperProfile.STANDARD), edgeGlassFillFragment(CornerTaperProfile.STANDARD)),
            new Strategy("vitrine",
                    perm -> TaperedFrameGeometryGenerator.generateVitrine(perm, CornerTaperProfile.STANDARD),
                    perm -> TaperedGlassGeometryGenerator.generateVitrine(perm, CornerTaperProfile.STANDARD),
                    SandGeometryGenerator::generateNone,
                    (corner, capState) -> TaperedFrameGeometryGenerator.generateVitrineCornerFragment(
                            corner, (capState & 2) != 0, (capState & 1) != 0, CornerTaperProfile.STANDARD),
                    taperedEdgeFragment(CornerTaperProfile.STANDARD),
                    edgeGlassFillFragment(CornerTaperProfile.STANDARD),
                    (corner, capState) -> TaperedGlassGeometryGenerator.generateVitrineGlassFillFragment(
                            corner, (capState & 2) != 0, (capState & 1) != 0, CornerTaperProfile.STANDARD)),
            new Strategy("cupola",
                    perm -> ShellFrameGeometryGenerator.generateCupola(perm, CornerTaperProfile.STURDY),
                    perm -> TaperedGlassGeometryGenerator.generateCupola(perm, CornerTaperProfile.STURDY),
                    perm -> SteppedSandGeometryGenerator.generate(perm, CornerTaperProfile.STURDY),
                    (corner, capState) -> ShellFrameGeometryGenerator.generateCupolaCornerFragment(
                            corner, (capState & 2) != 0, (capState & 1) != 0, CornerTaperProfile.STURDY),
                    shellEdgeFragment(CornerTaperProfile.STURDY),
                    edgeGlassFillFragment(CornerTaperProfile.STURDY),
                    (corner, capState) -> TaperedGlassGeometryGenerator.generateCupolaGlassFillFragment(
                            corner, (capState & 2) != 0, (capState & 1) != 0, CornerTaperProfile.STURDY)),
            new Strategy("hutch",
                    perm -> ShellFrameGeometryGenerator.generateHutch(perm, CornerTaperProfile.STURDY),
                    perm -> TaperedGlassGeometryGenerator.generateHutch(perm, CornerTaperProfile.STURDY),
                    SandGeometryGenerator::generateNone,
                    (corner, capState) -> ShellFrameGeometryGenerator.generateHutchCornerFragment(
                            corner, (capState & 2) != 0, (capState & 1) != 0, CornerTaperProfile.STURDY),
                    shellEdgeFragment(CornerTaperProfile.STURDY),
                    edgeGlassFillFragment(CornerTaperProfile.STURDY),
                    (corner, capState) -> TaperedGlassGeometryGenerator.generateHutchGlassFillFragment(
                            corner, (capState & 2) != 0, (capState & 1) != 0, CornerTaperProfile.STURDY))
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
                perm -> SandGeometryGenerator.generate(perm, profile),
                taperedCornerFragment(profile), taperedEdgeFragment(profile), edgeGlassFillFragment(profile));
    }

    /** Stepped-octagon shape: chamfered-ring frame (for {@code 16} rows) + tapered glass + stepped sand. */
    private static Strategy stepped(String name, CornerTaperProfile profile) {
        return new Strategy(name,
                perm -> ShellFrameGeometryGenerator.generate(perm, profile),
                perm -> TaperedGlassGeometryGenerator.generate(perm, profile),
                perm -> SteppedSandGeometryGenerator.generate(perm, profile),
                shellCornerFragment(profile), shellEdgeFragment(profile), edgeGlassFillFragment(profile));
    }
}

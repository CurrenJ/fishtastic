package grill24.fishtastic.fishtank;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Every model file the client block state models ({@code FishTankBlockStateModel} on NeoForge,
 * {@code FishTankBlockStateModelFabric} on Fabric) request for a shape must exist in the checked-in
 * datagen output. The loaders derive that list purely from {@link FishTankShape}'s flags, while the
 * files come from {@code tools/tank-shape-gen}'s strategy table — two sources of truth that have
 * drifted before: lattice shipped with edge-beam fragments but no glass-fill fragments
 * (2026-09-17), so the client quietly resolved vanilla's missing model for them and composited a
 * full purple/black cube into any lattice tank whose edge-diagonal cell was filled.
 *
 * <p>A missing file here is only ever logged as a {@code Missing block model} warning at resource
 * load, never surfaced in-game — which is why this is a unit test rather than something left for
 * playtesting to catch.
 */
class FishTankShapeModelAssetsTest {

    private static final Path MODELS_DIR = Path.of("src/main/resources/assets/fishtastic/models/block");
    private static final int PERMUTATION_COUNT = 64;

    @TestFactory
    Stream<DynamicTest> everyModelTheLoaderRequestsExists() {
        List<DynamicTest> tests = new ArrayList<>();
        for (FishTankShape shape : FishTankShape.values()) {
            List<String> expected = expectedModelNames(shape);
            tests.add(dynamicTest(shape.getSerializedName() + " (" + expected.size() + " models)", () -> {
                Path shapeDir = MODELS_DIR.resolve(shape.modelPathPrefix());
                assertTrue(Files.isDirectory(shapeDir), "no model directory for shape " + shape + " at " + shapeDir);
                List<String> missing = new ArrayList<>();
                for (String name : expected) {
                    if (!Files.isRegularFile(shapeDir.resolve(name + ".json"))) missing.add(name);
                }
                assertTrue(missing.isEmpty(), shape + " is missing " + missing.size() + " model file(s) the client"
                        + " will request (check FishTankShape's fragment flags against"
                        + " TankShapeGeometryStrategies and rerun datagen): " + missing);
            }));
        }
        return tests.stream();
    }

    /** Mirrors the dependency list in both platforms' {@code FishTankBlockStateModel#resolveDependencies}. */
    private static List<String> expectedModelNames(FishTankShape shape) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < PERMUTATION_COUNT; i++) {
            names.add("fish_tank_frame_" + i);
            names.add("fish_tank_sand_" + i);
            names.add("fish_tank_glass_" + i);
        }
        if (shape.hasDiagonalCornerFragments()) {
            for (TankDiagonal diagonal : TankDiagonal.values()) {
                for (int capState = 0; capState < 4; capState++) {
                    names.add("fish_tank_frame_corner_" + cornerSuffix(diagonal) + "_" + capState);
                }
            }
        }
        if (shape.hasCornerGlassFillFragments()) {
            for (TankDiagonal diagonal : TankDiagonal.values()) {
                for (int capState = 0; capState < 4; capState++) {
                    names.add("fish_tank_glass_fill_corner_" + cornerSuffix(diagonal) + "_" + capState);
                }
            }
        }
        if (shape.hasEdgeDiagonalFragments()) {
            for (TankEdgeDiagonal edge : TankEdgeDiagonal.values()) {
                names.add("fish_tank_frame_edge_" + edgeSuffix(edge));
                for (TankDiagonal corner : edge.endDiagonals()) {
                    names.add("fish_tank_glass_fill_" + edgeSuffix(edge) + "_" + cornerSuffix(corner));
                }
            }
        }
        return names;
    }

    private static String cornerSuffix(TankDiagonal diagonal) {
        return switch (diagonal) {
            case NORTHWEST -> "nw";
            case NORTHEAST -> "ne";
            case SOUTHWEST -> "sw";
            case SOUTHEAST -> "se";
        };
    }

    private static String edgeSuffix(TankEdgeDiagonal edge) {
        return edge.horizontal().getSerializedName() + "_" + edge.vertical().getSerializedName();
    }
}

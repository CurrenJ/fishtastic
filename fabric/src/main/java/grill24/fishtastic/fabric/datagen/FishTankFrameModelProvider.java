package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonObject;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.shapegen.TankCorner;
import grill24.fishtastic.shapegen.TankEdge;
import grill24.fishtastic.shapegen.TankShapeGeometry;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Data generator for fish tank frame models — all 64 permutations, for every registered
 * {@link FishTankShape}.
 *
 * <p>Thin wrapper: the actual element-generation logic lives in {@code tools/tank-shape-gen}
 * (a plain-Java library shared with the live shape previewer), reached per shape via
 * {@link FishTankShapeGeometryStrategies} so both produce byte-identical geometry from the exact
 * same code — see docs/fish-tanks.md, Phase 1/2.
 *
 * Each permutation is identified by an index (0-63) where each bit represents whether a face is open:
 * - Bit 0: DOWN
 * - Bit 1: UP
 * - Bit 2: NORTH
 * - Bit 3: SOUTH
 * - Bit 4: WEST
 * - Bit 5: EAST
 */
public class FishTankFrameModelProvider implements DataProvider {
    private final PackOutput.PathProvider pathProvider;

    public FishTankFrameModelProvider(FabricPackOutput output) {
        this.pathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/block");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        for (FishTankShape shape : FishTankShape.values()) {
            FishTankShapeGeometryStrategies.Strategy strategy = FishTankShapeGeometryStrategies.forShape(shape);
            for (int i = 0; i < TankShapeGeometry.PERMUTATION_COUNT; i++) {
                JsonObject model = strategy.frame().apply(i);
                Path path = pathProvider.json(Fishtastic.id(shape.modelPathPrefix() + "/fish_tank_frame_" + i));
                futures.add(DataProvider.saveStable(cache, model, path));
            }

            // Diagonal-aware corner posts (docs/tank-shapes diagonal-corner-post project): small
            // standalone fragments composited onto the 64 base bakes above at render time — not
            // baked into the permutations themselves, so the 64 files above stay byte-identical.
            // Only shapes whose frame generator has a combined-face corner gate need these.
            if (strategy.cornerFragment() != null) {
                for (TankCorner corner : TankCorner.values()) {
                    for (int capState = 0; capState < 4; capState++) {
                        JsonObject fragment = strategy.cornerFragment().apply(corner, capState);
                        Path path = pathProvider.json(Fishtastic.id(shape.modelPathPrefix()
                                + "/fish_tank_frame_corner_" + corner.name().toLowerCase() + "_" + capState));
                        futures.add(DataProvider.saveStable(cache, fragment, path));
                    }
                }
            }

            // Edge-diagonal frame beams (edge-diagonal frame beam fix): small standalone
            // fragments composited onto the 64 base bakes above at render time, one per horizontal
            // × vertical face combination — not baked into the permutations themselves, so the 64
            // files above stay byte-identical. Only shapes whose frame generator supports it emit
            // these (see FishTankShape#hasEdgeDiagonalFragments).
            if (strategy.edgeFragment() != null) {
                for (TankEdge edge : TankEdge.values()) {
                    JsonObject fragment = strategy.edgeFragment().apply(edge);
                    Path path = pathProvider.json(Fishtastic.id(shape.modelPathPrefix()
                            + "/fish_tank_frame_edge_" + edge.name().toLowerCase()));
                    futures.add(DataProvider.saveStable(cache, fragment, path));
                }
            }
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Fish Tank Frame Models";
    }
}

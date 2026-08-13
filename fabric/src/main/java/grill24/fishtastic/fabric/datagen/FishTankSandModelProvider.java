package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonObject;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.fishtank.FishTankShape;
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
 * Data generator for fish tank sand models — all 64 permutations, for every registered
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
 *
 * Sand behavior:
 * - If DOWN is open (bit 0 = 1), no sand is rendered (empty model)
 * - Sand extends to edges in cardinal directions where those faces are open
 * - Base sand dimensions and insets scale with each shape's corner geometry
 */
public class FishTankSandModelProvider implements DataProvider {
    private final PackOutput.PathProvider pathProvider;

    public FishTankSandModelProvider(FabricPackOutput output) {
        this.pathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/block");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        for (FishTankShape shape : FishTankShape.values()) {
            FishTankShapeGeometryStrategies.Strategy strategy = FishTankShapeGeometryStrategies.forShape(shape);
            for (int i = 0; i < TankShapeGeometry.PERMUTATION_COUNT; i++) {
                JsonObject model = strategy.sand().apply(i);
                Path path = pathProvider.json(Fishtastic.id(shape.modelPathPrefix() + "/fish_tank_sand_" + i));
                futures.add(DataProvider.saveStable(cache, model, path));
            }
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Fish Tank Sand Models";
    }
}

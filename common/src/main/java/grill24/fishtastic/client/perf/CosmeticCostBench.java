package grill24.fishtastic.client.perf;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.SpanStructures;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dev-only measurements of what a cosmetic structure costs to mesh, for the {@code bakebench}
 * self-test scene (docs/cosmetic-render-cost.md): the pieces and quads it bakes to, by chunk
 * layer, and the time to bake them with the cache cold.
 *
 * <p>PORT-ONLY: 1.21.1 has no {@code ChunkSectionLayer} and its quads carry no layer, so a quad is
 * counted under its piece's chunk {@link RenderType} ({@link ItemBlockRenderTypes#getChunkRenderType}),
 * the layer the chunk mesh would put it in; the layers are {@link RenderType#chunkBufferLayers()}.
 */
public final class CosmeticCostBench {

    /** The snapshots a structure meshes to, as the game builds them: one per tank of a span, one for a floor piece. */
    public static List<TankCosmeticMesh.Snapshot> snapshots(CosmeticStructure structure) {
        List<TankCosmeticMesh.Snapshot> out = new ArrayList<>();
        if (structure.span().isPresent()) {
            CosmeticStructure.Span span = structure.span().get();
            float s = structure.scale();
            for (int x = 0; x < span.x(); x++) {
                for (int y = 0; y < span.y(); y++) {
                    for (int z = 0; z < span.z(); z++) {
                        List<TankCosmeticMesh.Piece> pieces = new ArrayList<>();
                        for (SpanStructures.Placed part : SpanStructures.partsIn(structure, Rotation.NONE, new BlockPos(x, y, z))) {
                            if (TankCosmeticMesh.rendersDynamically(part.state())) continue;
                            pieces.add(new TankCosmeticMesh.Piece(part.state(),
                                    new Matrix4f().translate(part.x(), part.y(), part.z()).scale(s), part.cullMask()));
                        }
                        if (!pieces.isEmpty()) out.add(new TankCosmeticMesh.Snapshot(List.copyOf(pieces)));
                    }
                }
            }
            return out;
        }
        // A floor structure, anchored in the middle cell, as TankCosmeticMesh.addStructures places it.
        CosmeticGridCell anchor = new CosmeticGridCell(1, 1);
        float scale = structure.scale();
        List<TankCosmeticMesh.Piece> pieces = new ArrayList<>();
        for (CosmeticStructure.StructurePart part : structure.parts()) {
            if (TankCosmeticMesh.rendersDynamically(part.state())) continue;
            float[] r = CosmeticStructures.rotateOffset(Rotation.NONE, part.offsetX(), part.offsetZ());
            Matrix4f pose = new Matrix4f()
                    .translate((float) (anchor.localX() + r[0] * CosmeticGridCell.CELL_WIDTH),
                            CosmeticGridCell.FLOOR_Y + part.offsetY() * scale,
                            (float) (anchor.localZ() + r[1] * CosmeticGridCell.CELL_WIDTH))
                    .scale(scale).translate(-0.5f, 0f, -0.5f);
            pieces.add(new TankCosmeticMesh.Piece(part.state(), pose, 0));
        }
        if (!pieces.isEmpty()) out.add(new TankCosmeticMesh.Snapshot(List.copyOf(pieces)));
        return out;
    }

    /** Pieces, quads, tinted quads, and quads per chunk layer across the snapshots' bakes. Bakes through the cache. */
    public static Map<String, Integer> quadStats(List<TankCosmeticMesh.Snapshot> snapshots) {
        Map<RenderType, Integer> layers = new HashMap<>();
        int pieces = 0, tinted = 0, total = 0;
        for (TankCosmeticMesh.Snapshot snap : snapshots) {
            pieces += snap.pieces().size();
            TankCosmeticMesh.Baked baked = TankCosmeticMesh.bake(snap);
            for (TankCosmeticMesh.Quad q : baked.untinted()) layers.merge(ItemBlockRenderTypes.getChunkRenderType(q.tintState()), 1, Integer::sum);
            for (TankCosmeticMesh.Quad q : baked.tinted()) layers.merge(ItemBlockRenderTypes.getChunkRenderType(q.tintState()), 1, Integer::sum);
            tinted += baked.tinted().size();
            total += baked.untinted().size() + baked.tinted().size();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("pieces", pieces);
        out.put("quads", total);
        out.put("tinted", tinted);
        for (RenderType layer : RenderType.chunkBufferLayers()) out.put(layerName(layer), layers.getOrDefault(layer, 0));
        return out;
    }

    /** Wall time of baking all the snapshots with the cache cleared first, {@code reps} times, sorted (ns). */
    public static long[] timeBakes(List<TankCosmeticMesh.Snapshot> snapshots, int reps) {
        long[] t = new long[reps];
        for (int r = 0; r < reps; r++) {
            TankCosmeticMesh.clearCache();
            long start = System.nanoTime();
            for (TankCosmeticMesh.Snapshot snap : snapshots) TankCosmeticMesh.bake(snap);
            t[r] = System.nanoTime() - start;
        }
        Arrays.sort(t);
        return t;
    }

    /** One {@code [bakebench]} line per structure: its geometry and cold-cache bake time. */
    public static void run(List<String> names, java.util.function.Function<String, CosmeticStructure> lookup, int reps) {
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            Fishtastic.LOGGER.info("[bakebench] LAYER {} vertex_bytes={}", layerName(layer), layer.format().getVertexSize());
        }
        // Warm the JIT on the bake path before anything is timed.
        for (int w = 0; w < 3; w++) {
            for (String name : names) {
                CosmeticStructure s = lookup.apply(name);
                if (s != null) timeBakes(snapshots(s), 1);
            }
        }
        for (String name : names) {
            CosmeticStructure s = lookup.apply(name);
            if (s == null) continue;
            List<TankCosmeticMesh.Snapshot> snapshots = snapshots(s);
            long[] t = timeBakes(snapshots, reps);
            StringBuilder stats = new StringBuilder();
            for (Map.Entry<String, Integer> e : quadStats(snapshots).entrySet()) stats.append(e.getKey()).append('=').append(e.getValue()).append(' ');
            Fishtastic.LOGGER.info(String.format(Locale.ROOT,
                    "[bakebench] STRUCT name=%s kind=%s parts=%d tanks=%d %sbake_ms_p10=%.3f bake_ms_p50=%.3f bake_ms_p90=%.3f",
                    name, s.span().isPresent() ? "span" : "floor", s.parts().size(), snapshots.size(),
                    stats, t[reps / 10] / 1e6, t[reps / 2] / 1e6, t[reps * 9 / 10] / 1e6));
        }
        TankCosmeticMesh.clearCache();
    }

    /** A chunk layer's name as 26.1.2's {@code ChunkSectionLayer} would print it (solid, cutout_mipped, cutout, translucent, tripwire). */
    private static String layerName(RenderType layer) {
        if (layer == RenderType.solid()) return "solid";
        if (layer == RenderType.cutoutMipped()) return "cutout_mipped";
        if (layer == RenderType.cutout()) return "cutout";
        if (layer == RenderType.translucent()) return "translucent";
        if (layer == RenderType.tripwire()) return "tripwire";
        return layer.toString().toLowerCase(Locale.ROOT);
    }

    private CosmeticCostBench() {}
}

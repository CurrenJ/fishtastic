package grill24.fishtastic.client.compositemodel;

import grill24.FishtasticRegistries;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.perf.CosmeticBenchmark;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.HangingCosmetics;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.fishtank.SpanStructures;
import grill24.fishtastic.fishtank.TankColumns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tank's static cosmetics, baked into its chunk mesh rather than drawn every frame.
 *
 * <p>World blocks are cheap because the chunk mesher builds each section's geometry once, when
 * something in it changes, and the GPU then redraws that stored mesh every frame for free. Drawn by
 * the block entity renderer instead, every cosmetic block was re-submitted every frame — the model
 * looked up, its pose copied, every face sent again — so a 1,000-block set piece cost a thousand
 * submissions a frame. This class puts that geometry into the tank's own composite block model
 * (see docs/fish-tank-rendering.md), so it costs what the same blocks would cost in the world.
 *
 * <p>The flow, on both loaders:
 * <ol>
 *   <li>{@link #snapshot} — on the main thread, alongside the tank's other model data: every static
 *       cosmetic piece this tank draws, as a block state plus the transform from that block's model
 *       space into the tank's block space. Covers single floor cosmetics, kelp and hanging strands
 *       passing through this storey ({@link TankColumns}), structures, and this tank's share of a
 *       spanning structure ({@link SpanStructures}).</li>
 *   <li>{@link #bake} — on the meshing thread: each piece's block model quads, transformed, in the
 *       unculled bucket (so they take the tank's own light and are never culled against the world).
 *       Cached per snapshot, so a re-mesh that changes nothing about the cosmetics reuses them.
 *       Inside a spanning structure, a face against a neighbouring solid part is dropped, the way
 *       the chunk mesher drops faces between neighbouring world blocks.</li>
 *   <li>Tints are resolved per mesh with the cosmetic's own tint sources (a leaf's foliage colour,
 *       not the tank's) and written into vertex colours by the loader's model — see
 *       {@link #tintColor}.</li>
 * </ol>
 *
 * <p>1.21.1 port: there is no {@code QuadCollection} or quad material info, so {@link Baked} holds
 * plain quad lists and every {@link Quad} keeps its cosmetic's state, which each loader's model maps
 * to that block's own chunk layer (26.1.2 reads the layer off the quad's material, and ORs it into
 * the model's material flags). Quads are moved by rewriting the vertex data's positions.
 *
 * <p>What stays on the block entity renderer: chests (their lid animates, and their model is
 * special-rendered), the particles lit furnaces and campfires give off, the water fill, and fish.
 */
public final class TankCosmeticMesh {

    /** One static cosmetic block: its state, the pose from its model's [0,1]³ space into the tank block, and faces to drop (by {@link Direction#ordinal()}). */
    public record Piece(BlockState state, Matrix4f pose, int cullMask) {}

    /** Every static cosmetic piece one tank draws. Value-equal, so it doubles as the bake cache key. */
    public record Snapshot(List<Piece> pieces) {
        public static final Snapshot EMPTY = new Snapshot(List.of());

        public boolean isEmpty() {
            return pieces.isEmpty();
        }
    }

    /**
     * A transformed quad, with the cosmetic state whose tint sources colour it. PORT-ONLY: never
     * null here, since the state also picks the quad's chunk layer (see the class note).
     */
    public record Quad(BakedQuad quad, BlockState tintState) {}

    /** A snapshot's quads: the untinted ones as baked, and the tinted ones to colour per mesh. */
    public record Baked(List<Quad> untinted, List<Quad> tinted) {
        public static final Baked EMPTY = new Baked(List.of(), List.of());

        public boolean isEmpty() {
            return untinted.isEmpty() && tinted.isEmpty();
        }
    }

    private static final long MODEL_SEED = 42L;
    private static final int CACHE_LIMIT = 512;
    private static final Map<Snapshot, Baked> CACHE = new ConcurrentHashMap<>();

    private TankCosmeticMesh() {}

    // ── 1. Snapshot (main thread) ───────────────────────────────────────────────

    /** True for cosmetics that must stay on the block entity renderer: animated or special-rendered. */
    public static boolean rendersDynamically(BlockState state) {
        return state.getBlock() == Blocks.CHEST;
    }

    /** The snapshot the chunk mesh uses: empty unless the mesh path is active (see {@link CosmeticBenchmark}). */
    public static Snapshot snapshot(FishTankBlockEntity tank) {
        if (CosmeticBenchmark.mode != CosmeticBenchmark.Mode.MESH) return Snapshot.EMPTY;
        long start = System.nanoTime();
        Snapshot snapshot = compute(tank);
        CosmeticBenchmark.snapshotNanos.addAndGet(System.nanoTime() - start);
        CosmeticBenchmark.snapshotCount.incrementAndGet();
        return snapshot;
    }

    /** Every static cosmetic piece this tank draws, whichever path draws them. */
    public static Snapshot compute(FishTankBlockEntity tank) {
        Level level = tank.getLevel();
        if (level == null) return Snapshot.EMPTY;
        List<Piece> pieces = new ArrayList<>();
        addFloorCosmetics(tank, pieces);
        addColumnSegments(tank, level, pieces);
        addStructures(tank, level, pieces);
        addSpanShare(tank, level, pieces);
        return pieces.isEmpty() ? Snapshot.EMPTY : new Snapshot(List.copyOf(pieces));
    }

    private static Matrix4f cosmeticPose(double x, double y, double z, CosmeticTransforms.Transform t) {
        Matrix4f m = new Matrix4f().translate((float) x, (float) y, (float) z);
        if (t.rotX() != 0f) m.rotateX((float) Math.toRadians(t.rotX()));
        if (t.rotY() != 0f) m.rotateY((float) Math.toRadians(t.rotY()));
        if (t.rotZ() != 0f) m.rotateZ((float) Math.toRadians(t.rotZ()));
        return m.scale(t.scale()).translate(-0.5f, 0f, -0.5f);
    }

    /** Single-cell floor cosmetics; kelp goes with the column segments. */
    private static void addFloorCosmetics(FishTankBlockEntity tank, List<Piece> out) {
        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : tank.getCosmetics().entrySet()) {
            PlacedCosmetic cosmetic = entry.getValue();
            if (cosmetic.block() == Blocks.KELP || rendersDynamically(cosmetic.blockState())) continue;
            CosmeticGridCell cell = entry.getKey();
            CosmeticTransforms.Transform t = CosmeticTransforms.get(cosmetic.block());
            out.add(new Piece(cosmetic.blockState(),
                    cosmeticPose(cell.localX() + t.offsetX(), CosmeticGridCell.FLOOR_Y + t.offsetY(), cell.localZ() + t.offsetZ(), t), 0));
        }
    }

    /**
     * Kelp rooted in this tank or any below it, and hanging strands from this tank's lid or any
     * above it, whose segments start inside this tank. A segment crossing a seam belongs to the tank
     * its attached end is in (a kelp segment's bottom, a hanging one's top), so it's meshed once.
     */
    private static void addColumnSegments(FishTankBlockEntity self, Level level, List<Piece> out) {
        BlockPos pos = self.getBlockPos();

        FishTankBlockEntity tank = self;
        for (int depth = 0; ; depth++) {
            for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : tank.getCosmetics().entrySet()) {
                PlacedCosmetic kelp = entry.getValue();
                if (kelp.block() != Blocks.KELP) continue;
                CosmeticTransforms.Transform t = CosmeticTransforms.get(Blocks.KELP);
                CosmeticGridCell cell = entry.getKey();
                int height = Math.max(1, kelp.height());
                for (int seg = 0; seg < height; seg++) {
                    float baseY = CosmeticGridCell.FLOOR_Y + t.offsetY() + seg * t.scale() - depth;
                    if (baseY < 0f || baseY >= 1f) continue;
                    BlockState segState = seg < height - 1 ? Blocks.KELP_PLANT.defaultBlockState() : Blocks.KELP.defaultBlockState();
                    out.add(new Piece(segState, cosmeticPose(cell.localX() + t.offsetX(), baseY, cell.localZ() + t.offsetZ(), t), 0));
                }
            }
            if (!tank.isFaceOpen(Direction.DOWN) || depth >= MAX_COLUMN_WALK) break;
            if (!(level.getBlockEntity(pos.below(depth + 1)) instanceof FishTankBlockEntity below)
                    || !below.isFaceOpen(Direction.UP)) break;
            tank = below;
        }

        tank = self;
        for (int up = 0; ; up++) {
            for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : tank.getCeilingCosmetics().entrySet()) {
                PlacedCosmetic hanging = entry.getValue();
                CosmeticTransforms.Transform t = CosmeticTransforms.get(hanging.block());
                CosmeticGridCell cell = entry.getKey();
                List<BlockState> segments = HangingCosmetics.segments(hanging);
                for (int k = 0; k < segments.size(); k++) {
                    float topY = CosmeticGridCell.CEILING_Y + t.offsetY() - k * t.scale() + up;
                    if (topY <= 0f || topY > 1f) continue;
                    out.add(new Piece(segments.get(k), cosmeticPose(cell.localX() + t.offsetX(), topY - t.scale(), cell.localZ() + t.offsetZ(), t), 0));
                }
            }
            if (!tank.isFaceOpen(Direction.UP) || up >= MAX_COLUMN_WALK) break;
            if (!(level.getBlockEntity(pos.above(up + 1)) instanceof FishTankBlockEntity above)
                    || !above.isFaceOpen(Direction.DOWN)) break;
            tank = above;
        }
    }

    /** How many storeys a column strand is looked for across — far more than any strand can reach. */
    private static final int MAX_COLUMN_WALK = 16;

    /** Single-tank structures anchored here (spanning ones go through {@link #addSpanShare}). */
    private static void addStructures(FishTankBlockEntity tank, Level level, List<Piece> out) {
        if (tank.getStructureCosmetics().isEmpty()) return;
        var registry = level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
        for (Map.Entry<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> entry : tank.getStructureCosmetics().entrySet()) {
            CosmeticStructure structure = registry.getOptional(entry.getValue().structureId()).orElse(null);
            if (structure == null || structure.span().isPresent()) continue;
            CosmeticGridCell anchor = entry.getKey();
            Rotation rotation = entry.getValue().rotation();
            float scale = structure.scale();
            for (CosmeticStructure.StructurePart part : structure.parts()) {
                BlockState partState = part.state().rotate(rotation);
                if (rendersDynamically(partState)) continue;
                float[] r = CosmeticStructures.rotateOffset(rotation, part.offsetX(), part.offsetZ());
                Matrix4f pose = new Matrix4f()
                        .translate((float) (anchor.localX() + r[0] * CosmeticGridCell.CELL_WIDTH),
                                CosmeticGridCell.FLOOR_Y + part.offsetY() * scale,
                                (float) (anchor.localZ() + r[1] * CosmeticGridCell.CELL_WIDTH))
                        .scale(scale).translate(-0.5f, 0f, -0.5f);
                out.add(new Piece(partState, pose, 0));
            }
        }
    }

    /** This tank's share of the spanning structure it belongs to, with faces against solid neighbours dropped. */
    private static void addSpanShare(FishTankBlockEntity tank, Level level, List<Piece> out) {
        if (tank.getSpanLink() == null && tank.getStructureCosmetics().isEmpty()) return;
        SpanStructures.Ref ref = SpanStructures.resolve(level, tank);
        if (ref == null) return;
        float s = ref.structure().scale();
        for (SpanStructures.Placed part : SpanStructures.partsIn(ref.structure(), ref.placed().rotation(), ref.offsetInBox())) {
            if (rendersDynamically(part.state())) continue;
            out.add(new Piece(part.state(), new Matrix4f().translate(part.x(), part.y(), part.z()).scale(s), part.cullMask()));
        }
    }

    // ── 2. Bake (meshing thread, cached) ────────────────────────────────────────

    public static Baked bake(Snapshot snapshot) {
        if (snapshot.isEmpty()) return Baked.EMPTY;
        Baked cached = CACHE.get(snapshot);
        if (cached != null) return cached;
        long start = System.nanoTime();
        Baked baked = bakeUncached(snapshot);
        CosmeticBenchmark.bakeNanos.addAndGet(System.nanoTime() - start);
        CosmeticBenchmark.bakeCount.incrementAndGet();
        CosmeticBenchmark.bakedPieces.addAndGet(snapshot.pieces().size());
        CosmeticBenchmark.bakedQuads.addAndGet(baked.untinted().size() + baked.tinted().size());
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(snapshot, baked);
        return baked;
    }

    /** Drops every cached bake — on resource reload, since quads hold sprites and model geometry. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Baked bakeUncached(Snapshot snapshot) {
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        List<Quad> untinted = new ArrayList<>();
        List<Quad> tinted = new ArrayList<>();
        Vector3f scratch = new Vector3f();
        for (Piece piece : snapshot.pieces()) {
            BakedModel model = blockRenderer.getBlockModel(piece.state());
            for (Direction direction : Direction.values()) {
                if ((piece.cullMask() & (1 << direction.ordinal())) != 0) continue;
                for (BakedQuad quad : model.getQuads(piece.state(), direction, RandomSource.create(MODEL_SEED))) {
                    add(piece, quad, untinted, tinted, scratch);
                }
            }
            for (BakedQuad quad : model.getQuads(piece.state(), null, RandomSource.create(MODEL_SEED))) {
                add(piece, quad, untinted, tinted, scratch);
            }
        }
        return new Baked(List.copyOf(untinted), List.copyOf(tinted));
    }

    /** Ints per vertex in a baked quad (DefaultVertexFormat.BLOCK: position x3, colour, uv x2, light, normal). */
    private static final int VERTEX_STRIDE = 8;

    private static void add(Piece piece, BakedQuad quad, List<Quad> untinted, List<Quad> tinted, Vector3f scratch) {
        Matrix4f pose = piece.pose();
        int[] vertices = quad.getVertices().clone();
        for (int v = 0; v < 4; v++) {
            int o = v * VERTEX_STRIDE;
            scratch.set(Float.intBitsToFloat(vertices[o]), Float.intBitsToFloat(vertices[o + 1]), Float.intBitsToFloat(vertices[o + 2]));
            pose.transformPosition(scratch);
            vertices[o] = Float.floatToRawIntBits(scratch.x);
            vertices[o + 1] = Float.floatToRawIntBits(scratch.y);
            vertices[o + 2] = Float.floatToRawIntBits(scratch.z);
            // The packed normal no longer matches the rotated face; let the renderer derive it.
            vertices[o + 7] = 0;
        }
        // The face's direction (diffuse shading, and Fabric's nominal face) follows the pose's rotation.
        scratch.set(quad.getDirection().getStepX(), quad.getDirection().getStepY(), quad.getDirection().getStepZ());
        pose.transformDirection(scratch);
        Direction direction = Direction.getNearest(scratch.x, scratch.y, scratch.z);
        BakedQuad moved = new BakedQuad(vertices, quad.getTintIndex(), direction, quad.getSprite(), quad.isShade());
        (quad.isTinted() ? tinted : untinted).add(new Quad(moved, piece.state()));
    }

    // ── 3. Tint (per mesh) ──────────────────────────────────────────────────────

    /** The ARGB colour a tinted cosmetic quad takes at this tank's position, from the cosmetic's own tint sources. */
    public static int tintColor(Quad quad, BlockAndTintGetter level, BlockPos pos) {
        int rgb = Minecraft.getInstance().getBlockColors().getColor(quad.tintState(), level, pos, quad.quad().getTintIndex());
        if (rgb == -1) return -1;
        // Opaque: a source returning bare RGB would otherwise zero the vertex alpha.
        return 0xFF000000 | rgb;
    }

    /**
     * PORT-ONLY: {@code quad} re-made with {@code argb} baked into its vertex colours and its tint
     * index cleared, so the chunk mesher (which tints with the tank's own block colours) leaves it
     * alone. NeoForge's model uses this where 26.1.2 re-makes the quad with {@code BakedColors}.
     */
    public static BakedQuad withBakedColor(BakedQuad quad, int argb) {
        int[] vertices = quad.getVertices().clone();
        if (argb != -1) {
            // Vertex colour is stored ABGR (little-endian RGBA bytes).
            int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
            for (int v = 0; v < 4; v++) vertices[v * VERTEX_STRIDE + 3] = abgr;
        }
        return new BakedQuad(vertices, -1, quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    // ── Re-meshing the tanks a change reaches ───────────────────────────────────

    /**
     * Asks every tank whose mesh draws cosmetics stored on {@code tank} to re-mesh: kelp rooted here
     * shows in the storeys above, hanging strands in the storeys below, and a spanning structure in
     * its whole box. Called on the client whenever the tank's data arrives.
     */
    public static void refreshDependents(FishTankBlockEntity tank) {
        Level level = tank.getLevel();
        if (level == null || !level.isClientSide()) return;
        BlockPos pos = tank.getBlockPos();
        for (int up = 1; up <= MAX_COLUMN_WALK && tank.isFaceOpen(Direction.UP); up++) {
            if (!(level.getBlockEntity(pos.above(up)) instanceof FishTankBlockEntity above)) break;
            remesh(above);
            if (!above.isFaceOpen(Direction.UP)) break;
        }
        for (int down = 1; down <= MAX_COLUMN_WALK && tank.isFaceOpen(Direction.DOWN); down++) {
            if (!(level.getBlockEntity(pos.below(down)) instanceof FishTankBlockEntity below)) break;
            remesh(below);
            if (!below.isFaceOpen(Direction.DOWN)) break;
        }
        SpanStructures.Ref ref = SpanStructures.anchoredIn(level, tank);
        if (ref != null) {
            CosmeticStructure.Span box = ref.box();
            for (BlockPos p : BlockPos.betweenClosed(pos, pos.offset(box.x() - 1, box.y() - 1, box.z() - 1))) {
                if (!p.equals(pos) && level.getBlockEntity(p) instanceof FishTankBlockEntity member) {
                    remesh(member);
                }
            }
        }
    }

    /** Refreshes a tank's model data (NeoForge caches it) and marks its section for re-meshing. */
    private static void remesh(FishTankBlockEntity tank) {
        RegistrationApiSided.getInstance().requestModelDataUpdate(tank);
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            BlockPos p = tank.getBlockPos();
            mc.levelRenderer.setBlocksDirty(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
        }
    }
}

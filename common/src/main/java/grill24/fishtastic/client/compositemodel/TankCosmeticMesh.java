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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>What stays on the block entity renderer: chests (their lid animates, and their model is
 * special-rendered), the particles lit furnaces and campfires give off, the water fill, and fish.
 *
 * <p>1.20.1 port: a {@link BakedQuad} is a packed {@code int[]} in {@link DefaultVertexFormat#BLOCK}
 * rather than 26.1's position/UV records, so {@link #add} rewrites the packed positions and normals,
 * and there are no per-quad material flags: each quad carries the chunk layer its own block renders
 * in ({@link ItemBlockRenderTypes#getChunkRenderType}), which the loaders' models route it to.
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
     * A transformed quad, with the cosmetic state whose tint sources colour it (null when untinted)
     * and the chunk layer its block renders in (26.1 keeps that layer on the quad's material).
     */
    public record Quad(BakedQuad quad, BlockState tintState, RenderType layer) {}

    /**
     * A snapshot's quads: the untinted ones ready to emit, and the tinted ones to colour per mesh,
     * plus every chunk layer they use (26.1's {@code materialFlags}).
     */
    public record Baked(List<Quad> untinted, List<Quad> tinted, Set<RenderType> layers) {
        public static final Baked EMPTY = new Baked(List.of(), List.of(), Set.of());
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
        var models = Minecraft.getInstance().getBlockRenderer();
        List<Quad> untinted = new ArrayList<>();
        List<Quad> tinted = new ArrayList<>();
        Set<RenderType> layers = new LinkedHashSet<>();
        for (Piece piece : snapshot.pieces()) {
            BakedModel model = models.getBlockModel(piece.state());
            RenderType layer = ItemBlockRenderTypes.getChunkRenderType(piece.state());
            for (Direction direction : Direction.values()) {
                if ((piece.cullMask() & (1 << direction.ordinal())) != 0) continue;
                for (BakedQuad quad : model.getQuads(piece.state(), direction, RandomSource.create(MODEL_SEED))) {
                    add(piece, layer, quad, untinted, tinted, layers);
                }
            }
            for (BakedQuad quad : model.getQuads(piece.state(), null, RandomSource.create(MODEL_SEED))) {
                add(piece, layer, quad, untinted, tinted, layers);
            }
        }
        return new Baked(List.copyOf(untinted), List.copyOf(tinted), Collections.unmodifiableSet(layers));
    }

    /** Ints per vertex in a block quad, and where the colour and normal sit in each. */
    private static final int STRIDE = DefaultVertexFormat.BLOCK.getIntegerSize();
    private static final int COLOR_OFFSET = 3;
    private static final int NORMAL_OFFSET = 7;

    private static void add(Piece piece, RenderType layer, BakedQuad quad, List<Quad> untinted, List<Quad> tinted, Set<RenderType> layers) {
        Matrix4f pose = piece.pose();
        Matrix3f normalPose = new Matrix3f(pose).invert().transpose();
        int[] vertices = quad.getVertices().clone();
        Vector3f v = new Vector3f();
        for (int i = 0; i < 4; i++) {
            int base = i * STRIDE;
            v.set(Float.intBitsToFloat(vertices[base]), Float.intBitsToFloat(vertices[base + 1]), Float.intBitsToFloat(vertices[base + 2]));
            pose.transformPosition(v);
            vertices[base] = Float.floatToRawIntBits(v.x);
            vertices[base + 1] = Float.floatToRawIntBits(v.y);
            vertices[base + 2] = Float.floatToRawIntBits(v.z);
            int packed = vertices[base + NORMAL_OFFSET];
            if ((packed & 0xFFFFFF) != 0) {
                v.set((byte) packed / 127f, (byte) (packed >> 8) / 127f, (byte) (packed >> 16) / 127f);
                normalPose.transform(v).normalize();
                vertices[base + NORMAL_OFFSET] = (packed & 0xFF000000)
                        | ((int) (v.z * 127f) & 0xFF) << 16 | ((int) (v.y * 127f) & 0xFF) << 8 | ((int) (v.x * 127f) & 0xFF);
            }
        }
        // The face's direction (diffuse shading, and Fabric's nominal face) follows the pose's rotation.
        v.set(quad.getDirection().getStepX(), quad.getDirection().getStepY(), quad.getDirection().getStepZ());
        normalPose.transform(v);
        Direction direction = Direction.getNearest(v.x, v.y, v.z);
        BakedQuad moved = new BakedQuad(vertices, quad.getTintIndex(), direction, quad.getSprite(), quad.isShade());
        layers.add(layer);
        if (quad.isTinted()) {
            tinted.add(new Quad(moved, piece.state(), layer));
        } else {
            untinted.add(new Quad(moved, null, layer));
        }
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
     * PORT-ONLY: {@code quad} with {@code argb} multiplied into its packed vertex colours and its
     * tint index cleared, so the chunk mesher keeps that colour instead of asking the tank's own
     * block colours (1.20.1's terrain path multiplies vertex colours in). 26.1's NeoForge model does
     * the same with {@code BakedColors}; Fabric sets the emitter's colours instead.
     */
    public static BakedQuad withColor(BakedQuad quad, int argb) {
        int[] vertices = quad.getVertices().clone();
        if (argb != -1) {
            float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
            for (int i = 0; i < 4; i++) {
                int base = i * STRIDE + COLOR_OFFSET;
                int abgr = vertices[base];
                int vr = (int) ((abgr & 0xFF) * r), vg = (int) (((abgr >> 8) & 0xFF) * g), vb = (int) (((abgr >> 16) & 0xFF) * b);
                vertices[base] = (abgr & 0xFF000000) | vb << 16 | vg << 8 | vr;
            }
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

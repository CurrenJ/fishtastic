package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.util.ClientTankFlocks;
import grill24.fishtastic.client.util.FishermanPoseDebug;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.fishtank.SpanStructures;
import grill24.fishtastic.fishtank.TankColumns;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.util.ItemSizeHelper;
import grill24.fishtastic.util.Ids;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.phys.Vec2;

import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FishTankBlockEntityRenderer
        implements BlockEntityRenderer<FishTankBlockEntity, FishTankRenderState> {

    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;
    private final ChestModel chestModel;
    private final SpriteGetter chestSprites;

    private static final SpriteId CHEST_SPRITE = Sheets.chooseSprite(ChestRenderState.ChestMaterialType.REGULAR, ChestType.SINGLE);

    public FishTankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModelResolver = context.blockModelResolver();
        this.chestModel = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
        this.chestSprites = context.sprites();
    }

    // Reused across frames — the old code allocated a fresh Random per fish per frame (see Task 1
    // of docs/fish-simulation-handoff.md). The Random is safe to share because FishAnimator consumes
    // it synchronously; the ItemStackRenderState is NOT (its submit defers to the end of the frame,
    // so a shared one would render every fish as the last fish), so those live per-fish on the flock.
    private final Random fishRandom = new Random();

    private static final Vector3f SAND_BASE_Y_OFFSET =
        new Vector3f(0f, CosmeticGridCell.SAND_LAYER_HEIGHT * 0.5f, 0f);
    /**
     * Local-space Y every free-swimming fish is positioned from, and so the origin of the
     * simulation's own vertical axis — see {@code TankFloors.LOCAL_SURFACE_Y}, which measures the
     * sand relative to it.
     */
    public static final float ITEM_BASELINE_Y = 8f / 16f;
    private static final Vector3f ITEM_POSITION_OFFSET = new Vector3f(0.5f, ITEM_BASELINE_Y, 0.5f);
    public static final float COSMETIC_FLOOR_Y = CosmeticGridCell.FLOOR_Y;
    // Underside of the tank's glass ceiling, in local block-space Y — where rising bubbles pop.
    static final float TANK_CEILING_Y = 15f / 16f;

    // ── Water fill behind the glass ──────────────────────────────────────────────
    // Flat quads on every closed side wall, textured with vanilla's animated still-water sprite,
    // sitting just inside that wall's interior glass surface. The along-wall trim of each quad
    // mirrors the per-row corner-post widths that tools/tank-shape-gen's CornerTaperProfile /
    // TaperedGlassGeometryGenerator use to carve that shape's actual glass panes (ported below
    // rather than depended on, since that module otherwise has no runtime presence in this mod's
    // build graph), so tapered shapes get one water quad per taper run instead of a single
    // STANDARD-sized box. Shapes with decorative frame overlays instead of a tapered corner post
    // (ORNATE/SHAGGY/BRAMBLE/TOOTH/FILM) fall back to STANDARD's uniform 1px profile — their
    // corner posts stay 1px, the decoration sits on top of the glass rather than reshaping it.
    // SKYLIGHT additionally gets a horizontal quad under its roof pane. See docs/fish-tanks.md for
    // the real geometry axis.
    private static final SpriteId WATER_STILL_SPRITE =
            new SpriteId(TextureAtlas.LOCATION_BLOCKS, Ids.of("minecraft", "block/water_still"));
    // Built from FishtasticRenderPipelines.TANK_WATER_FILL (depth write disabled) rather than
    // RenderTypes.entityTranslucent — see that pipeline's doc for why depth write breaks this.
    private static final RenderType WATER_FILL_RENDER_TYPE = RenderType.create(
            "fishtastic_tank_water_fill",
            RenderSetup.builder(FishtasticRenderPipelines.TANK_WATER_FILL)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useLightmap()
                    .useOverlay()
                    .createRenderSetup());
    // Exact interior bounds of the glass panes in fish_tank_glass_0.json (elements span 1-15 on
    // every axis but depth): matching these precisely, rather than the floor grid's WALL_THICKNESS
    // convention (which starts 1px higher), is what was leaving a sliver of bare frame visible.
    private static final float WATER_FILL_MIN = 1f / 16f;
    private static final float WATER_FILL_MAX = 15f / 16f;
    // How far inside each wall's interior glass surface the quad sits. Kept tight: recessing it
    // further reads fine head-on but lets the corner posts' own depth eclipse the quad's edges
    // from any off-angle view, which is what looked like the fill "falling short" at the sides.
    private static final float WATER_FILL_RECESS = 0.05f / 16f;
    // water_still.png is a near-grayscale ripple pattern on its own — vanilla only reads as "blue
    // water" because LiquidBlockRenderer multiplies in the biome water tint. We have no biome here,
    // so bake in a fixed tint (Minecraft's default/plains water color, 0x3F76E4) instead of white.
    private static final int WATER_FILL_TINT_R = 0x3F;
    private static final int WATER_FILL_TINT_G = 0x76;
    private static final int WATER_FILL_TINT_B = 0xE4;
    private static final int WATER_FILL_ALPHA = 120;
    private static final Direction[] WATER_FILL_FACES =
            {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    // Per-row corner-post width (image rows 1-14, ceiling→floor) — duplicates the corresponding
    // CornerTaperProfile constants in tools/tank-shape-gen; see the water-fill header comment above.
    private static final int[] WATER_FILL_PROFILE_UNIFORM_1 = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final int[] WATER_FILL_PROFILE_STURDY = {16, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 16};
    private static final int[] WATER_FILL_PROFILE_TRIMMED = {3, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 3};
    private static final int[] WATER_FILL_PROFILE_REINFORCED = {5, 3, 2, 2, 1, 1, 1, 1, 1, 1, 2, 2, 3, 5};
    private static final int[] WATER_FILL_PROFILE_HONED = {6, 4, 3, 2, 2, 1, 1, 1, 1, 2, 2, 3, 4, 6};
    private static final int[] WATER_FILL_PROFILE_FACETED = {16, 4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 16};
    private static final int[] WATER_FILL_PROFILE_BASTION = {16, 6, 4, 3, 3, 2, 2, 2, 2, 3, 3, 4, 6, 16};
    private static final int[] WATER_FILL_PROFILE_RAMPART = {16, 7, 5, 4, 3, 3, 2, 2, 3, 3, 4, 5, 7, 16};

    /** Selects the corner-taper profile whose glass panes this shape's water fill should track. */
    private static int[] waterFillProfile(FishTankShape shape) {
        return switch (shape) {
            case STURDY -> WATER_FILL_PROFILE_STURDY;
            case TRIMMED -> WATER_FILL_PROFILE_TRIMMED;
            case REINFORCED -> WATER_FILL_PROFILE_REINFORCED;
            case HONED -> WATER_FILL_PROFILE_HONED;
            case FACETED -> WATER_FILL_PROFILE_FACETED;
            case BASTION -> WATER_FILL_PROFILE_BASTION;
            case RAMPART -> WATER_FILL_PROFILE_RAMPART;
            default -> WATER_FILL_PROFILE_UNIFORM_1; // STANDARD, SKYLIGHT, and the decorative-overlay shapes
        };
    }

    /** One contiguous run of equal corner-post width, in local block-space Y (0-1) rather than image rows. */
    private record WaterFillRun(float yFrom, float yTo, int width) {}

    // Every (shape, ceilingClosed, floorClosed) combination's run list, precomputed once at class
    // load rather than recomputed by computeWaterFillRuns() on every render call. There are only
    // FishTankShape.values().length * 4 possible combinations and they never change at runtime —
    // a tank's shape/connections only change on block update, not per-frame — so paying the
    // clone/ArrayList/record-boxing cost of computeWaterFillRuns() again for every visible tank on
    // every frame would be pure waste. Indexed by shape.ordinal() * 4 + (ceilingClosed<<1 | floorClosed).
    @SuppressWarnings("unchecked")
    private static final List<WaterFillRun>[] WATER_FILL_RUNS_CACHE = buildWaterFillRunsCache();

    @SuppressWarnings("unchecked")
    private static List<WaterFillRun>[] buildWaterFillRunsCache() {
        FishTankShape[] shapes = FishTankShape.values();
        List<WaterFillRun>[] cache = new List[shapes.length * 4];
        for (FishTankShape shape : shapes) {
            int[] profile = waterFillProfile(shape);
            for (int flags = 0; flags < 4; flags++) {
                boolean ceilingClosed = (flags & 0b10) != 0;
                boolean floorClosed = (flags & 0b01) != 0;
                cache[shape.ordinal() * 4 + flags] = List.copyOf(computeWaterFillRuns(profile, ceilingClosed, floorClosed));
            }
        }
        return cache;
    }

    /** Cached lookup — see {@link #WATER_FILL_RUNS_CACHE}. */
    private static List<WaterFillRun> waterFillRuns(FishTankShape shape, boolean ceilingClosed, boolean floorClosed) {
        int flags = (ceilingClosed ? 0b10 : 0) | (floorClosed ? 0b01 : 0);
        return WATER_FILL_RUNS_CACHE[shape.ordinal() * 4 + flags];
    }

    /**
     * Ports {@code CornerTaperProfile.runs()} (tools/tank-shape-gen) into local block-space Y:
     * merges consecutive equal-width rows into runs, then — when a cap is open — replaces the
     * leading/trailing run that differs from the profile's steady-state middle width with that
     * width and extends it to the block boundary, so a real tank-to-tank connection meets flush
     * instead of falling 1px short. Only ever called by {@link #buildWaterFillRunsCache()} — render
     * calls go through the cached {@link #waterFillRuns(FishTankShape, boolean, boolean)} instead.
     */
    private static List<WaterFillRun> computeWaterFillRuns(int[] rowWidths, boolean ceilingClosed, boolean floorClosed) {
        int[] widths = rowWidths.clone();
        int base = widths[widths.length / 2];
        if (!ceilingClosed) {
            for (int i = 0; i < widths.length && widths[i] != base; i++) widths[i] = base;
        }
        if (!floorClosed) {
            for (int i = widths.length - 1; i >= 0 && widths[i] != base; i--) widths[i] = base;
        }

        List<WaterFillRun> runs = new ArrayList<>();
        int runStartRow = 1; // image row (1-14) the current run started at
        int runWidth = widths[0];
        for (int i = 1; i <= widths.length; i++) {
            int imageRow = i + 1; // widths[i-1] corresponds to image row i, so the *next* row is i+1
            int width = i < widths.length ? widths[i] : Integer.MIN_VALUE; // sentinel to flush the last run
            if (width != runWidth) {
                int runEndRow = imageRow - 1; // last image row included in the run just ending
                runs.add(new WaterFillRun((15 - runEndRow) / 16f, (16 - runStartRow) / 16f, runWidth));
                runStartRow = imageRow;
                runWidth = width;
            }
        }

        if (!ceilingClosed && !runs.isEmpty()) {
            WaterFillRun top = runs.get(0);
            runs.set(0, new WaterFillRun(top.yFrom(), 1f, top.width()));
        }
        if (!floorClosed && !runs.isEmpty()) {
            WaterFillRun bottom = runs.get(runs.size() - 1);
            runs.set(runs.size() - 1, new WaterFillRun(0f, bottom.yTo(), bottom.width()));
        }
        return runs;
    }

    // ── Chest cosmetic hinge-open cycle (ticks) ─────────────────────────────────
    private static final int CHEST_OPEN_RAMP_TICKS = 10;
    private static final int CHEST_HOLD_OPEN_TICKS = 20;
    private static final int CHEST_CLOSE_RAMP_TICKS = 10;
    private static final int CHEST_IDLE_MIN_TICKS = 160;
    private static final int CHEST_IDLE_RANGE_TICKS = 150;
    // Bubble stream released while the lid opens: one bubble every few ticks, not all at once.
    private static final int CHEST_BUBBLE_STREAM_COUNT = 4;
    private static final int CHEST_BUBBLE_STREAM_INTERVAL_TICKS = 3;

    // Lit-furnace-family structure parts: smoke/flame spawn interval, throttled well below vanilla's
    // every-tick rate since these cosmetics render far smaller than a real furnace.
    private static final int FURNACE_PARTICLE_INTERVAL_TICKS = 10;

    // Lit-campfire single-cell cosmetic: smoke spawn interval, same reasoning as the furnace family.
    // Tighter than the furnace's 10-tick interval since the smoke is the cosmetic's whole visual hook.
    private static final int CAMPFIRE_SMOKE_INTERVAL_TICKS = 5;

    // ── BlockEntityRenderer ───────────────────────────────────────────────────

    @Override
    public FishTankRenderState createRenderState() {
        return new FishTankRenderState();
    }

    @Override
    public void extractRenderState(
            FishTankBlockEntity blockEntity,
            FishTankRenderState state,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPos, crumblingOverlay);

        Level level = blockEntity.getLevel();
        if (level == null) return;

        state.hasOpenDownFace = blockEntity.getOpenFaces().contains(Direction.DOWN);
        state.openFaces = blockEntity.getOpenFaces();
        state.shape = blockEntity.getShape();
        state.gameTimeTicks = level.getGameTime() + partialTick;
        state.cosmetics = new HashMap<>(blockEntity.getCosmetics());
        state.structureCosmetics = resolveStructureCosmetics(blockEntity, level);
        collectSpanParts(blockEntity, level, state);
        remeshOnTransformEdit();
        state.benchPieces = benchPiecesFor(blockEntity);

        int blockPosHash = blockEntity.getBlockPos().hashCode();
        state.blockPosHash = blockPosHash;

        if (level instanceof ClientLevel clientLevel) {
            spawnDueChestBubbles(clientLevel, blockEntity.getBlockPos(), blockPosHash, state);
            spawnDueFurnaceParticles(clientLevel, blockEntity, state);
            spawnDueCampfireSmoke(clientLevel, blockEntity, state);
        }

        // Attach (or create) this tank's flock and interpolate its fish to this frame's partial
        // tick. The flock persists in ClientTankFlocks across frames; extract only reads and
        // interpolates — it never advances simulation time (that happens in ClientTankFlocks.tickAll()).
        state.flock = ClientTankFlocks.getOrCreate(blockEntity, blockPosHash);
        state.flock.interpolate(partialTick);
    }

    @Override
    public void submit(
            FishTankRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector nodes,
            CameraRenderState camera) {

        renderCosmetics(state, poseStack, nodes);
        renderTankWaterFill(state, poseStack, nodes);

        TankFlockAdapter flock = state.flock;
        if (flock == null) return;

        float t = state.gameTimeTicks;
        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();

        submitGroupSwimmers(state, poseStack, nodes, flock, resolver, t);

        if (flock.count() == 0) return;
        FlockEngine eng = flock.engine();
        int n = flock.count();
        int[] order = eng.order;

        for (int k = 0; k < n; k++) {
            int i = order[k];
            poseStack.pushPose();

            float scale = eng.lengths[i];
            FishAnimationConfig anim = flock.anims[i];
            float baseY = computeBaseY(anim, state.hasOpenDownFace, scale);
            // Swarm's yRange jitter is an absolute world-space offset meant to spread swimmers
            // across a water column — it says nothing about an anchored creature's own size, so
            // applying it there sinks/floats them relative to the sand by a fixed amount that's
            // proportionally huge for a small instance and negligible for a large one (visible as
            // small crabs clipping into the sand). Planted creatures are pinned to COSMETIC_FLOOR_Y
            // by computeBaseY (correctly scaled), so they get none of it.
            //
            // Floor-dwellers are the exception: the engine places and (for a crawler) walks them,
            // so their Y *is* the engine's — the height of the sand under wherever they have got
            // to, which in a stacked group is not the same everywhere. computeBaseY leaves the
            // floor to them. Since Phase 4 that includes anchored creatures, whose Y the renderer
            // used to pin itself.
            float swarmYOffset = eng.renderY[i];
            poseStack.translate(
                    ITEM_POSITION_OFFSET.x() + eng.renderX[i],
                    baseY + swarmYOffset,
                    ITEM_POSITION_OFFSET.z() + eng.renderZ[i]);

            fishRandom.setSeed(eng.seeds[i]);
            // The item sprite's nose points along −lateral at rotation 0 (verified in-game:
            // the pre-fix `heading < 0` mapping rendered every swimmer facing backwards), so a
            // fish travelling +lateral is the one that needs the 180° mirror.
            boolean mirrored = eng.swimmers[i] ? eng.heading[i] > 0f : eng.hoverMirrored[i];
            if (eng.swimmers[i]) {
                // Simulated swimmers animate on the engine's speed-integrated clock, not game
                // time — that's what couples tail-beat frequency to swim speed without the
                // phase-teleport jitter of scaling the sine frequency per frame.
                FishAnimator.applySwimming(poseStack, (FishAnimationConfig.HorizontalSwim) anim, fishRandom,
                        eng.renderPhase[i], eng.baseRotations[i], mirrored, eng.speedFactor(i), eng.bank[i]);
            } else if (eng.locomotion[i] == Locomotion.BENTHIC) {
                // A crawler faces where it is walking. The +180° is the same mapping the group
                // swimmers use: the sprite's nose points along −lateral at rotation 0.
                FishAnimator.applyBenthic(poseStack, anim, fishRandom, t, eng.renderYaw[i] + 180f,
                        eng.baseRotations[i], scale, false, eng.renderShape[i]);
            } else if (eng.locomotion[i] == Locomotion.GLIDE) {
                FishAnimator.applyGliding(poseStack, anim, fishRandom, t, eng.renderYaw[i] + 180f,
                        eng.bankFraction(i), eng.baseRotations[i], scale, false);
            } else if (eng.locomotion[i] == Locomotion.DRIFT) {
                // The bell contracts on the engine's own pulse rather than on a clock of its own;
                // everything else about a drifter's pose is still game time.
                FishAnimator.applyDrifting(poseStack, anim, fishRandom, t, eng.baseRotations[i],
                        scale, mirrored, eng.renderShape[i]);
            } else if (eng.locomotion[i] == Locomotion.ANCHORED) {
                // Likewise for an eel's withdrawal: the sway is game time, the retract is the
                // engine's answer to what has just swum past the burrow.
                FishAnimator.applyAnchored(poseStack, anim, fishRandom, t, eng.baseRotations[i],
                        scale, mirrored, eng.renderShape[i]);
            } else {
                FishAnimator.apply(poseStack, anim, fishRandom, t, eng.baseRotations[i], scale, mirrored);
            }

            poseStack.scale(scale, scale, scale);

            ItemStackRenderState fishRender = flock.itemRenderStates[i];
            resolver.updateForTopItem(fishRender, flock.stacks[i], ItemDisplayContext.FIXED, null, null, 0);

            FishtasticWorldOutlineRenderer.capture(fishRender, flock.stacks[i]);
            FishtasticWorldOutlineRenderer.submitOutline(poseStack, nodes, fishRender, true);
            FishtasticGlintState.WORLD_OUTLINE_MAP.remove(fishRender);

            fishRender.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

            poseStack.popPose();
        }
    }

    /**
     * Draws the fish the group simulates inside <em>this</em> tank (multi-tank aquariums, see
     * {@link TankGroupFlock}). Positions come from the group's voxel-domain engine in group-local
     * coordinates (bounding-box center origin, lateral = world X, depth = world Z — no facing
     * rotation); {@link TankGroupFlock#localOffsetX} and friends rebase them onto this block.
     *
     * <p>Each member draws only the fish that are physically inside it, which is what keeps them on
     * screen: a block entity renderer runs only while its own chunk section is visible, so a fish
     * drawn by some other tank's block entity would disappear whenever that tank was out of view.
     * Drawing the fish where it is means vanilla's own section culling decides the two together —
     * and it gives each fish its own tank's light, and stops a single visible tank submitting every
     * fish in the build.
     *
     * <p>The one thing this gives up is a single global depth sort: the engine orders the whole
     * shoal back-to-front, but separate block entities submit in whatever order the level renderer
     * walks their sections, so fish in <em>different</em> tanks blend in submission order rather than
     * in depth order. That is how every other piece of tank geometry already works (the water fill,
     * the cosmetics) and how vanilla orders translucent block entities generally; the ordering that
     * matters — between fish sharing a tank, which are the ones that overlap — is preserved by
     * {@link TankGroupFlock#bucket(int)}.
     */
    private void submitGroupSwimmers(FishTankRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodes, TankFlockAdapter flock, ItemModelResolver resolver, float t) {
        TankGroupFlock group = flock.groupFlock();
        if (group == null) return;

        int memberIndex = group.memberIndexOf(state.blockPos);
        if (memberIndex < 0) return;
        int bucketSize = group.bucketSize(memberIndex);
        if (bucketSize == 0) return;

        FlockEngine eng = group.engine();
        int[] bucket = group.bucket(memberIndex);
        float groupOffsetX = group.localOffsetX(state.blockPos);
        float groupOffsetY = group.localOffsetY(state.blockPos);
        float groupOffsetZ = group.localOffsetZ(state.blockPos);

        for (int k = 0; k < bucketSize; k++) {
            int i = bucket[k];
            poseStack.pushPose();

            float scale = eng.lengths[i];
            FishAnimationConfig anim = group.anims[i];
            // The engine's Y is the sand surface for a crawler and the swim position for a
            // swimmer; only the former needs a pose lift off the floor, which floorPoseLift
            // returns 0 for everything else.
            poseStack.translate(
                    groupOffsetX + eng.renderX[i],
                    groupOffsetY + eng.renderY[i] + FishAnimator.floorPoseLift(anim, scale),
                    groupOffsetZ + eng.renderZ[i]);

            fishRandom.setSeed(eng.seeds[i]);
            // Planar model: continuous yaw, no mirror flag. The +180° maps the engine's
            // "faces +lateral at 0°" convention onto the item sprite, whose nose points along
            // −lateral at rotation 0 (same offset the single-tank mirror mapping encodes).
            if (eng.swimmers[i]) {
                FishAnimator.applySwimming(poseStack, (FishAnimationConfig.HorizontalSwim) anim, fishRandom,
                        eng.renderPhase[i], eng.renderYaw[i] + 180f, false, eng.speedFactor(i), eng.bank[i]);
            } else if (eng.locomotion[i] == Locomotion.BENTHIC) {
                FishAnimator.applyBenthic(poseStack, anim, fishRandom, t, eng.renderYaw[i] + 180f,
                        eng.baseRotations[i], scale, false, eng.renderShape[i]);
            } else if (eng.locomotion[i] == Locomotion.GLIDE) {
                FishAnimator.applyGliding(poseStack, anim, fishRandom, t, eng.renderYaw[i] + 180f,
                        eng.bankFraction(i), eng.baseRotations[i], scale, false);
            } else if (eng.locomotion[i] == Locomotion.DRIFT) {
                FishAnimator.applyDrifting(poseStack, anim, fishRandom, t, eng.baseRotations[i],
                        scale, eng.hoverMirrored[i], eng.renderShape[i]);
            } else if (eng.locomotion[i] == Locomotion.ANCHORED) {
                FishAnimator.applyAnchored(poseStack, anim, fishRandom, t, eng.baseRotations[i],
                        scale, eng.hoverMirrored[i], eng.renderShape[i]);
            } else {
                // Anything the group's engine demoted to STATIC: pose on game time exactly as the
                // single-tank path does. Branching on swimmers[] rather than falling through to
                // applySwimming is what keeps a jellyfish — an UprightFloat, not a
                // HorizontalSwim — from reaching that cast.
                FishAnimator.apply(poseStack, anim, fishRandom, t, eng.baseRotations[i], scale,
                        eng.hoverMirrored[i]);
            }

            poseStack.scale(scale, scale, scale);

            ItemStackRenderState fishRender = group.renderStates[i];
            resolver.updateForTopItem(fishRender, group.stacks[i], ItemDisplayContext.FIXED, null, null, 0);

            FishtasticWorldOutlineRenderer.capture(fishRender, group.stacks[i]);
            FishtasticWorldOutlineRenderer.submitOutline(poseStack, nodes, fishRender, true);
            FishtasticGlintState.WORLD_OUTLINE_MAP.remove(fishRender);

            fishRender.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

            poseStack.popPose();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Whether this animation mode pins the creature's Y to the tank floor (see {@link #computeBaseY}). */
    static boolean isFloorAnchored(FishAnimationConfig animConfig) {
        return animConfig instanceof FishAnimationConfig.FloorSit
                || animConfig instanceof FishAnimationConfig.Planted
                || animConfig instanceof FishAnimationConfig.UprightSit;
    }

    static float computeBaseY(FishAnimationConfig animConfig, boolean hasOpenDownFace, float scale) {
        return switch (animConfig) {
            // The engine walks crawlers and owns their vertical: it reports the height of the sand
            // under wherever the creature has got to (see the swarmYOffset note above), so all
            // that is left here is the baseline that offset is measured from, plus the pose's own
            // lift off the sand.
            case FishAnimationConfig.FloorSit    fs -> ITEM_BASELINE_Y + FishAnimator.floorPoseLift(fs, scale);
            // An anchored creature's Y is the engine's too, since Phase 4: its burrow is on the
            // group's sand, which in a stacked group is not all at one height. All that is left
            // here is the baseline, plus the pose's own plant depth and centre-pivot compensation.
            case FishAnimationConfig.Planted     p  -> ITEM_BASELINE_Y + FishAnimator.floorPoseLift(p, scale);
            case FishAnimationConfig.UprightSit  us -> ITEM_BASELINE_Y + FishAnimator.floorPoseLift(us, scale);
            default -> {
                float y = ITEM_POSITION_OFFSET.y();
                if (!hasOpenDownFace) y += SAND_BASE_Y_OFFSET.y();
                yield y;
            }
        };
    }

    /** Animation config + per-species render calibration, resolved together from one profile lookup. */
    record ResolvedFishRender(FishAnimationConfig animation, float renderCalibration) {
        private static final ResolvedFishRender DEFAULT = new ResolvedFishRender(
                FishAnimationConfig.HorizontalSwim.DEFAULT, FishProfile.DEFAULT_RENDER_CALIBRATION);
    }

    /**
     * Same size-to-scale formula the tank uses to draw fish true-to-scale
     * ({@code size/100 * per-species render_calibration}, see {@link ResolvedFishRender}) — for
     * anything else (held items, GUI previews) that wants a caught fish to render at its recorded
     * size. Returns {@code 1.0} (no scaling) for stacks with no recorded size.
     */
    public static float getHeldItemRenderScale(ItemStack stack, Level level) {
        if (!ItemSizeHelper.hasSize(stack)) {
            return 1.0f;
        }
        return (ItemSizeHelper.getSize(stack) / 100f) * resolveFishRender(stack, level).renderCalibration();
    }

    /**
     * Roll (degrees) to lay a held fish's sprite head-down, tail-up, for the fisherman hang pose.
     * Uses the exact drawn angle when a species has authored {@code head_uv}/{@code tail_uv}
     * points ({@code atan2} between them), falling back to the tank's diagonal-texture convention
     * otherwise (see {@link FishAnimationConfig}). Only meaningful for the swimming-shaped pose
     * modes; other modes (floor/planted/belly-sit species) return 0 — the pose doesn't suit them.
     */
    public static float getHeldItemHangingRollDegrees(ItemStack stack, Level level) {
        FishAnimationConfig animation = resolveFishRender(stack, level).animation();
        return switch (animation) {
            case FishAnimationConfig.HorizontalSwim hs -> hangingRoll(hs.headUv(), hs.tailUv(), hs.diagonalTexture());
            case FishAnimationConfig.UprightFloat uf -> hangingRoll(uf.headUv(), uf.tailUv(), uf.diagonalTexture());
            case FishAnimationConfig.UprightSit us -> hangingRoll(us.headUv(), us.tailUv(), us.diagonalTexture());
            default -> 0f;
        };
    }

    private static float hangingRoll(Optional<Vec2> head, Optional<Vec2> tail, boolean diagonalTexture) {
        if (head.isPresent() && tail.isPresent()) {
            Vec2 h = head.get();
            Vec2 t = tail.get();
            // Exact drawn angle, then rotated to point straight down instead of horizontal. Both
            // the "-90" target and the axis this feeds into need eyeballing once actually visible.
            float drawnAngleDeg = (float) Math.toDegrees(Math.atan2(h.y - t.y, h.x - t.x));
            return drawnAngleDeg - 90f;
        }
        // Live-tunable via /fishtastic pose — see FishermanPoseDebug.
        return diagonalTexture ? FishermanPoseDebug.rollDiagonalDegrees : FishermanPoseDebug.rollStraightDegrees;
    }

    static ResolvedFishRender resolveFishRender(ItemStack stack, Level level) {
        if (stack.isEmpty()) return ResolvedFishRender.DEFAULT;

        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) return ResolvedFishRender.DEFAULT;

        ResourceKey<FishProfile> profileKey = ResourceKey.create(
                FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier());

        return level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(profileKey)
                .map(profile -> new ResolvedFishRender(
                        profile.animation().orElse(FishAnimationConfig.HorizontalSwim.DEFAULT),
                        profile.renderCalibration()))
                .orElse(ResolvedFishRender.DEFAULT);
    }

    /** Resolves each placed structure's definition once here (render thread never does registry lookups). */
    private static Map<CosmeticGridCell, FishTankRenderState.ResolvedStructureCosmetic> resolveStructureCosmetics(
            FishTankBlockEntity blockEntity, Level level) {
        Map<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> placed = blockEntity.getStructureCosmetics();
        if (placed.isEmpty()) return java.util.Collections.emptyMap();

        var registry = level.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
        Map<CosmeticGridCell, FishTankRenderState.ResolvedStructureCosmetic> resolved = new HashMap<>();
        for (Map.Entry<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> entry : placed.entrySet()) {
            registry.getOptional(entry.getValue().structureId())
                    // Spanning structures draw through collectSpanParts, tank by tank, instead.
                    .filter(structure -> structure.span().isEmpty())
                    .ifPresent(structure ->
                    resolved.put(entry.getKey(), new FishTankRenderState.ResolvedStructureCosmetic(structure, entry.getValue().rotation())));
        }
        return resolved;
    }

    // ── Benchmark: the previous per-frame path (CosmeticBenchmark.Mode.PER_FRAME) ──────────────
    // Same pieces and poses the chunk mesh bakes, submitted block by block every frame, the way
    // static cosmetics were drawn before they moved into the mesh. Snapshots are cached per tank
    // for the run, so this reading leaves out the per-frame transform maths the old path also did:
    // if anything it flatters the old path.
    private final Map<BlockPos, List<TankCosmeticMesh.Piece>> benchPieceCache = new HashMap<>();

    private List<TankCosmeticMesh.Piece> benchPiecesFor(FishTankBlockEntity blockEntity) {
        if (grill24.fishtastic.client.perf.CosmeticBenchmark.mode != grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.PER_FRAME) {
            if (!benchPieceCache.isEmpty()) benchPieceCache.clear();
            return List.of();
        }
        return benchPieceCache.computeIfAbsent(blockEntity.getBlockPos().immutable(), p -> TankCosmeticMesh.compute(blockEntity).pieces());
    }

    private void renderBenchPieces(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
        if (state.benchPieces.isEmpty()) return;
        BlockModelRenderState blockModelState = new BlockModelRenderState();
        for (TankCosmeticMesh.Piece piece : state.benchPieces) {
            poseStack.pushPose();
            poseStack.mulPose(piece.pose());
            blockModelResolver.update(blockModelState, piece.state(), BLOCK_DISPLAY_CONTEXT);
            blockModelState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    private static int seenTransformVersion = CosmeticTransforms.version();

    /**
     * A dev command (/fishtastic cosmetic nudge/rotate/scale) edited a cosmetic transform: the
     * cosmetics baked into tank chunk meshes (TankCosmeticMesh) are stale, so re-mesh the world once.
     */
    private static void remeshOnTransformEdit() {
        int version = CosmeticTransforms.version();
        if (version == seenTransformVersion) return;
        seenTransformVersion = version;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.levelRenderer.allChanged());
    }

    /** Last span share computed per tank, reused while the span it came from is unchanged. */
    private record SpanShare(FishTankBlockEntity anchor, FishTankBlockEntity.PlacedStructureCosmetic placed,
                             CosmeticStructure structure, BlockPos offset, List<SpanStructures.Placed> parts) {}
    private final Map<BlockPos, SpanShare> spanShares = new HashMap<>();

    /**
     * This tank's share of the spanning structure it belongs to, if any: the parts whose centre
     * lies inside this block (see {@link SpanStructures}). Every tank in the box draws its own
     * share, for the same reason kelp segments and group fish are drawn where they are — a block
     * entity renders only while its own section is visible.
     */
    private void collectSpanParts(FishTankBlockEntity blockEntity, Level level, FishTankRenderState state) {
        state.spanParts = List.of();
        if (blockEntity.getSpanLink() == null && blockEntity.getStructureCosmetics().isEmpty()) {
            spanShares.remove(blockEntity.getBlockPos());
            return;
        }
        SpanStructures.Ref ref = SpanStructures.resolve(level, blockEntity);
        if (ref == null) {
            spanShares.remove(blockEntity.getBlockPos());
            return;
        }
        SpanShare share = spanShares.get(blockEntity.getBlockPos());
        if (share == null || share.anchor() != ref.anchor() || !share.placed().equals(ref.placed())
                || share.structure() != ref.structure() || !share.offset().equals(ref.offsetInBox())) {
            share = new SpanShare(ref.anchor(), ref.placed(), ref.structure(), ref.offsetInBox(),
                    SpanStructures.partsIn(ref.structure(), ref.placed().rotation(), ref.offsetInBox()).stream()
                            // Everything else in the span is in the chunk mesh (TankCosmeticMesh).
                            .filter(part -> TankCosmeticMesh.rendersDynamically(part.state()))
                            .toList());
            spanShares.put(blockEntity.getBlockPos().immutable(), share);
        }
        state.spanParts = share.parts();
        state.spanScale = ref.structure().scale();
    }

    private void renderSpanParts(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
        if (state.spanParts.isEmpty()) return;
        BlockModelRenderState blockModelState = new BlockModelRenderState();
        float s = state.spanScale;
        for (SpanStructures.Placed part : state.spanParts) {
            BlockState partState = part.state();
            poseStack.pushPose();
            poseStack.translate(part.x() + s / 2f, part.y(), part.z() + s / 2f);
            // Same chest special case as renderStructureCosmetics: its lid is posed from FACING.
            if (partState.getBlock() == Blocks.CHEST) {
                Direction facing = partState.getValue(net.minecraft.world.level.block.ChestBlock.FACING);
                poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            }
            poseStack.scale(s, s, s);
            poseStack.translate(-0.5f, 0f, -0.5f);
            if (partState.getBlock() == Blocks.CHEST) {
                nodes.submitModel(chestModel, 0f, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, CHEST_SPRITE, chestSprites, 0, null);
            } else {
                blockModelResolver.update(blockModelState, partState, BLOCK_DISPLAY_CONTEXT);
                blockModelState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            }
            poseStack.popPose();
        }
    }

    /**
     * Draws one flat quad per closed side wall (north/south/east/west — never up/down) per taper
     * run, textured with vanilla's animated still-water sprite, set just inside that wall's
     * interior glass surface. A face is skipped entirely when open to a connected neighbor tank,
     * since there's no glass there to sit behind; a full-width (16px) run is skipped too, since
     * that band is a solid cap ring rather than glass. Every other run's in-plane extent is pushed
     * out to the full block boundary on whichever edge borders an open connection (instead of
     * stopping at this tank's own interior corner), so two connected tanks' water reads as one
     * continuous surface rather than leaving a gap at the seam. SKYLIGHT additionally gets a
     * horizontal quad under its roof pane when the ceiling is closed.
     */
    private void renderTankWaterFill(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
        if (!FishtasticClientConfig.isTankWaterFillEnabled()) return;

        TextureAtlasSprite sprite = chestSprites.get(WATER_STILL_SPRITE);
        RenderType renderType = WATER_FILL_RENDER_TYPE;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int light = state.lightCoords;

        boolean westOpen = state.openFaces.contains(Direction.WEST);
        boolean eastOpen = state.openFaces.contains(Direction.EAST);
        boolean northOpen = state.openFaces.contains(Direction.NORTH);
        boolean southOpen = state.openFaces.contains(Direction.SOUTH);
        boolean ceilingClosed = !state.openFaces.contains(Direction.UP);
        boolean floorClosed = !state.openFaces.contains(Direction.DOWN);

        int[] profile = waterFillProfile(state.shape);
        List<WaterFillRun> runs = waterFillRuns(state.shape, ceilingClosed, floorClosed);
        boolean skylightTop = state.shape == FishTankShape.SKYLIGHT && ceilingClosed;
        int skylightInset = profile[profile.length - 1]; // floor-adjacent row width, matching the skylight pane's inset

        // ONE submission for the whole tank, not one per quad. submitCustomGeometry takes a
        // capturing lambda, so a per-quad call allocated one closure per run per face per frame —
        // up to 37 for a RAMPART tank, which at 20 visible tanks was ~43k allocations/sec of pure
        // young-gen churn. Emitting every face and run inside a single closure makes it one
        // allocation per tank per frame instead.
        nodes.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            for (Direction face : WATER_FILL_FACES) {
                if (state.openFaces.contains(face)) continue;

                // NORTH/SOUTH walls run along the X axis (their normal is on Z); EAST/WEST run along Z.
                boolean horizontalIsX = face.getAxis() == Direction.Axis.Z;
                boolean loOpen = horizontalIsX ? westOpen : northOpen;
                boolean hiOpen = horizontalIsX ? eastOpen : southOpen;

                for (WaterFillRun run : runs) {
                    if (run.width() >= 16) continue; // full-width run is a solid cap ring — never glass
                    float horizontalLo = loOpen ? 0f : run.width() / 16f;
                    float horizontalHi = hiOpen ? 1f : 1f - run.width() / 16f;
                    if (horizontalLo >= horizontalHi) continue; // degenerate — a full-width run leaves no pane

                    addWaterFillQuad(buffer, pose, face, horizontalLo, horizontalHi,
                            run.yFrom(), run.yTo(), u0, u1, v0, v1, light);
                }
            }

            if (skylightTop) {
                float xLo = westOpen ? 0f : skylightInset / 16f;
                float xHi = eastOpen ? 1f : 1f - skylightInset / 16f;
                float zLo = northOpen ? 0f : skylightInset / 16f;
                float zHi = southOpen ? 1f : 1f - skylightInset / 16f;
                addSkylightWaterFillQuad(buffer, pose, xLo, xHi, zLo, zHi, u0, u1, v0, v1, light);
            }
        });
    }

    /** Horizontal water quad just under SKYLIGHT's roof glass, matching that pane's footprint. */
    private static void addSkylightWaterFillQuad(VertexConsumer buffer, PoseStack.Pose pose,
            float xLo, float xHi, float zLo, float zHi, float u0, float u1, float v0, float v1, int light) {
        float y = WATER_FILL_MAX - WATER_FILL_RECESS; // just under the y=1 roof pane
        addWaterFillVertex(buffer, pose, xLo, y, zLo, u0, v0, 0f, -1f, 0f, light);
        addWaterFillVertex(buffer, pose, xLo, y, zHi, u0, v1, 0f, -1f, 0f, light);
        addWaterFillVertex(buffer, pose, xHi, y, zHi, u1, v1, 0f, -1f, 0f, light);
        addWaterFillVertex(buffer, pose, xHi, y, zLo, u1, v0, 0f, -1f, 0f, light);
    }

    /** Emits the interior-facing water quad for a single closed side wall. */
    private static void addWaterFillQuad(VertexConsumer buffer, PoseStack.Pose pose,
            Direction face, float horizontalLo, float horizontalHi, float verticalLo, float verticalHi,
            float u0, float u1, float v0, float v1, int light) {
        boolean positive = face.getStepX() > 0 || face.getStepZ() > 0;
        float depth = positive ? WATER_FILL_MAX - WATER_FILL_RECESS : WATER_FILL_MIN + WATER_FILL_RECESS;
        boolean horizontalIsX = face.getAxis() == Direction.Axis.Z;
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        if (horizontalIsX) {
            addWaterFillVertex(buffer, pose, horizontalLo, verticalHi, depth, u0, v0, nx, ny, nz, light);
            addWaterFillVertex(buffer, pose, horizontalLo, verticalLo, depth, u0, v1, nx, ny, nz, light);
            addWaterFillVertex(buffer, pose, horizontalHi, verticalLo, depth, u1, v1, nx, ny, nz, light);
            addWaterFillVertex(buffer, pose, horizontalHi, verticalHi, depth, u1, v0, nx, ny, nz, light);
        } else {
            addWaterFillVertex(buffer, pose, depth, verticalHi, horizontalLo, u0, v0, nx, ny, nz, light);
            addWaterFillVertex(buffer, pose, depth, verticalLo, horizontalLo, u0, v1, nx, ny, nz, light);
            addWaterFillVertex(buffer, pose, depth, verticalLo, horizontalHi, u1, v1, nx, ny, nz, light);
            addWaterFillVertex(buffer, pose, depth, verticalHi, horizontalHi, u1, v0, nx, ny, nz, light);
        }
    }

    private static void addWaterFillVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
            float u, float v, float nx, float ny, float nz, int light) {
        buffer.addVertex(pose, x, y, z)
                .setColor(WATER_FILL_TINT_R, WATER_FILL_TINT_G, WATER_FILL_TINT_B, WATER_FILL_ALPHA)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Draws the cosmetics that can't live in the chunk mesh — the ones whose look changes frame to
     * frame. Everything static (single cosmetics, kelp and hanging strands, structures, spanning
     * structures) is baked into the tank's chunk mesh instead, where it costs what the same blocks
     * would cost in the world (see {@link TankCosmeticMesh}). What's left is the chest, whose lid
     * opens and closes on a timer and whose model is special-rendered.
     */
    private void renderCosmetics(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
        renderStructureCosmetics(state, poseStack, nodes);
        renderSpanParts(state, poseStack, nodes);
        renderBenchPieces(state, poseStack, nodes);

        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : state.cosmetics.entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            PlacedCosmetic cosmetic = entry.getValue();
            if (cosmetic.block() != Blocks.CHEST) continue;
            CosmeticTransforms.Transform transform = CosmeticTransforms.get(cosmetic.block());

            poseStack.pushPose();
            poseStack.translate(cell.localX() + transform.offsetX(), COSMETIC_FLOOR_Y + transform.offsetY(), cell.localZ() + transform.offsetZ());
            if (transform.rotX() != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotX()));
            if (transform.rotY() != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(transform.rotY()));
            if (transform.rotZ() != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotZ()));
            // Facing before the scale/recenter: PoseStack composes in reverse call order, so a
            // rotation pushed after the -0.5,-0.5 recenter would pivot around the cell corner.
            Direction facing = cosmetic.blockState().getValue(net.minecraft.world.level.block.ChestBlock.FACING);
            poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            float s = transform.scale();
            poseStack.scale(s, s, s);
            poseStack.translate(-0.5f, 0f, -0.5f);
            float openness = chestOpenness(chestCycle(cellSeed(state.blockPosHash, cell)), state.gameTimeTicks);
            nodes.submitModel(chestModel, openness, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, CHEST_SPRITE, chestSprites, 0, null);
            poseStack.popPose();
        }
    }

    /**
     * Draws the chests in placed single-tank structures (the rest of each structure is in the chunk
     * mesh — see {@link TankCosmeticMesh}). Each part's offset is rotated with the structure and
     * converted from grid cells to block space via {@link CosmeticGridCell#CELL_WIDTH}; the chest's
     * {@code FACING} is read off the rotated state, so its lid turns with the rest of the structure.
     */
    private void renderStructureCosmetics(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
        for (Map.Entry<CosmeticGridCell, FishTankRenderState.ResolvedStructureCosmetic> entry : state.structureCosmetics.entrySet()) {
            CosmeticGridCell anchor = entry.getKey();
            CosmeticStructure structure = entry.getValue().structure();
            Rotation rotation = entry.getValue().rotation();
            float scale = structure.scale();
            for (CosmeticStructure.StructurePart part : structure.parts()) {
                BlockState partState = part.state().rotate(rotation);
                if (partState.getBlock() != Blocks.CHEST) continue;
                float[] rotatedXZ = CosmeticStructures.rotateOffset(rotation, part.offsetX(), part.offsetZ());
                poseStack.pushPose();
                poseStack.translate(anchor.localX() + rotatedXZ[0] * CosmeticGridCell.CELL_WIDTH,
                        COSMETIC_FLOOR_Y + part.offsetY() * scale,
                        anchor.localZ() + rotatedXZ[1] * CosmeticGridCell.CELL_WIDTH);
                Direction facing = partState.getValue(net.minecraft.world.level.block.ChestBlock.FACING);
                poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
                poseStack.scale(scale, scale, scale);
                poseStack.translate(-0.5f, 0f, -0.5f);
                nodes.submitModel(chestModel, 0f, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, CHEST_SPRITE, chestSprites, 0, null);
                poseStack.popPose();
            }
        }
    }

    // ── Chest hinge-open cycle + bubble particles ───────────────────────────────

    /** Deterministic per-cell seed, consistent with the swarm-fish seeding convention above. */
    private static long cellSeed(int blockPosHash, CosmeticGridCell cell) {
        return (long) blockPosHash ^ ((long) (cell.gridX() * CosmeticGridCell.GRID_SIZE + cell.gridZ() + 1) * 2654435761L);
    }

    /** Stable per-chest cycle timing: how long it idles closed, and a random phase so chests desync. */
    private record ChestCycle(long idleTicks, long totalTicks, long phase) {}

    private static ChestCycle chestCycle(long seed) {
        Random rnd = new Random(seed);
        long idleTicks = CHEST_IDLE_MIN_TICKS + rnd.nextInt(CHEST_IDLE_RANGE_TICKS);
        long totalTicks = idleTicks + CHEST_OPEN_RAMP_TICKS + CHEST_HOLD_OPEN_TICKS + CHEST_CLOSE_RAMP_TICKS;
        long phase = rnd.nextInt((int) totalTicks);
        return new ChestCycle(idleTicks, totalTicks, phase);
    }

    /** Lid openness (0=closed, 1=open) at the given time, with vanilla's cubic ease-out applied. */
    private static float chestOpenness(ChestCycle cycle, float gameTimeTicks) {
        float cyclePos = (gameTimeTicks + cycle.phase()) % cycle.totalTicks();
        long openStart = cycle.idleTicks();
        long holdStart = openStart + CHEST_OPEN_RAMP_TICKS;
        long closeStart = holdStart + CHEST_HOLD_OPEN_TICKS;

        float raw;
        if (cyclePos < openStart) {
            raw = 0f;
        } else if (cyclePos < holdStart) {
            raw = (cyclePos - openStart) / CHEST_OPEN_RAMP_TICKS;
        } else if (cyclePos < closeStart) {
            raw = 1f;
        } else {
            raw = 1f - (cyclePos - closeStart) / CHEST_CLOSE_RAMP_TICKS;
        }
        raw = Mth.clamp(raw, 0f, 1f);

        float eased = 1f - raw;
        return 1f - eased * eased * eased;
    }

    /** Releases one bubble of the chest's stream every few ticks while its lid is opening. */
    private static void spawnDueChestBubbles(ClientLevel level, BlockPos blockPos, int blockPosHash, FishTankRenderState state) {
        long gameTime = level.getGameTime();
        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : state.cosmetics.entrySet()) {
            if (entry.getValue().block() != Blocks.CHEST) continue;

            CosmeticGridCell cell = entry.getKey();
            ChestCycle cycle = chestCycle(cellSeed(blockPosHash, cell));
            long cyclePos = Math.floorMod(gameTime + cycle.phase(), cycle.totalTicks());

            long sinceOpenStart = cyclePos - cycle.idleTicks();
            boolean isStreamTick = sinceOpenStart >= 0
                    && sinceOpenStart % CHEST_BUBBLE_STREAM_INTERVAL_TICKS == 0
                    && sinceOpenStart / CHEST_BUBBLE_STREAM_INTERVAL_TICKS < CHEST_BUBBLE_STREAM_COUNT;
            if (!isStreamTick) continue;

            Long lastSpawnTick = state.chestLastBubbleSpawnTick.get(cell);
            if (lastSpawnTick != null && lastSpawnTick == gameTime) continue;

            state.chestLastBubbleSpawnTick.put(cell, gameTime);
            spawnTankBubble(level, blockPos, cell);
        }
    }

    private static void spawnTankBubble(ClientLevel level, BlockPos blockPos, CosmeticGridCell cell) {
        CosmeticTransforms.Transform transform = CosmeticTransforms.get(Blocks.CHEST);
        double worldX = blockPos.getX() + cell.localX() + transform.offsetX();
        double worldZ = blockPos.getZ() + cell.localZ() + transform.offsetZ();
        double worldY = blockPos.getY() + COSMETIC_FLOOR_Y + transform.offsetY() + 0.05;

        BlockPos topPos = topOfConnectedTankStack(level, blockPos);
        double popWorldY = topPos.getY() + TANK_CEILING_Y;

        level.addParticle(FishtasticParticleTypes.TANK_BUBBLE.value(), worldX, worldY, worldZ, 0.0, popWorldY, 0.0);
    }

    /**
     * Mirrors {@link #spawnDueChestBubbles} for structure-cosmetic parts: any lit furnace-family part
     * (furnace/blast furnace/smoker) periodically spawns the same smoke+flame vanilla furnaces do,
     * scaled down to the cosmetic's own {@link CosmeticStructure#scale()}.
     * <p>
     * Throttle state lives on {@code blockEntity} itself, not on {@link FishTankRenderState} — a fresh
     * render state is allocated by {@code BlockEntityRenderDispatcher} every single rendered frame, so
     * anything stored there for cross-frame throttling is silently reset before it can ever take effect.
     */
    private static void spawnDueFurnaceParticles(ClientLevel level, FishTankBlockEntity blockEntity, FishTankRenderState state) {
        BlockPos blockPos = blockEntity.getBlockPos();
        Map<FishTankBlockEntity.FurnacePartKey, Long> lastParticleTick = blockEntity.getFurnaceLastParticleTick();
        long gameTime = level.getGameTime();

        for (Map.Entry<CosmeticGridCell, FishTankRenderState.ResolvedStructureCosmetic> entry : state.structureCosmetics.entrySet()) {
            CosmeticGridCell anchor = entry.getKey();
            CosmeticStructure structure = entry.getValue().structure();
            Rotation rotation = entry.getValue().rotation();
            List<CosmeticStructure.StructurePart> parts = structure.parts();

            for (int i = 0; i < parts.size(); i++) {
                CosmeticStructure.StructurePart part = parts.get(i);
                BlockState partState = part.state().rotate(rotation);
                if (!(partState.getBlock() instanceof AbstractFurnaceBlock) || !partState.getValue(AbstractFurnaceBlock.LIT)) {
                    continue;
                }

                FishTankBlockEntity.FurnacePartKey key = new FishTankBlockEntity.FurnacePartKey(anchor, i);
                Long lastSpawnTick = lastParticleTick.get(key);
                if (lastSpawnTick != null && gameTime - lastSpawnTick < FURNACE_PARTICLE_INTERVAL_TICKS) continue;

                lastParticleTick.put(key, gameTime);
                spawnFurnaceParticles(level, blockPos, anchor, rotation, part, partState, structure.scale());
            }
        }
    }

    /** Scaled-down replica of vanilla {@code FurnaceBlock#animateTick}'s smoke+flame spawn geometry. */
    private static void spawnFurnaceParticles(ClientLevel level, BlockPos blockPos, CosmeticGridCell anchor,
            Rotation rotation, CosmeticStructure.StructurePart part, BlockState partState, float scale) {
        float[] rotatedXZ = CosmeticStructures.rotateOffset(rotation, part.offsetX(), part.offsetZ());
        double x = blockPos.getX() + anchor.localX() + rotatedXZ[0] * CosmeticGridCell.CELL_WIDTH;
        double y = blockPos.getY() + COSMETIC_FLOOR_Y + part.offsetY() * scale;
        double z = blockPos.getZ() + anchor.localZ() + rotatedXZ[1] * CosmeticGridCell.CELL_WIDTH;

        RandomSource random = level.getRandom();
        Direction facing = partState.getValue(AbstractFurnaceBlock.FACING);
        Direction.Axis axis = facing.getAxis();
        double r = 0.52 * scale;
        double jitter = (random.nextDouble() * 0.6 - 0.3) * scale;
        double dx = axis == Direction.Axis.X ? facing.getStepX() * r : jitter;
        double dy = random.nextDouble() * (6.0 / 16.0) * scale;
        double dz = axis == Direction.Axis.Z ? facing.getStepZ() * r : jitter;

        level.addParticle(FishtasticParticleTypes.MINI_SMOKE.value(), x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
        level.addParticle(FishtasticParticleTypes.MINI_FLAME.value(), x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
    }

    /**
     * Mirrors {@link #spawnDueFurnaceParticles} for the single-cell lit-campfire cosmetic: periodically
     * spawns the soft rising campfire-smoke puff (not the furnace family's ash particle — vanilla's own
     * campfire doesn't spawn that from its fire either) while {@code LIT} is true. Throttle state lives
     * on {@code blockEntity}, not {@link FishTankRenderState}, for the same reason furnace particles do.
     */
    private static void spawnDueCampfireSmoke(ClientLevel level, FishTankBlockEntity blockEntity, FishTankRenderState state) {
        BlockPos blockPos = blockEntity.getBlockPos();
        Map<CosmeticGridCell, Long> lastParticleTick = blockEntity.getCampfireLastParticleTick();
        long gameTime = level.getGameTime();

        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : state.cosmetics.entrySet()) {
            PlacedCosmetic cosmetic = entry.getValue();
            if (cosmetic.block() != Blocks.CAMPFIRE || !cosmetic.blockState().getValue(CampfireBlock.LIT)) continue;

            CosmeticGridCell cell = entry.getKey();
            Long lastSpawnTick = lastParticleTick.get(cell);
            if (lastSpawnTick != null && gameTime - lastSpawnTick < CAMPFIRE_SMOKE_INTERVAL_TICKS) continue;

            lastParticleTick.put(cell, gameTime);
            spawnCampfireSmoke(level, blockPos, cell);
        }
    }

    /**
     * Scaled-down replica of vanilla {@code CampfireBlock.makeParticles}' smoke spawn geometry. The
     * flame quads in {@code block/template_campfire}'s model span local Y 1–17 (of 16, i.e. roughly
     * the upper two-thirds of the model up to just above it), so the jitter is centered on the flame's
     * upper half rather than the model's base — spawning down at the logs would read as smoke coming
     * from nowhere, blended into the fire.
     */
    private static void spawnCampfireSmoke(ClientLevel level, BlockPos blockPos, CosmeticGridCell cell) {
        CosmeticTransforms.Transform transform = CosmeticTransforms.get(Blocks.CAMPFIRE);
        float scale = transform.scale();
        double x = blockPos.getX() + cell.localX() + transform.offsetX();
        double y = blockPos.getY() + COSMETIC_FLOOR_Y + transform.offsetY();
        double z = blockPos.getZ() + cell.localZ() + transform.offsetZ();

        RandomSource random = level.getRandom();
        double dx = (random.nextDouble() * 0.6 - 0.3) * scale;
        double dy = (0.6 + random.nextDouble() * 0.5) * scale;
        double dz = (random.nextDouble() * 0.6 - 0.3) * scale;

        level.addParticle(FishtasticParticleTypes.MINI_CAMPFIRE_SMOKE.value(), x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
    }

    /** Walks upward through tanks connected via an open UP face, so bubbles rise to the true top of a vertical stack. */
    static BlockPos topOfConnectedTankStack(Level level, BlockPos pos) {
        BlockPos current = pos;
        // Bounded to avoid any chance of looping on malformed/cyclic open-face state.
        for (int i = 0; i < 64; i++) {
            if (!(level.getBlockEntity(current) instanceof FishTankBlockEntity tank)
                    || !tank.getOpenFaces().contains(Direction.UP)) {
                break;
            }
            BlockPos above = current.above();
            if (!(level.getBlockEntity(above) instanceof FishTankBlockEntity)) break;
            current = above;
        }
        return current;
    }
}

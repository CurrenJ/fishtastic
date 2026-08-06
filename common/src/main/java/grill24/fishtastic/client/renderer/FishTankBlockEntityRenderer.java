package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticParticleTypes;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.util.ItemSizeHelper;
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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
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

    private static final Vector3f SAND_BASE_Y_OFFSET =
        new Vector3f(0f, CosmeticGridCell.SAND_LAYER_HEIGHT * 0.5f, 0f);
    private static final Vector3f ITEM_POSITION_OFFSET = new Vector3f(0.5f, 8f / 16f, 0.5f);
    public static final float COSMETIC_FLOOR_Y = CosmeticGridCell.FLOOR_Y;
    // Underside of the tank's glass ceiling, in local block-space Y — where rising bubbles pop.
    private static final float TANK_CEILING_Y = 15f / 16f;

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

    // Usable half-width inside the tank walls for swarm scatter (block units from centre).
    private static final float TANK_HALF_EXTENT = 0.35f;
    // Minimum 3D separation between swarm fish before rejection sampling gives up.
    private static final float SWARM_MIN_SEP = 0.14f;
    // Z positions for each depth layer (offset from block centre, back→front).
    private static final float[] LAYER_Z = {-0.25f, 0f, 0.25f};

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
        state.gameTimeTicks = level.getGameTime() + partialTick;
        state.cosmetics = new HashMap<>(blockEntity.getCosmetics());
        state.structureCosmetics = resolveStructureCosmetics(blockEntity, level);

        int blockPosHash = blockEntity.getBlockPos().hashCode();
        state.blockPosHash = blockPosHash;

        if (level instanceof ClientLevel clientLevel) {
            spawnDueChestBubbles(clientLevel, blockEntity.getBlockPos(), blockPosHash, state);
            spawnDueFurnaceParticles(clientLevel, blockEntity, state);
        }

        // Resolve swarm config from the first non-empty item's fish profile.
        ItemStack firstItem = blockEntity.getFirstItem();
        SwarmConfig swarm = resolveSwarmConfig(firstItem, level);

        if (countItems(blockEntity, swarm.count()) > 1) {
            state.fishInstances = buildSwarmInstances(
                    blockEntity, swarm, blockPosHash,
                    blockEntity.getFirstItemRotation(), level);
        } else {
            // Solo fish: single instance at tank centre with no offsets.
            if (!firstItem.isEmpty()) {
                FishAnimationConfig animConfig = resolveAnimationConfig(firstItem, level);
                int firstSlot = blockEntity.getFirstItemSlot();
                state.fishInstances = List.of(new SwarmFishInstance(
                        firstItem.copy(), animConfig,
                        blockEntity.getFirstItemRotation(),
                        0f, 0f, 0f,
                        (long) blockPosHash,
                        blockEntity.isItemMirrored(firstSlot)));
            } else {
                state.fishInstances = List.of();
            }
        }
    }

    @Override
    public void submit(
            FishTankRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector nodes,
            CameraRenderState camera) {

        renderCosmetics(state, poseStack, nodes);

        if (state.fishInstances.isEmpty()) return;

        float t = state.gameTimeTicks;
        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();

        for (SwarmFishInstance fish : state.fishInstances) {
            poseStack.pushPose();

            float scale = 0.5f;
            if (ItemSizeHelper.hasSize(fish.stack())) {
                float size = ItemSizeHelper.getSize(fish.stack());
                scale = 0.01f + (size / 100f) * 0.8f;
            }

            float baseY = computeBaseY(fish.animationConfig(), state.hasOpenDownFace, scale);
            // Swarm's yRange jitter is an absolute world-space offset meant to spread swimmers
            // across a water column — it says nothing about a floor-anchored creature's own size,
            // so applying it there sinks/floats them relative to the sand by a fixed amount that's
            // proportionally huge for a small instance and negligible for a large one (visible as
            // small crabs clipping into the sand). Floor-anchored modes are already pinned to
            // COSMETIC_FLOOR_Y by computeBaseY (correctly scaled), so they get none of this jitter.
            float swarmYOffset = isFloorAnchored(fish.animationConfig()) ? 0f : fish.yOffset();
            poseStack.translate(
                    ITEM_POSITION_OFFSET.x() + fish.xOffset(),
                    baseY + swarmYOffset,
                    ITEM_POSITION_OFFSET.z() + fish.zOffset());

            Random fishRandom = new Random(fish.seed());
            FishAnimator.apply(poseStack, fish.animationConfig(), fishRandom, t, fish.baseRotation(), fish.mirrored());

            poseStack.scale(scale, scale, scale);

            ItemStackRenderState itemRenderState = new ItemStackRenderState();
            resolver.updateForTopItem(itemRenderState, fish.stack(), ItemDisplayContext.FIXED, null, null, 0);

            FishtasticWorldOutlineRenderer.capture(itemRenderState, fish.stack());
            FishtasticWorldOutlineRenderer.submitOutline(poseStack, nodes, itemRenderState, true);
            FishtasticGlintState.WORLD_OUTLINE_MAP.remove(itemRenderState);

            itemRenderState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

            poseStack.popPose();
        }
    }

    // ── Swarm instance building ───────────────────────────────────────────────

    /**
     * Collects up to {@code swarm.count()} non-empty items from the tank and assigns each a
     * spatial offset. Depth layers cycle front-to-back; within each layer fish scatter in XZ
     * with simple rejection sampling to avoid clumping.
     */
    private static List<SwarmFishInstance> buildSwarmInstances(
            FishTankBlockEntity blockEntity,
            SwarmConfig swarm,
            int blockPosHash,
            float firstItemRotation,
            Level level) {

        int count = swarm.count();
        int depthLayers = Math.max(1, Math.min(swarm.depthLayers(), LAYER_Z.length));
        float xzSpread = Math.min(swarm.xzSpread(), TANK_HALF_EXTENT);
        float yRange = swarm.yRange();
        float rotationJitter = swarm.rotationJitter();

        // Precompute rotation matrix coefficients so that lateral scatter (perpendicular to facing)
        // and depth layers (along facing) are expressed in world block XZ rather than always axis-aligned.
        float rotRad = (float) Math.toRadians(firstItemRotation);
        float cosR = (float) Math.cos(rotRad);
        float sinR = (float) Math.sin(rotRad);

        // Collect distinct items (up to count), tracking each one's source slot for mirror lookup.
        List<ItemStack> items = new ArrayList<>(count);
        List<Integer> itemSlots = new ArrayList<>(count);
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && items.size() < count; slot++) {
            ItemStack s = blockEntity.getItem(slot);
            if (!s.isEmpty()) {
                items.add(s.copy());
                itemSlots.add(slot);
            }
        }
        if (items.isEmpty()) return List.of();

        Random rng = new Random((long) blockPosHash);
        List<float[]> placedXYZ = new ArrayList<>(items.size());
        List<SwarmFishInstance> instances = new ArrayList<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            FishAnimationConfig animConfig = resolveAnimationConfig(stack, level);

            int layer = i % depthLayers;
            float depth = LAYER_Z[layer];  // offset along facing direction (local frame)

            // sampleXY returns (lateral, y); lateral is perpendicular to facing, in local frame.
            float[] ly = sampleXY(rng, xzSpread, yRange, depth, placedXYZ);
            float lateral = ly[0];

            // Rotate local (lateral, depth) into world block XZ offsets.
            float worldX = lateral * cosR + depth * sinR;
            float worldZ = -lateral * sinR + depth * cosR;

            // Rejection sampling runs in local space; store pre-rotation coords.
            placedXYZ.add(new float[]{lateral, ly[1], depth});

            // Clamp rotation within ±jitter of the base facing angle.
            float rotation = firstItemRotation + (rng.nextFloat() - 0.5f) * 2f * rotationJitter;
            long seed = (long) blockPosHash ^ ((long) (i + 1) * 2654435761L);
            boolean mirrored = blockEntity.isItemMirrored(itemSlots.get(i));

            instances.add(new SwarmFishInstance(stack, animConfig, rotation, worldX, ly[1], worldZ, seed, mirrored));
        }

        // Render back-to-front by world Z so foreground fish draw over background fish.
        instances.sort(Comparator.comparingDouble(SwarmFishInstance::zOffset));
        return instances;
    }

    /**
     * Samples an (x, y) position offset for a fish at depth {@code z}, with rejection against
     * already-placed fish. Falls back to an unchecked sample after {@code maxAttempts}.
     */
    private static float[] sampleXY(Random rng, float xzSpread, float yRange, float z, List<float[]> placed) {
        int maxAttempts = 25;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            float x = (rng.nextFloat() - 0.5f) * 2f * xzSpread;
            float y = (yRange > 0f) ? (rng.nextFloat() - 0.5f) * yRange : 0f;
            if (isFarEnough(x, y, z, placed)) return new float[]{x, y};
        }
        // Give up on rejection; just place.
        float x = (rng.nextFloat() - 0.5f) * 2f * xzSpread;
        float y = (yRange > 0f) ? (rng.nextFloat() - 0.5f) * yRange : 0f;
        return new float[]{x, y};
    }

    private static boolean isFarEnough(float x, float y, float z, List<float[]> placed) {
        float minSep2 = SWARM_MIN_SEP * SWARM_MIN_SEP;
        for (float[] p : placed) {
            float dx = x - p[0], dy = y - p[1], dz = z - p[2];
            if (dx * dx + dy * dy + dz * dz < minSep2) return false;
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Whether this animation mode pins the creature's Y to the tank floor (see {@link #computeBaseY}). */
    private static boolean isFloorAnchored(FishAnimationConfig animConfig) {
        return animConfig instanceof FishAnimationConfig.FloorSit
                || animConfig instanceof FishAnimationConfig.Planted
                || animConfig instanceof FishAnimationConfig.UprightSit;
    }

    private static float computeBaseY(FishAnimationConfig animConfig, boolean hasOpenDownFace, float scale) {
        return switch (animConfig) {
            case FishAnimationConfig.FloorSit    fs -> COSMETIC_FLOOR_Y + fs.floorOffset();
            case FishAnimationConfig.Planted     p  -> COSMETIC_FLOOR_Y - p.plantDepth() + FishAnimator.PLANTED_PIVOT_Y;
            // Unlike Planted/FloorSit's fixed-size decor, an upright fish's own render scale varies
            // per catch (see ItemSizeHelper below), so the centre-to-bottom pivot compensation must
            // scale with it too — a fixed offset overcorrects for anything smaller than max size.
            case FishAnimationConfig.UprightSit  us -> COSMETIC_FLOOR_Y + us.floorOffset() + FishAnimator.PLANTED_PIVOT_Y * scale;
            default -> {
                float y = ITEM_POSITION_OFFSET.y();
                if (!hasOpenDownFace) y += SAND_BASE_Y_OFFSET.y();
                yield y;
            }
        };
    }

    private static FishAnimationConfig resolveAnimationConfig(ItemStack stack, Level level) {
        if (stack.isEmpty()) return FishAnimationConfig.HorizontalSwim.DEFAULT;

        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) return FishAnimationConfig.HorizontalSwim.DEFAULT;

        ResourceKey<FishProfile> profileKey = ResourceKey.create(
                FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier());

        return level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(profileKey)
                .flatMap(FishProfile::animation)
                .orElse(FishAnimationConfig.HorizontalSwim.DEFAULT);
    }

    private static SwarmConfig resolveSwarmConfig(ItemStack stack, Level level) {
        return SwarmConfig.resolve(stack, level);
    }

    /** Resolves each placed structure's definition once here (render thread never does registry lookups). */
    private static Map<CosmeticGridCell, FishTankRenderState.ResolvedStructureCosmetic> resolveStructureCosmetics(
            FishTankBlockEntity blockEntity, Level level) {
        Map<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> placed = blockEntity.getStructureCosmetics();
        if (placed.isEmpty()) return java.util.Collections.emptyMap();

        var registry = level.registryAccess().lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
        Map<CosmeticGridCell, FishTankRenderState.ResolvedStructureCosmetic> resolved = new HashMap<>();
        for (Map.Entry<CosmeticGridCell, FishTankBlockEntity.PlacedStructureCosmetic> entry : placed.entrySet()) {
            registry.getOptional(entry.getValue().structureId()).ifPresent(structure ->
                    resolved.put(entry.getKey(), new FishTankRenderState.ResolvedStructureCosmetic(structure, entry.getValue().rotation())));
        }
        return resolved;
    }

    private static int countItems(FishTankBlockEntity blockEntity, int max) {
        int count = 0;
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && count < max; slot++) {
            if (!blockEntity.getItem(slot).isEmpty()) count++;
        }
        return count;
    }

    private void renderCosmetics(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
        renderStructureCosmetics(state, poseStack, nodes);

        if (state.cosmetics.isEmpty()) return;

        BlockModelRenderState blockModelState = new BlockModelRenderState();

        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : state.cosmetics.entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            PlacedCosmetic cosmetic = entry.getValue();
            CosmeticTransforms.Transform transform = CosmeticTransforms.get(cosmetic.block());

            poseStack.pushPose();

            double cellX = cell.localX() + transform.offsetX();
            double cellY = COSMETIC_FLOOR_Y + transform.offsetY();
            double cellZ = cell.localZ() + transform.offsetZ();
            poseStack.translate(cellX, cellY, cellZ);

            if (transform.rotX() != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotX()));
            if (transform.rotY() != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(transform.rotY()));
            if (transform.rotZ() != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotZ()));

            // Chest facing must be applied before the scale/recenter below: PoseStack composes
            // transforms in reverse call order, so a rotation pushed after the -0.5,-0.5 recenter
            // would pivot the model's raw (un-recentered) vertices around the cell corner instead
            // of the model's own center, producing an offset that grows with rotation angle.
            if (cosmetic.block() == Blocks.CHEST) {
                Direction facing = cosmetic.blockState().getValue(net.minecraft.world.level.block.ChestBlock.FACING);
                poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            }

            float s = transform.scale();
            poseStack.scale(s, s, s);
            poseStack.translate(-0.5f, 0f, -0.5f);

            if (cosmetic.block() == Blocks.CHEST) {
                long seed = cellSeed(state.blockPosHash, cell);
                float openness = chestOpenness(chestCycle(seed), state.gameTimeTicks);
                nodes.submitModel(chestModel, openness, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, CHEST_SPRITE, chestSprites, 0, null);
            } else if (cosmetic.block() == Blocks.KELP && cosmetic.height() > 1) {
                for (int seg = 0; seg < cosmetic.height(); seg++) {
                    poseStack.pushPose();
                    poseStack.translate(0f, seg, 0f);
                    net.minecraft.world.level.block.state.BlockState segState = seg < cosmetic.height() - 1
                            ? Blocks.KELP_PLANT.defaultBlockState()
                            : Blocks.KELP.defaultBlockState();
                    blockModelResolver.update(blockModelState, segState, BLOCK_DISPLAY_CONTEXT);
                    blockModelState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                    poseStack.popPose();
                }
            } else {
                blockModelResolver.update(blockModelState, cosmetic.blockState(), BLOCK_DISPLAY_CONTEXT);
                blockModelState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            }

            poseStack.popPose();
        }
    }

    /**
     * Renders every placed multi-block structure. Each part's footprint offset is rotated (Section 2's
     * {@link CosmeticStructures#rotateOffset}) and converted from grid-cell units to block-local units
     * via {@link CosmeticGridCell#CELL_WIDTH}; each part's {@link BlockState} is rotated the same way a
     * structure template rotates its blocks. The chest special case needs its {@code FACING} read off
     * the rotated state — reading the authored state's facing would rotate the rest of the structure
     * correctly while leaving the chest's lid pointing the original way.
     */
    private void renderStructureCosmetics(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
        if (state.structureCosmetics.isEmpty()) return;

        BlockModelRenderState blockModelState = new BlockModelRenderState();

        for (Map.Entry<CosmeticGridCell, FishTankRenderState.ResolvedStructureCosmetic> entry : state.structureCosmetics.entrySet()) {
            CosmeticGridCell anchor = entry.getKey();
            CosmeticStructure structure = entry.getValue().structure();
            Rotation rotation = entry.getValue().rotation();
            float scale = structure.scale();

            for (CosmeticStructure.StructurePart part : structure.parts()) {
                poseStack.pushPose();

                float[] rotatedXZ = CosmeticStructures.rotateOffset(rotation, part.offsetX(), part.offsetZ());
                double partX = anchor.localX() + rotatedXZ[0] * CosmeticGridCell.CELL_WIDTH;
                double partZ = anchor.localZ() + rotatedXZ[1] * CosmeticGridCell.CELL_WIDTH;
                double partY = COSMETIC_FLOOR_Y + part.offsetY() * scale;
                poseStack.translate(partX, partY, partZ);

                BlockState partState = part.state().rotate(rotation);

                // Chest fix: ChestModel poses its lid manually from FACING rather than deriving it from
                // a baked model variant, so it needs the same explicit pose rotation single-cosmetic
                // chests do above — but read off the rotated state, not the authored one.
                if (partState.getBlock() == Blocks.CHEST) {
                    Direction facing = partState.getValue(net.minecraft.world.level.block.ChestBlock.FACING);
                    poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
                }

                poseStack.scale(scale, scale, scale);
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

    /** Walks upward through tanks connected via an open UP face, so bubbles rise to the true top of a vertical stack. */
    private static BlockPos topOfConnectedTankStack(Level level, BlockPos pos) {
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

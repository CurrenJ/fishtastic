package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.FishtasticRegistries;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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

    public FishTankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModelResolver = context.blockModelResolver();
    }

    private static final Vector3f SAND_BASE_Y_OFFSET =
        new Vector3f(0f, CosmeticGridCell.SAND_LAYER_HEIGHT * 0.5f, 0f);
    private static final Vector3f ITEM_POSITION_OFFSET = new Vector3f(0.5f, 8f / 16f, 0.5f);
    public static final float COSMETIC_FLOOR_Y = CosmeticGridCell.FLOOR_Y;

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

        int blockPosHash = blockEntity.getBlockPos().hashCode();

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
                state.fishInstances = List.of(new SwarmFishInstance(
                        firstItem.copy(), animConfig,
                        blockEntity.getFirstItemRotation(),
                        0f, 0f, 0f,
                        (long) blockPosHash));
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

            float baseY = computeBaseY(fish.animationConfig(), state.hasOpenDownFace);
            poseStack.translate(
                    ITEM_POSITION_OFFSET.x() + fish.xOffset(),
                    baseY + fish.yOffset(),
                    ITEM_POSITION_OFFSET.z() + fish.zOffset());

            Random fishRandom = new Random(fish.seed());
            FishAnimator.apply(poseStack, fish.animationConfig(), fishRandom, t, fish.baseRotation());

            float scale = 0.5f;
            if (ItemSizeHelper.hasSize(fish.stack())) {
                float size = ItemSizeHelper.getSize(fish.stack());
                scale = 0.01f + (size / 100f) * 0.8f;
            }
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

        // Collect distinct items (up to count).
        List<ItemStack> items = new ArrayList<>(count);
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && items.size() < count; slot++) {
            ItemStack s = blockEntity.getItem(slot);
            if (!s.isEmpty()) items.add(s.copy());
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

            instances.add(new SwarmFishInstance(stack, animConfig, rotation, worldX, ly[1], worldZ, seed));
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

    private static float computeBaseY(FishAnimationConfig animConfig, boolean hasOpenDownFace) {
        return switch (animConfig) {
            case FishAnimationConfig.FloorSit fs -> COSMETIC_FLOOR_Y + fs.floorOffset();
            case FishAnimationConfig.Planted  p  -> COSMETIC_FLOOR_Y - p.plantDepth() + FishAnimator.PLANTED_PIVOT_Y;
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
        if (stack.isEmpty()) return SwarmConfig.DEFAULT;

        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) return SwarmConfig.DEFAULT;

        ResourceKey<FishProfile> profileKey = ResourceKey.create(
                FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier());

        return level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(profileKey)
                .map(FishProfile::swarm)
                .orElse(SwarmConfig.DEFAULT);
    }

    private static int countItems(FishTankBlockEntity blockEntity, int max) {
        int count = 0;
        for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE && count < max; slot++) {
            if (!blockEntity.getItem(slot).isEmpty()) count++;
        }
        return count;
    }

    private void renderCosmetics(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
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

            float s = transform.scale();
            poseStack.scale(s, s, s);
            poseStack.translate(-0.5f, 0f, -0.5f);

            if (cosmetic.block() == Blocks.KELP && cosmetic.height() > 1) {
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
}

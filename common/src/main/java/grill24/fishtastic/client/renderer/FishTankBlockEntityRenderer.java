package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.FishtasticRegistries;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class FishTankBlockEntityRenderer
        implements BlockEntityRenderer<FishTankBlockEntity, FishTankRenderState> {

    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;

    public FishTankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModelResolver = context.blockModelResolver();
    }

    // Lift applied to the display item when a sand floor is present: half the sand layer height.
    private static final Vector3f SAND_BASE_Y_OFFSET =
        new Vector3f(0f, CosmeticGridCell.SAND_LAYER_HEIGHT * 0.5f, 0f);
    private static final Vector3f ITEM_POSITION_OFFSET = new Vector3f(0.5f, 8f / 16f, 0.5f);
    /** Y offset for cosmetics resting on the tank floor — mirrors CosmeticGridCell.FLOOR_Y. */
    public static final float COSMETIC_FLOOR_Y = CosmeticGridCell.FLOOR_Y;

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

        state.itemToRender = blockEntity.getFirstItem().copy();
        state.firstItemRotation = blockEntity.getFirstItemRotation();
        state.hasOpenDownFace = blockEntity.getOpenFaces().contains(Direction.DOWN);
        state.gameTimeTicks = level.getGameTime() + partialTick;
        state.blockPosHash = blockEntity.getBlockPos().hashCode();
        state.cosmetics = new HashMap<>(blockEntity.getCosmetics());
        state.animationConfig = resolveAnimationConfig(state.itemToRender, level);
    }

    @Override
    public void submit(
            FishTankRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector nodes,
            CameraRenderState camera) {

        // Always render cosmetics regardless of whether a display item is present
        renderCosmetics(state, poseStack, nodes);

        ItemStack itemToRender = state.itemToRender;
        if (itemToRender.isEmpty()) return;

        float t = state.gameTimeTicks;
        Random random = new Random(state.blockPosHash);

        poseStack.pushPose();

        // Base position: XZ centre of tank, Y determined by animation mode
        float baseY = computeBaseY(state);
        poseStack.translate(ITEM_POSITION_OFFSET.x(), baseY, ITEM_POSITION_OFFSET.z());

        // Animation (orientation + positional offset for the mode)
        FishAnimator.apply(poseStack, state.animationConfig, random, t, state.firstItemRotation);

        // Scale by item size
        float scale = 0.5f;
        if (ItemSizeHelper.hasSize(itemToRender)) {
            float size = ItemSizeHelper.getSize(itemToRender);
            scale = 0.01f + (size / 100f) * 0.8f;
        }
        poseStack.scale(scale, scale, scale);

        // Render item using new pipeline
        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
        ItemStackRenderState itemRenderState = new ItemStackRenderState();
        resolver.updateForTopItem(itemRenderState, itemToRender, ItemDisplayContext.FIXED, null, null, 0);

        // Submit world outline for quality items (same pattern as ItemEntityRenderer / ItemFrameRenderer)
        FishtasticWorldOutlineRenderer.capture(itemRenderState, itemToRender);
        FishtasticWorldOutlineRenderer.submitOutline(poseStack, nodes, itemRenderState, true);
        FishtasticGlintState.WORLD_OUTLINE_MAP.remove(itemRenderState);

        itemRenderState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Y base position for the fish before animation offsets are applied. */
    private float computeBaseY(FishTankRenderState state) {
        return switch (state.animationConfig) {
            case FishAnimationConfig.FloorSit fs -> COSMETIC_FLOOR_Y + fs.floorOffset();
            // plantDepth = how far the bottom edge sits below the floor.
            // baseY must be PLANTED_PIVOT_Y above the desired bottom-edge position so the
            // pivot trick (translate up → rotate → translate down) leaves the bottom at FLOOR - plantDepth.
            case FishAnimationConfig.Planted  p  -> COSMETIC_FLOOR_Y - p.plantDepth() + FishAnimator.PLANTED_PIVOT_Y;
            default -> {
                float y = ITEM_POSITION_OFFSET.y();
                if (!state.hasOpenDownFace) y += SAND_BASE_Y_OFFSET.y();
                yield y;
            }
        };
    }

    /** Resolves the animation config from the fish profile registry, falling back to HORIZONTAL_SWIM. */
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

    private void renderCosmetics(FishTankRenderState state, PoseStack poseStack, SubmitNodeCollector nodes) {
        if (state.cosmetics.isEmpty()) return;

        BlockModelRenderState blockModelState = new BlockModelRenderState();

        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : state.cosmetics.entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            PlacedCosmetic cosmetic = entry.getValue();
            CosmeticTransforms.Transform transform = CosmeticTransforms.get(cosmetic.block());

            poseStack.pushPose();

            // Position at grid cell center + floor Y + per-type offset
            double cellX = cell.localX() + transform.offsetX();
            double cellY = COSMETIC_FLOOR_Y + transform.offsetY();
            double cellZ = cell.localZ() + transform.offsetZ();
            poseStack.translate(cellX, cellY, cellZ);

            // Per-type rotation
            if (transform.rotX() != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotX()));
            if (transform.rotY() != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(transform.rotY()));
            if (transform.rotZ() != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotZ()));

            float s = transform.scale();
            poseStack.scale(s, s, s);
            // Block models have their origin at the (0,0,0) corner; shift -0.5 in XZ so the
            // block's horizontal center sits at the cell position. Y is intentionally left at
            // the floor — COSMETIC_FLOOR_Y already places the block bottom, not its centre.
            poseStack.translate(-0.5f, 0f, -0.5f);

            // Render as placed block model(s). Kelp stacks KELP_PLANT segments under a KELP tip.
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

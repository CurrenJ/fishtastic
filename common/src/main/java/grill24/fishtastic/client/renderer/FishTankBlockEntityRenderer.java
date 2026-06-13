package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import grill24.fishtastic.client.renderer.FishtasticWorldOutlineRenderer;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Random;

public class FishTankBlockEntityRenderer
        implements BlockEntityRenderer<FishTankBlockEntity, FishTankRenderState> {

    public FishTankBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    private static final Vector3f SAND_BASE_Y_OFFSET = new Vector3f(0f, 1f / 32f, 0f);
    private static final Vector3f ITEM_POSITION_OFFSET = new Vector3f(0.5f, 8f / 16f, 0.5f);

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
    }

    @Override
    public void submit(
            FishTankRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector nodes,
            CameraRenderState camera) {

        ItemStack itemToRender = state.itemToRender;
        if (itemToRender.isEmpty()) return;

        float t = state.gameTimeTicks;
        Random random = new Random(state.blockPosHash);

        poseStack.pushPose();

        // Translate to center of tank
        float yOffset = ITEM_POSITION_OFFSET.y();
        if (!state.hasOpenDownFace) {
            yOffset += SAND_BASE_Y_OFFSET.y();
        }
        poseStack.translate(ITEM_POSITION_OFFSET.x(), yOffset, ITEM_POSITION_OFFSET.z());

        // Bobbing animation
        float amplitude = 0.125f;
        float hertz = 0.08f + (random.nextFloat() * 0.04f);
        float yBobHeight = getBobbingHeight(random, t, amplitude, hertz);
        poseStack.translate(0f, yBobHeight, 0f);

        // Stored rotation
        poseStack.mulPose(Axis.YP.rotationDegrees(state.firstItemRotation));

        // Surfing tilt
        float surfFactor = 0.12f;
        float surfAngle = getSurfingAngle(random, t, amplitude, hertz) * surfFactor;

        // Y-axis wiggle
        float wiggleFactor = 0.5f;
        float yWiggle = getOrganicWiggle(random, t) * wiggleFactor;
        poseStack.mulPose(Axis.YP.rotationDegrees(yWiggle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(surfAngle + 45f));

        // Scale by item size
        float scale = 1f;
        if (ItemSizeHelper.hasSize(itemToRender)) {
            float size = ItemSizeHelper.getSize(itemToRender);
            scale = 0.01f + (size / 100f) * 0.8f;
        } else {
            scale *= 0.5f;
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

    // ── Animation helpers ─────────────────────────────────────────────────────

    private static float getBobbingHeight(Random random, float gameTimeTicks, float amplitude, float hertz) {
        float time = gameTimeTicks / (20f / hertz);
        time += (float) (random.nextFloat() * 2 * Math.PI);
        return (float) (Math.sin(time * 2 * Math.PI) * amplitude);
    }

    private static float getSurfingAngle(Random random, float gameTimeTicks, float amplitude, float hertz) {
        float time = gameTimeTicks / (20f / hertz);
        time += (float) (random.nextFloat() * 2 * Math.PI);
        float derivative = (float) (Math.cos(time * 2 * Math.PI) * amplitude * 2 * Math.PI);
        return (float) Math.toDegrees(Math.atan(derivative));
    }

    private static float getOrganicWiggle(Random random, float gameTimeTicks) {
        float randomOffset = (float) (random.nextFloat() * 2 * Math.PI);
        float slowWave   = (float) Math.sin((gameTimeTicks / 60f  + randomOffset) * 2 * Math.PI) * 15f;
        float mediumWave = (float) Math.sin((gameTimeTicks / 35f  + randomOffset * 1.3f) * 2 * Math.PI) * 8f;
        float fastWave   = (float) Math.sin((gameTimeTicks / 18f  + randomOffset * 0.7f) * 2 * Math.PI) * 4f;
        float drift      = (float) Math.sin((gameTimeTicks / 120f + randomOffset * 0.5f) * 2 * Math.PI) * 10f;
        return slowWave + mediumWave + fastWave + drift;
    }
}

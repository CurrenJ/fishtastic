package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/**
 * Renders a {@link FishPileBlockEntity}'s contents as individual item stacks, laid flat and
 * stacked like pancakes with a small deterministic per-fish XZ jitter/rotation (seeded from the
 * block position, re-derived every frame rather than stored, so it stays stable without needing
 * per-fish state in the block entity).
 */
public class FishPileBlockEntityRenderer implements BlockEntityRenderer<FishPileBlockEntity, FishPileRenderState> {
    // Render scale and layer geometry are derived from the fish item models' actual baked
    // thickness — see FishPileBlockEntity.RENDER_SCALE's javadoc for the full derivation. Kept
    // there (not here) so FishPileBlockEntity.MAX_FISH can be computed from the same numbers
    // without a server-loadable class depending on this client-only renderer.
    private static final float SCALE = FishPileBlockEntity.RENDER_SCALE;
    private static final float BASE_Y = FishPileBlockEntity.RENDER_BASE_Y;
    private static final float LAYER_HEIGHT = FishPileBlockEntity.RENDER_LAYER_HEIGHT;
    private static final float JITTER_XZ = 0.18f;

    public FishPileBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public FishPileRenderState createRenderState() {
        return new FishPileRenderState();
    }

    @Override
    public void extractRenderState(
            FishPileBlockEntity blockEntity,
            FishPileRenderState state,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPos, crumblingOverlay);
        state.fish = new ArrayList<>(blockEntity.getFish());
        state.blockPosHash = blockEntity.getBlockPos().hashCode();
    }

    @Override
    public void submit(FishPileRenderState state, PoseStack poseStack, SubmitNodeCollector nodes, CameraRenderState camera) {
        if (state.fish.isEmpty()) return;

        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
        Level level = Minecraft.getInstance().level;
        RandomSource random = RandomSource.create(state.blockPosHash);

        for (int i = 0; i < state.fish.size(); i++) {
            ItemStack stack = state.fish.get(i);
            if (stack.isEmpty()) continue;

            float jitterX = (random.nextFloat() - 0.5f) * 2f * JITTER_XZ;
            float jitterZ = (random.nextFloat() - 0.5f) * 2f * JITTER_XZ;
            float rotation = random.nextFloat() * 360f;

            poseStack.pushPose();
            poseStack.translate(0.5f + jitterX, BASE_Y + i * LAYER_HEIGHT, 0.5f + jitterZ);
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            poseStack.scale(SCALE, SCALE, SCALE);

            ItemStackRenderState itemRenderState = new ItemStackRenderState();
            resolver.updateForTopItem(itemRenderState, stack, ItemDisplayContext.FIXED, level, null, i);
            itemRenderState.submit(poseStack, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

            poseStack.popPose();
        }
    }
}

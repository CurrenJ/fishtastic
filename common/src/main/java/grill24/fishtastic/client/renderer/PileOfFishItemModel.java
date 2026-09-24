package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.FishtasticItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pile of Fish item's icon: up to three of its fish, shrunk and fanned out so the back ones
 * peek out from behind the front one. 26.1.2 builds this as an {@code ItemModel} that appends the
 * fish items' own layers to the pile's render state; on 1.21.1 it's drawn by the item's
 * {@code builtin/entity} renderer ({@link FishtasticItemRenderers}), with each fish drawn through
 * its own model and the same transforms.
 */
public final class PileOfFishItemModel {
    private static final int MAX_LAYERS = 3;

    // Each item is shrunk to this fraction of its normal size
    private static final float SCALE = 0.7f;

    // Per-layer XY offset so back items peek out from behind front ones.
    // Layer 0 (front / newest) sits at the bottom-left; successive layers
    // step toward the top-right.
    private static final float STEP_X = 0.12f;
    private static final float STEP_Y = 0.10f;

    // Small Z nudge per layer to keep depth ordering correct
    private static final float STEP_Z = 0.02f;

    // Offset to re-center the item after scaling around the ItemTransform's
    // fixed (-0.5, -0.5, -0.5) pivot.
    private static final float RE_CENTER = 0.5f * (1f - SCALE) - 0.2f;

    // 26.1.2 applies this in place of each fish layer's own display transform, in every context.
    private static final ItemTransform PILE_SCALE_TRANSFORM = new ItemTransform(
            new Vector3f(0, 0, 0),      // rotation
            new Vector3f(0, 0, 0),      // translation
            new Vector3f(SCALE, SCALE, 1f)  // scale
    );

    /**
     * Called with the pose in the item's model space (display transform applied, then the
     * {@code -0.5} centring), like any {@code builtin/entity} renderer.
     */
    static void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        List<ItemStack> fish = new ArrayList<>(MAX_LAYERS);
        for (ItemStack content : FishtasticItemData.bundleContentsOrEmpty(stack).items()) {
            if (fish.size() == MAX_LAYERS) break;
            fish.add(content);
        }
        if (fish.isEmpty()) return;

        // The fish are flat sprites, lit front-on in the GUI like any flat item (26.1.2's layers
        // keep their own models' lighting); the pile's builtin model asks for block lighting.
        boolean flatGuiLighting = displayContext == ItemDisplayContext.GUI && buffers instanceof MultiBufferSource.BufferSource;
        if (flatGuiLighting) {
            ((MultiBufferSource.BufferSource) buffers).endBatch();
            Lighting.setupForFlatItems();
        }

        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        // Render from back (oldest = index limit-1) to front (newest = index 0)
        for (int i = fish.size() - 1; i >= 0; i--) {
            ItemStack layerStack = fish.get(i);
            float d = (float) i;
            poseStack.pushPose();
            // 26.1.2's layer pose: its ItemTransform, the -0.5 centring, then the local offset.
            poseStack.translate(0.5f, 0.5f, 0.5f);
            PILE_SCALE_TRANSFORM.apply(false, poseStack);
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            poseStack.translate(RE_CENTER + d * STEP_X, RE_CENTER + d * STEP_Y, -d * STEP_Z);
            // Draw the fish's own model with no display transform (NONE is the identity), undoing
            // the -0.5 render() applies itself.
            poseStack.translate(0.5f, 0.5f, 0.5f);
            itemRenderer.render(layerStack, ItemDisplayContext.NONE, false, poseStack, buffers, light, overlay,
                    itemRenderer.getModel(layerStack, mc.level, null, i));
            poseStack.popPose();
        }

        if (flatGuiLighting) {
            ((MultiBufferSource.BufferSource) buffers).endBatch();
            Lighting.setupFor3DItems();
        }
    }

    private PileOfFishItemModel() {}
}

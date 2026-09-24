package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a bundle of fish the way the placed Fish Pile <em>block</em> looks — each fish laid flat
 * and stacked like pancakes inside a one-block cube, viewed through the vanilla block display
 * transform so it reads as a real block rather than a flat icon.
 *
 * <p>Geometry is the same math {@link FishPileBlockEntityRenderer} draws in-world with (shared via
 * {@link FishPileBlockEntity}'s render constants). 26.1.2 selects this per stack through the
 * {@code minecraft:item_model} component; on 1.21.1 the Pile of Fish's item renderer
 * ({@link FishtasticItemRenderers}) picks it for stacks marked by
 * {@code FishPileIcons.PILE_BLOCK_MARKER}. Contents render bottom-up in list order, matching the
 * block's oldest-first insertion order.
 */
public final class FishPileBlockItemModel {
    private static final float SCALE = FishPileBlockEntity.RENDER_SCALE;
    private static final float BASE_Y = FishPileBlockEntity.RENDER_BASE_Y;
    private static final float LAYER_HEIGHT = FishPileBlockEntity.RENDER_LAYER_HEIGHT;
    private static final float JITTER_XZ = FishPileBlockEntityRenderer.JITTER_XZ;
    /** Fixed (rather than the in-world block-position hash) so one pile always looks the same. */
    private static final long JITTER_SEED = 1913L;

    /**
     * Called with the pose in the Pile of Fish item's model space. That item's model has no
     * display transform of its own (its flat icon, {@link PileOfFishItemModel}, applies its own),
     * so this applies the vanilla block one — {@code block/block}'s, read off a plain block item.
     */
    static void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        List<ItemStack> fish = new ArrayList<>();
        for (ItemStack content : FishtasticItemData.bundleContentsOrEmpty(stack).items()) {
            if (fish.size() == FishPileBlockEntity.MAX_FISH) break;
            fish.add(content);
        }
        if (fish.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        itemRenderer.getItemModelShaper().getItemModel(Items.STONE).getTransforms()
                .getTransform(displayContext).apply(leftHand, poseStack);
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        RandomSource random = RandomSource.create(JITTER_SEED);
        for (int i = 0; i < fish.size(); i++) {
            float jitterX = (random.nextFloat() - 0.5f) * 2f * JITTER_XZ;
            float jitterZ = (random.nextFloat() - 0.5f) * 2f * JITTER_XZ;
            float rotation = random.nextFloat() * 360f;

            ItemStack layerStack = fish.get(i);
            if (layerStack.isEmpty()) continue;

            // Applied to a fish item model's own [0,1]³ vertices right-to-left: centre the fish on
            // the origin, shrink it, lay it flat, spin it, then lift it onto its layer of the stack.
            // (The fish's own render below starts with the centring translate(-0.5).)
            poseStack.pushPose();
            poseStack.translate(0.5f + jitterX, BASE_Y + i * LAYER_HEIGHT, 0.5f + jitterZ);
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            poseStack.scale(SCALE, SCALE, SCALE);
            itemRenderer.render(layerStack, ItemDisplayContext.NONE, false, poseStack, buffers, light, overlay,
                    itemRenderer.getModel(layerStack, mc.level, null, i));
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private FishPileBlockItemModel() {}
}

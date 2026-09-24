package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * In-world quality outlines: the 1.21.1 port (from the rendering spike).
 *
 * <p>26.1.2 captures the stack during render-state extraction and adds the quad at submission via
 * {@code submitCustomGeometry}, from four hooks (item entities, item frames, the tank renderer,
 * first-person held items), sized from the model bounding box, and has to mirror U for item frames
 * because the display transform is applied after its hook. 1.21.1 has the stack, the model and the
 * pose all in scope inside {@code ItemRenderer.render}, which all four go through: hooking just
 * after the display transform and the {@code translate(-0.5, -0.5, -0.5)}
 * ({@code mixin/ItemRendererMixin}) puts us in model space, where a flat item's sprite covers
 * exactly [0,1]x[0,1] at z = 0.5. The quad is drawn there, so it follows every display transform
 * (bob, spin, frame rotation, hand) with no special cases.
 *
 * <p>Flat (texture-based) item models only, as in 26.1.2.
 */
public final class FishtasticWorldOutlineRenderer {

    private static final int FULL_BRIGHT = 15728880;

    /**
     * The contexts 26.1.2 outlines: item entities (GROUND), item frames and tank fish (FIXED), and
     * first-person held items. Third-person hands, heads, the GUI (its own path,
     * {@link FishtasticGuiOutlineRenderer}) and nested draws (NONE, e.g. the fish in a pile icon)
     * get none.
     */
    private static boolean outlines(ItemDisplayContext context) {
        return switch (context) {
            case GROUND, FIXED, FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> true;
            default -> false;
        };
    }

    /** Called from {@code ItemRenderer.render} right after the model-space translate. */
    public static void render(ItemStack stack, ItemDisplayContext context, BakedModel model,
                              PoseStack poseStack, MultiBufferSource bufferSource) {
        if (!outlines(context)) {
            return;
        }
        if (model.isGui3d() || model.isCustomRenderer()) {
            return; // 3D block models get no flat outline.
        }
        FishtasticOutlineStyle style = FishtasticOutlineStyle.of(ItemEffectManager.getEffectForItem(stack));
        if (style == null) {
            return;
        }
        FishtasticItemOutlineAtlas atlas = FishtasticItemOutlineAtlas.getInstance();
        FishtasticItemOutlineAtlas.SlotView slot = atlas.requestSlot(stack, style);
        if (slot == null || atlas.outlineTextureId() < 0) {
            return;
        }

        // The slot is SLOT_PX / ITEM_RENDER_PX times the item; grow the unit sprite square by the
        // same ratio about its centre so the item texels stay aligned and the padding holds the ring.
        float half = 0.5F * FishtasticItemOutlineAtlas.SLOT_PX / FishtasticItemOutlineAtlas.ITEM_RENDER_PX;
        float min = 0.5F - half;
        float max = 0.5F + half;
        float z = 0.5F;

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = bufferSource.getBuffer(FishtasticRenderTypes.ITEM_OUTLINE);
        vertex(buffer, pose, min, max, z, slot.u0(), slot.v0());
        vertex(buffer, pose, min, min, z, slot.u0(), slot.v1());
        vertex(buffer, pose, max, min, z, slot.u1(), slot.v1());
        vertex(buffer, pose, max, max, z, slot.u1(), slot.v0());
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private FishtasticWorldOutlineRenderer() {}
}

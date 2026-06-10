package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * In-world outline rendering for item entities and item frame items.
 *
 * <p>The GUI outline path computes outlines live from the vanilla {@code GuiItemAtlas};
 * this path does the same against the Fishtastic-owned {@link FishtasticItemOutlineAtlas},
 * which holds a padded 2D bake of each quality item.  At submit time a single double-sided
 * quad is added co-planar with the item model, scaled up by the slot/item ratio so the
 * outline can extend past the sprite edge, textured with the item's atlas slot, and drawn
 * with the effect's world outline pipeline (the same fragment logic as the GUI shaders).
 *
 * <p>Flat (texture-based) item models only: a flat "sticker" outline behind a 3D block
 * model would look wrong, so models thicker than vanilla's {@code FLAT_ITEM_DEPTH_THRESHOLD}
 * are skipped.
 */
public final class FishtasticWorldOutlineRenderer {

    /** Outline data captured at render-state extraction, consumed at submission. */
    public record Entry(ItemEffect effect, FishtasticItemOutlineAtlas.SlotView slot) {}

    /** Matches {@code ItemEntityRenderer.FLAT_ITEM_DEPTH_THRESHOLD}. */
    private static final double FLAT_ITEM_DEPTH_THRESHOLD = 0.0625;

    /**
     * Called from extraction mixins while the {@link ItemStack} is still in scope.
     * Resolves the item's effect, requests (or queues) its atlas slot, and records both
     * against the render state's identity for {@link #submitOutline} to pick up.
     */
    public static void capture(ItemStackRenderState renderState, ItemStack stack) {
        if (stack.isEmpty()) {
            FishtasticGlintState.WORLD_OUTLINE_MAP.remove(renderState);
            return;
        }
        ItemEffect effect = ItemEffectManager.getEffectForItem(stack);
        if (effect == null || !effect.hasOutline()) {
            FishtasticGlintState.WORLD_OUTLINE_MAP.remove(renderState);
            return;
        }
        FishtasticItemOutlineAtlas.SlotView slot = FishtasticItemOutlineAtlas.getInstance().requestSlot(stack);
        if (slot == null) {
            // Not baked yet (queued for next frame) or atlas full — no outline this frame.
            FishtasticGlintState.WORLD_OUTLINE_MAP.remove(renderState);
            return;
        }
        FishtasticGlintState.WORLD_OUTLINE_MAP.put(renderState, new Entry(effect, slot));
    }

    /**
     * Called from submission mixins with the pose positioned exactly where the item model
     * is about to be submitted (after bob/spin for item entities, after frame rotation and
     * scaling for item frames).  No-op unless {@link #capture} recorded an entry this frame.
     *
     * @param mirrorU swap the quad's U coordinates.  Needed for item frames: the
     *                {@code item/generated} FIXED display transform rotates the item 180°
     *                about Y <em>inside</em> {@code state.item.submit(...)} — after this
     *                injection point — so without the swap the outline appears horizontally
     *                mirrored against the framed item.  GROUND (item entities) has no such
     *                rotation.
     */
    public static void submitOutline(PoseStack poseStack, SubmitNodeCollector collector, ItemStackRenderState renderState, boolean mirrorU) {
        Entry entry = FishtasticGlintState.WORLD_OUTLINE_MAP.get(renderState);
        if (entry == null) {
            return;
        }

        AABB bbox = renderState.getModelBoundingBox();
        if (bbox.getZsize() > FLAT_ITEM_DEPTH_THRESHOLD) {
            return; // 3D block models get no flat outline.
        }

        // The atlas slot is larger than the item render by SLOT_PX / ITEM_RENDER_PX; the quad
        // must grow by the same ratio about the model centre so item texels stay aligned with
        // the model and the padding ring carries the outline.
        float expand = (float) FishtasticItemOutlineAtlas.SLOT_PX / FishtasticItemOutlineAtlas.ITEM_RENDER_PX;
        float cx = (float) ((bbox.minX + bbox.maxX) * 0.5);
        float cy = (float) ((bbox.minY + bbox.maxY) * 0.5);
        float cz = (float) ((bbox.minZ + bbox.maxZ) * 0.5);
        float halfW = (float) (bbox.getXsize() * 0.5) * expand;
        float halfH = (float) (bbox.getYsize() * 0.5) * expand;
        if (halfW <= 0.0F || halfH <= 0.0F) {
            return;
        }

        FishtasticItemOutlineAtlas.SlotView slot = entry.slot();
        float uLeft = mirrorU ? slot.u1() : slot.u0();
        float uRight = mirrorU ? slot.u0() : slot.u1();
        // The shader takes all colour data from the UBO; the vertex colour is for GPU
        // debugger identification only (same convention as the GUI blit path).
        int color = entry.effect().outlineColor();

        collector.submitCustomGeometry(poseStack, entry.effect().worldOutlineRenderType(), (pose, buffer) -> {
            buffer.addVertex(pose, cx - halfW, cy + halfH, cz).setUv(uLeft, slot.v0()).setColor(color);
            buffer.addVertex(pose, cx - halfW, cy - halfH, cz).setUv(uLeft, slot.v1()).setColor(color);
            buffer.addVertex(pose, cx + halfW, cy - halfH, cz).setUv(uRight, slot.v1()).setColor(color);
            buffer.addVertex(pose, cx + halfW, cy + halfH, cz).setUv(uRight, slot.v0()).setColor(color);
        });
    }

    private FishtasticWorldOutlineRenderer() {}
}

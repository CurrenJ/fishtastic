package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

    /** Full-bright lightmap coords, so the outline glows instead of picking up ambient light. */
    private static final int FULL_BRIGHT = 15728880;

    /**
     * One render type for every quality tier: the outline is fully baked into the atlas, so the
     * draw carries no per-effect state. Targets the item-entity output like vanilla item entity
     * render types.
     */
    private static final RenderType OUTLINE_RENDER_TYPE = RenderType.create(
            "fishtastic_world_item_outline",
            RenderSetup.builder(FishtasticRenderPipelines.WORLD_OUTLINE)
                    .withTexture("Sampler0", FishtasticItemOutlineAtlas.TEXTURE_ID)
                    .useLightmap()
                    .useOverlay()
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup());

    private static void outlineVertex(VertexConsumer buffer, PoseStack.Pose pose,
                                      float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

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
        FishtasticItemOutlineAtlas.SlotView slot = FishtasticItemOutlineAtlas.getInstance().requestSlot(stack, effect);
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

        // The atlas already holds the finished, tinted outline (colour, falloff and opacity were
        // applied at bake time), so the vertex colour must be plain white — anything else would
        // modulate the baked ring.
        //
        // Vertices are in NEW_ENTITY format because the pipeline is registered with Iris, and a
        // shaderpack's gbuffers_entities program reads all of these attributes; omitting any of
        // them leaves the pack's shader sampling unbound data. Light is full-bright so the outline
        // reads as a glow rather than picking up ambient darkness, and the normal faces the viewer.
        collector.submitCustomGeometry(poseStack, OUTLINE_RENDER_TYPE, (pose, buffer) -> {
            outlineVertex(buffer, pose, cx - halfW, cy + halfH, cz, uLeft, slot.v0());
            outlineVertex(buffer, pose, cx - halfW, cy - halfH, cz, uLeft, slot.v1());
            outlineVertex(buffer, pose, cx + halfW, cy - halfH, cz, uRight, slot.v1());
            outlineVertex(buffer, pose, cx + halfW, cy + halfH, cz, uRight, slot.v0());
        });
    }

    private FishtasticWorldOutlineRenderer() {}
}

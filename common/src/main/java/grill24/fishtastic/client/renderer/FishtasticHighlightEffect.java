package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * The animated warm-gold "look here" outline drawn around GUI items the player is being steered
 * toward (rods while holding bait/hook/charm on the cursor, bait/hooks/charms while holding a rod,
 * and the tutorial's bait/rod prompts).
 *
 * <p>Same shape as {@link FishtasticBlackOutlineEffect}: UI-context driven (see
 * {@link FishtasticGlintState#HIGHLIGHT_REQUESTED}), fixed look. Unlike the quality-tier outlines it
 * pulses, so it can't be mistaken for a rarity.
 *
 * <p>26.1.2 builds a {@code RenderPipeline} + params UBO and samples the item's cell in vanilla's GUI
 * item atlas. 1.21.1 has neither, so — like {@link FishtasticSilhouetteEffect} — the
 * {@code gui_item_highlight} {@link ShaderInstance} samples the item's slot in
 * {@link FishtasticItemOutlineAtlas}'s mask, with the parameters as plain uniforms. The mask slot is
 * already padded past the item (4 item pixels a side), so the padded quad never reaches a
 * neighbouring slot; the slot's bounds are still passed so the distance search can't either.
 */
public final class FishtasticHighlightEffect {

    /** How far past the 16x16 item slot the blit quad extends, in item pixels, so the glow isn't clipped. */
    public static final int PAD_PX = 2;

    /** Warm gold, matching the tutorial's hotbar box (0xFFFFDD44). */
    private static final float COLOR_R = 1.0f, COLOR_G = 0.867f, COLOR_B = 0.267f;
    private static final float OPACITY = 1.0f;
    /** Solid ring thickness, item pixels. */
    private static final float WIDTH = 0.75f;
    /** Soft falloff beyond the ring at the top of the pulse, item pixels. WIDTH + GLOW must stay within {@link #PAD_PX}. */
    private static final float GLOW = 1.25f;
    /** Cycles per in-game day (24000 ticks) — 900 = 0.75 pulses per real second. Keep it a whole number. */
    private static final float PULSE_SPEED = 900.0f;
    /** How far the glow's reach shrinks at the bottom of each pulse (0.45 = down to 55%). The ring itself doesn't pulse. */
    private static final float PULSE_AMOUNT = 0.45f;

    private FishtasticHighlightEffect() {}

    /**
     * Draws the highlight around {@code stack}'s {@code size}-wide square centred on ({@code cx},
     * {@code cy}) at depth {@code z}. Nothing is drawn until the item's mask is baked (next frame).
     */
    static void render(GuiGraphics guiGraphics, ItemStack stack, float cx, float cy, float size, float z) {
        ShaderInstance shader = FishtasticShaders.guiItemHighlight;
        FishtasticItemOutlineAtlas atlas = FishtasticItemOutlineAtlas.getInstance();
        FishtasticItemOutlineAtlas.SlotView slot = atlas.requestSlot(stack, null);
        if (shader == null || slot == null || atlas.maskTextureId() < 0) {
            return;
        }
        FishtasticItemOutlineAtlas.SlotView item = FishtasticItemOutlineAtlas.innerView(slot);

        guiGraphics.flush();

        shader.safeGetUniform("HighlightColor").set(COLOR_R, COLOR_G, COLOR_B, 1.0f);
        shader.safeGetUniform("Opacity").set(OPACITY);
        shader.safeGetUniform("Width").set(WIDTH);
        shader.safeGetUniform("Glow").set(GLOW);
        shader.safeGetUniform("PulseSpeed").set(PULSE_SPEED);
        shader.safeGetUniform("PulseAmount").set(PULSE_AMOUNT);
        shader.safeGetUniform("TexelsPerItemPx").set((float) FishtasticItemOutlineAtlas.TEXELS_PER_ITEM_PX);
        shader.safeGetUniform("SlotBounds").set(
                Math.min(slot.u0(), slot.u1()), Math.min(slot.v0(), slot.v1()),
                Math.max(slot.u0(), slot.u1()), Math.max(slot.v0(), slot.v1()));
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, atlas.maskTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // No depth write: the quad is mostly transparent and must not occlude the item drawn next.
        RenderSystem.depthMask(false);

        // Pad the quad and extrapolate the UVs to match: the item's 16 px span item.u0..u1.
        float half = size / 2.0F;
        float padGui = size * PAD_PX / 16.0F;
        float du = (item.u1() - item.u0()) / 16.0F * PAD_PX;
        float dv = (item.v1() - item.v0()) / 16.0F * PAD_PX;
        float x0 = cx - half - padGui, x1 = cx + half + padGui;
        float y0 = cy - half - padGui, y1 = cy + half + padGui;
        float u0 = item.u0() - du, u1 = item.u1() + du;
        float v0 = item.v0() - dv, v1 = item.v1() + dv;

        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, FishtasticShaders.GUI_EFFECT_FORMAT);
        buffer.addVertex(pose, x0, y0, z).setUv(u0, v0);
        buffer.addVertex(pose, x0, y1, z).setUv(u0, v1);
        buffer.addVertex(pose, x1, y1, z).setUv(u1, v1);
        buffer.addVertex(pose, x1, y0, z).setUv(u1, v0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}

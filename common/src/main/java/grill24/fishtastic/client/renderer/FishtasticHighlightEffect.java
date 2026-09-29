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
 * and the tutorial's bait/rod prompts), requested through
 * {@link FishtasticGlintState#HIGHLIGHT_REQUESTED}.
 *
 * <p>Same shape as {@link FishtasticBlackOutlineEffect}: UI-context driven, fixed look. Unlike the
 * quality-tier outlines it pulses, so it can't be mistaken for a rarity.
 *
 * <p>26.1.2 draws it with a {@code RenderPipeline} + params UBO over the item's cell in vanilla's
 * GUI item atlas, the cell passed in the vertex colour because the padded quad's UVs spill into
 * neighbouring cells. On 1.20.1 (as for {@link FishtasticSilhouetteEffect}) the
 * {@code gui_item_highlight} {@link ShaderInstance} samples the item's slot in
 * {@link FishtasticItemOutlineAtlas}'s mask, with the parameters as plain uniforms. That slot is
 * already padded {@link FishtasticItemOutlineAtlas#PAD_PX} texels past the item, more than this
 * effect's {@link #PAD_PX}, so the quad stays inside its own slot and no cell bounds are needed.
 */
public final class FishtasticHighlightEffect {

    /** How far past the 16x16 item slot the quad extends, in item pixels, so the glow isn't clipped. */
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
     * {@code cy}) at depth {@code z}, before the item itself is drawn. Nothing is drawn until the
     * item's mask is baked (the next frame).
     */
    static void render(GuiGraphics guiGraphics, ItemStack stack, float cx, float cy, float size, float z) {
        ShaderInstance shader = FishtasticShaders.guiItemHighlight;
        FishtasticItemOutlineAtlas atlas = FishtasticItemOutlineAtlas.getInstance();
        FishtasticItemOutlineAtlas.SlotView slot = atlas.requestSlot(stack, null);
        if (shader == null || slot == null || atlas.maskTextureId() < 0) {
            return;
        }
        // The item's 16x16 area grown by PAD_PX item pixels on every side, in atlas UV.
        float inset = (float) (FishtasticItemOutlineAtlas.PAD_PX - PAD_PX * FishtasticItemOutlineAtlas.TEXELS_PER_ITEM_PX)
                / FishtasticItemOutlineAtlas.TEXTURE_SIZE;
        float u0 = slot.u0() + inset, v0 = slot.v0() - inset, u1 = slot.u1() - inset, v1 = slot.v1() + inset;

        guiGraphics.flush();

        shader.safeGetUniform("HighlightColor").set(COLOR_R, COLOR_G, COLOR_B, 1.0f);
        shader.safeGetUniform("Opacity").set(OPACITY);
        shader.safeGetUniform("Width").set(WIDTH);
        shader.safeGetUniform("Glow").set(GLOW);
        shader.safeGetUniform("PulseSpeed").set(PULSE_SPEED);
        shader.safeGetUniform("PulseAmount").set(PULSE_AMOUNT);
        shader.safeGetUniform("TexelsPerItemPx").set((float) FishtasticItemOutlineAtlas.TEXELS_PER_ITEM_PX);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, atlas.maskTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // No depth write: the quad is mostly transparent and must not occlude the item drawn next.
        RenderSystem.depthMask(false);

        float half = size * (8 + PAD_PX) / 16.0F;
        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, FishtasticShaders.GUI_EFFECT_FORMAT);
        buffer.vertex(pose, cx - half, cy - half, z).uv(u0, v0).endVertex();
        buffer.vertex(pose, cx - half, cy + half, z).uv(u0, v1).endVertex();
        buffer.vertex(pose, cx + half, cy + half, z).uv(u1, v1).endVertex();
        buffer.vertex(pose, cx + half, cy - half, z).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}

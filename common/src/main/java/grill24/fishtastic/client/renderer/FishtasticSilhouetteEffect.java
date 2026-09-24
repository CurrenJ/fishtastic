package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * The GUI item silhouette fill used by {@code SilhouetteItemButton} (fish encyclopedia "never
 * caught" icons), requested through {@link FishtasticGlintState#SILHOUETTE_REQUESTED}.
 *
 * <p>Single shared look, not data-driven and not keyed by an {@code ItemStack}'s rarity-tier data
 * component — it's driven by UI state (has this player caught this fish?). 26.1.2 draws it with a
 * {@code RenderPipeline} + params UBO over the item's slot in vanilla's GUI item atlas; on 1.21.1 the
 * {@code gui_item_silhouette} {@link ShaderInstance} samples the item's slot in
 * {@link FishtasticItemOutlineAtlas}'s mask, with the parameters as plain uniforms.
 */
public final class FishtasticSilhouetteEffect {
    /** Fill colour (opaque black) and overall opacity. */
    private static final float COLOR_R = 0.0f, COLOR_G = 0.0f, COLOR_B = 0.0f;
    private static final float OPACITY = 1.0f;
    /** Gentle breathing alpha — 0 disables the pulse entirely. Cycles per in-game day. */
    private static final float PULSE_SPEED = 0.0f;
    private static final float PULSE_AMOUNT = 0.35f;
    /**
     * Rounds off fin/tail spikes that would otherwise give away the species at a glance. In 26.1.2's
     * GUI-atlas texels, which are one physical pixel each ({@code guiScale} per item pixel); the
     * mask has {@link FishtasticItemOutlineAtlas#TEXELS_PER_ITEM_PX}, so it's rescaled per draw to
     * blur the same item-pixel distance.
     */
    private static final float EDGE_BLUR_TEXELS = 2.5f;
    /** Per-icon dissolve noise: fine grain, slow drift, moderate erosion of the boundary only. */
    private static final float DISSOLVE_SCALE = 0.35f;
    private static final float DISSOLVE_SPEED = 0.015f;
    private static final float DISSOLVE_STRENGTH = 0.6f;

    private FishtasticSilhouetteEffect() {}

    /** Draws {@code stack}'s silhouette over its {@code size}-wide square centred on ({@code cx}, {@code cy}) at depth {@code z}. */
    static void render(GuiGraphics guiGraphics, ItemStack stack, float cx, float cy, float size, float z) {
        ShaderInstance shader = FishtasticShaders.guiItemSilhouette;
        FishtasticItemOutlineAtlas atlas = FishtasticItemOutlineAtlas.getInstance();
        FishtasticItemOutlineAtlas.SlotView slot = atlas.requestSlot(stack, null);
        if (shader == null || slot == null || atlas.maskTextureId() < 0) {
            return;
        }
        FishtasticItemOutlineAtlas.SlotView item = FishtasticItemOutlineAtlas.innerView(slot);

        guiGraphics.flush();

        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        shader.safeGetUniform("SilhouetteColor").set(COLOR_R, COLOR_G, COLOR_B, 1.0f);
        shader.safeGetUniform("Opacity").set(OPACITY);
        shader.safeGetUniform("PulseSpeed").set(PULSE_SPEED);
        shader.safeGetUniform("PulseAmount").set(PULSE_AMOUNT);
        shader.safeGetUniform("EdgeBlurTexels").set(
                (float) (EDGE_BLUR_TEXELS * FishtasticItemOutlineAtlas.TEXELS_PER_ITEM_PX / guiScale));
        shader.safeGetUniform("DissolveScale").set(DISSOLVE_SCALE);
        shader.safeGetUniform("DissolveSpeed").set(DISSOLVE_SPEED);
        shader.safeGetUniform("DissolveStrength").set(DISSOLVE_STRENGTH);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, atlas.maskTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float half = size / 2.0F;
        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, FishtasticShaders.GUI_EFFECT_FORMAT);
        buffer.addVertex(pose, cx - half, cy - half, z).setUv(item.u0(), item.v0());
        buffer.addVertex(pose, cx - half, cy + half, z).setUv(item.u0(), item.v1());
        buffer.addVertex(pose, cx + half, cy + half, z).setUv(item.u1(), item.v1());
        buffer.addVertex(pose, cx + half, cy - half, z).setUv(item.u1(), item.v0());
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.disableBlend();
    }
}

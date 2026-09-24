package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * GUI item effects for 1.21.1, called at the head of {@code GuiGraphics.renderItem}
 * ({@code mixin/GuiGraphicsMixin}): the quality outline, the fishing minigame's black gear outline,
 * and the encyclopedia's never-caught silhouette.
 *
 * <p>26.1.2 draws all three from vanilla's {@code GuiItemAtlas} (per fragment, in
 * {@code GuiRendererMixin}). That atlas only exists from 1.21.6; 1.21.1 draws GUI items straight
 * to the screen, leaving nothing to sample. So the GUI path here reuses
 * {@link FishtasticItemOutlineAtlas} — the pre-baked ring the world path draws, and the item mask
 * the silhouette needs — and blits it around the slot just before the item.
 *
 * <p>The slot is {@code SLOT_PX / ITEM_RENDER_PX} = 1.5x the item, so a 16x16 item gets a 24x24
 * quad offset by 4 GUI pixels. Sampled with LINEAR filtering: the atlas holds 4 texels per GUI
 * pixel, and NEAREST minification at GUI scales below 4 drops the thinnest (1-texel) rings.
 */
public final class FishtasticGuiOutlineRenderer {

    private static final float SLOT_GUI_PX = 16.0F * FishtasticItemOutlineAtlas.SLOT_PX / FishtasticItemOutlineAtlas.ITEM_RENDER_PX;
    /** Same depth as the item (GuiGraphics.renderItem translates items to z=150). */
    private static final float ITEM_Z = 150.0F;

    /**
     * Called at the head of {@code GuiGraphics.renderItem}, with the item's GUI top-left corner.
     *
     * @return true when the item itself must not be drawn (the silhouette replaces it)
     */
    public static boolean render(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        return renderAround(guiGraphics, stack, x + 8.0F, y + 8.0F, 16.0F, ITEM_Z);
    }

    /**
     * The same, for an item drawn {@code size} wide centred on ({@code cx}, {@code cy}) at depth
     * {@code z} of the current pose. The fishing minigame draws its items in unit space around the
     * origin ({@code IGuiGraphicsExtension#fishtastic$renderItem}), so it passes (0, 0, 1, 0).
     */
    public static boolean renderAround(GuiGraphics guiGraphics, ItemStack stack, float cx, float cy, float size, float z) {
        if (Boolean.TRUE.equals(FishtasticGlintState.SILHOUETTE_REQUESTED.get())) {
            // Replaces the item entirely, like 26.1.2's cancelled blit. Until the mask is baked
            // (the next frame) nothing is drawn, so the species never shows for a frame.
            FishtasticSilhouetteEffect.render(guiGraphics, stack, cx, cy, size, z);
            return true;
        }

        FishtasticOutlineStyle style = Boolean.TRUE.equals(FishtasticGlintState.BLACK_OUTLINE_REQUESTED.get())
                ? FishtasticBlackOutlineEffect.STYLE
                : FishtasticOutlineStyle.of(ItemEffectManager.getEffectForItem(stack));
        if (style == null) {
            return false;
        }
        FishtasticItemOutlineAtlas atlas = FishtasticItemOutlineAtlas.getInstance();
        FishtasticItemOutlineAtlas.SlotView slot = atlas.requestSlot(stack, style);
        if (slot == null || atlas.outlineTextureId() < 0) {
            return false; // queued for next frame's bake, or atlas full
        }

        // Anything batched in the GUI buffer so far must land before this immediate draw.
        guiGraphics.flush();

        float half = 0.5F * size * SLOT_GUI_PX / 16.0F;
        float x0 = cx - half;
        float y0 = cy - half;
        float x1 = cx + half;
        float y1 = cy + half;
        Matrix4f pose = guiGraphics.pose().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        atlas.useLinearFiltering();
        RenderSystem.setShaderTexture(0, atlas.outlineTextureId());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // No depth write: the quad is mostly transparent and must not occlude the item drawn next.
        RenderSystem.depthMask(false);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(pose, x0, y0, z).setUv(slot.u0(), slot.v0());
        buffer.addVertex(pose, x0, y1, z).setUv(slot.u0(), slot.v1());
        buffer.addVertex(pose, x1, y1, z).setUv(slot.u1(), slot.v1());
        buffer.addVertex(pose, x1, y0, z).setUv(slot.u1(), slot.v0());
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        return false;
    }

    private FishtasticGuiOutlineRenderer() {}
}

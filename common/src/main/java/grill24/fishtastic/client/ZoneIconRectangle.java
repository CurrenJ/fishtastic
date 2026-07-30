package grill24.fishtastic.client;

import grill24.fishtastic.client.renderer.FishtasticTextureOutlineEffect;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * A square zone icon rendered as a direct texture blit plus a black outline pass — the fish
 * encyclopedia's zone condition rows reusing the same
 * {@link FishtasticTextureOutlineEffect} pipeline the fishing minigame's HUD zone stack draws
 * with (see {@code FishingMinigameAnimation#renderZoneIcon}), rather than going through the
 * item-atlas path {@link SelectableItemButton}/{@link SilhouetteItemButton} rely on — zone art
 * isn't an itemstack, just a standalone texture.
 */
public class ZoneIconRectangle extends UIElement<ZoneIconRectangle> {
    private final Identifier texture;
    private final int texturePx;

    public ZoneIconRectangle(float size, Identifier texture, int texturePx) {
        this.size.set(size, size);
        this.texture = texture;
        this.texturePx = texturePx;
    }

    @Override
    protected void onUpdate(float deltaTime) {
        // Static icon — nothing to update.
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        if (!(context instanceof MinecraftRenderContext mcContext)) return;
        GuiGraphicsExtractor graphics = mcContext.getGraphics();

        int w = (int) Math.ceil(size.x);
        int h = (int) Math.ceil(size.y);

        // Outline first, icon over it — matches the minigame's submission order.
        graphics.blit(FishtasticTextureOutlineEffect.getOrCreatePipeline(), texture,
                0, 0, 0f, 0f, w, h, texturePx, texturePx, texturePx, texturePx);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                0, 0, 0f, 0f, w, h, texturePx, texturePx, texturePx, texturePx);
    }

    @Override
    protected ZoneIconRectangle self() {
        return this;
    }

    @Override
    protected String getDefaultDebugName() {
        return "ZoneIconRectangle(texture=" + texture + ")";
    }
}

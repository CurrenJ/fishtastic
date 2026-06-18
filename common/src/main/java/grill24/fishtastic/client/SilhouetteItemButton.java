package grill24.fishtastic.client;

import grill24.fishtastic.client.renderer.FishtasticGlintState;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.components.ItemButton;
import net.minecraft.world.item.ItemStack;

/**
 * An {@link ItemButton} that renders a solid black silhouette of its item instead of its normal
 * texture, used on the fish encyclopedia home screen for never-caught fish.
 *
 * <p>This renders through the real itemstack renderer ({@code super.renderSelf}, which calls
 * {@code GuiGraphicsExtractor.item(...)}) rather than re-deriving the icon some other way. The
 * silhouette swap happens via a Mixin pipeline ({@code gui_item_silhouette.fsh}, wired through
 * {@code GuiRendererMixin}/{@code ItemModelResolverMixin}/{@code FishtasticGlintState}) that
 * intercepts the GUI item blit and replaces it with a shader sampling the same already-baked
 * atlas slot — i.e. the actual rendered appearance of this exact item, whatever its model,
 * with any animation frame correctly resolved, since the bake is vanilla's own. See
 * {@code docs/item-effect-rendering.md} for the full architecture (this reuses the same
 * GUI-outline plumbing already built for quality-tier item glows, just with a fill shader
 * instead of an edge-detect one).
 *
 * <p>{@link FishtasticGlintState#SILHOUETTE_REQUESTED} is a thread-local set immediately before
 * the render call and cleared immediately after: {@code graphics.item(...)} synchronously
 * resolves the item model within that same call, and {@code ItemModelResolverMixin} reads the
 * flag at that exact moment to tag the resulting render state. This mirrors the existing
 * {@code ACTIVE_EFFECT} thread-local pattern used for the glint render path.
 */
public class SilhouetteItemButton extends ItemButton {
    private boolean silhouette = false;

    public SilhouetteItemButton(ItemStack itemStack) {
        super(itemStack);
    }

    public SilhouetteItemButton setSilhouette(boolean silhouette) {
        this.silhouette = silhouette;
        return this;
    }

    public boolean isSilhouette() {
        return silhouette;
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        if (!silhouette) {
            super.renderSelf(context);
            return;
        }

        FishtasticGlintState.SILHOUETTE_REQUESTED.set(Boolean.TRUE);
        try {
            super.renderSelf(context);
        } finally {
            FishtasticGlintState.SILHOUETTE_REQUESTED.remove();
        }
    }

    @Override
    protected String getDefaultDebugName() {
        return "SilhouetteItemButton(item=" + getItemStack().getItem() + ", silhouette=" + silhouette + ")";
    }
}

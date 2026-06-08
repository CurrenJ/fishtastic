package grill24.fishtastic.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import grill24.fishtastic.client.renderer.FishtasticRenderPipelines;
import grill24.fishtastic.itemeffect.ItemEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects a custom outline blit for GUI items that have an {@link ItemEffect} with a
 * non-zero {@code outline_color}.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Both the normal item blit and the outline blit read from the same slot in the
 *       {@link GuiItemAtlas}.  The atlas already provides per-item-type pixel isolation,
 *       so no extra framebuffer is needed.</li>
 *   <li>The outline blit is submitted <em>before</em> the normal item blit so it is
 *       placed earlier in the {@link GuiRenderState} element list.  The sort step in
 *       {@link GuiRenderer} may reorder them, but the outline fragment shader discards
 *       opaque item pixels, so visual correctness is order-independent.</li>
 *   <li>The outline fragment shader ({@code gui_item_outline.fsh}) performs 8-connected
 *       neighbour sampling in atlas-texel space.  Transparent pixels that are adjacent
 *       to opaque item pixels receive the outline colour; all other pixels are
 *       discarded.</li>
 * </ol>
 *
 * <h3>Compatibility with glint effects</h3>
 * The outline blit is entirely independent of the glint/foil system.  Glint overlays
 * are written into the atlas slot alongside the item geometry during
 * {@link GuiItemAtlas#drawToSlot} (they share the same
 * {@code FeatureRenderDispatcher.renderAllFeatures()} call), so the outline shader
 * correctly detects their opaque pixels too.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Shadow
    private GuiRenderState renderState;

    /**
     * Before the normal item blit is added to the render state, check whether the item
     * has an outline effect and, if so, add an outline blit first.
     *
     * <p>Outline blit pixel coverage is disjoint from the normal item blit pixel coverage
     * (outline = transparent-but-adjacent; normal = opaque), so draw order between them
     * does not affect the final image.
     */
    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"))
    private void fishtastic$addOutlineBlit(
            GuiItemRenderState itemState,
            GuiItemAtlas.SlotView slotView,
            CallbackInfo ci) {

        ItemEffect effect = FishtasticGlintState.GUI_EFFECT_MAP.get(itemState.itemStackRenderState());

        if (effect == null || !effect.hasOutline()) {
            return;
        }

        boolean legendary = effect.outlinePinwheel();
        var pipeline = legendary
                ? FishtasticRenderPipelines.GUI_ITEM_OUTLINE_LEGENDARY
                : FishtasticRenderPipelines.GUI_ITEM_OUTLINE;

        int packedColor;
        if (legendary) {
            int guiScale = Math.clamp(
                    Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.guiScale,
                    1, 4);
            packedColor = effect.outlineLegendaryPackedColor(guiScale);
        } else {
            packedColor = effect.outlinePackedColor();
        }

        this.renderState.addBlitToCurrentLayer(new BlitRenderState(
                pipeline,
                TextureSetup.singleTexture(
                        slotView.textureView(),
                        RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                itemState.pose(),
                itemState.x(),
                itemState.y(),
                itemState.x() + 16,
                itemState.y() + 16,
                slotView.u0(),
                slotView.u1(),
                slotView.v0(),
                slotView.v1(),
                packedColor,
                itemState.scissorArea()
        ));
    }
}

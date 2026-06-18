package grill24.fishtastic.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import grill24.fishtastic.client.renderer.FishtasticSilhouetteEffect;
import grill24.fishtastic.itemeffect.ItemEffect;
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
 * <p>All outline params (color, falloff, opacity, width, animation) are stored in a
 * per-effect {@code GpuBuffer} and bound via {@code ItemOutlineParams} UBO through the
 * {@link RenderSystemMixin}.  The blit's {@code color} field now carries only the raw
 * ARGB outline color (no packing) and is unused by the shader — it exists for GPU-debugger
 * identification.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Shadow
    private GuiRenderState renderState;

    /**
     * Fish encyclopedia "never caught" silhouette: replaces the normal item blit entirely
     * (rather than adding an extra layer like the outline below) with a fill that samples the
     * exact same already-baked atlas slot — i.e. the real itemstack renderer's output for this
     * item, whatever its model — through {@code gui_item_silhouette.fsh}.
     */
    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"), cancellable = true)
    private void fishtastic$renderSilhouette(
            GuiItemRenderState itemState,
            GuiItemAtlas.SlotView slotView,
            CallbackInfo ci) {

        if (!FishtasticGlintState.GUI_SILHOUETTE_MAP.containsKey(itemState.itemStackRenderState())) {
            return;
        }

        RenderPipeline pipeline = FishtasticSilhouetteEffect.getOrCreatePipeline();

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
                0xFF000000,
                itemState.scissorArea()
        ));

        ci.cancel();
    }

    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"))
    private void fishtastic$addOutlineBlit(
            GuiItemRenderState itemState,
            GuiItemAtlas.SlotView slotView,
            CallbackInfo ci) {

        ItemEffect effect = FishtasticGlintState.GUI_EFFECT_MAP.get(itemState.itemStackRenderState());

        if (effect == null || !effect.hasOutline()) {
            return;
        }

        RenderPipeline pipeline = effect.getOrCreateOutlinePipeline();

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
                effect.outlineColor(),
                itemState.scissorArea()
        ));
    }
}

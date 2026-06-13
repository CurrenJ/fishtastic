package grill24.fishtastic.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import grill24.fishtastic.client.renderer.FishtasticOutlineUboRegistry;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Binds the per-effect outline params UBO for in-world outline draws.
 *
 * <p>This is the world-rendering counterpart of {@link GuiRendererExecuteDrawMixin}:
 * GUI blits execute through {@code GuiRenderer.executeDraw}, but everything submitted
 * through the level feature pipeline (including our outline quads, which arrive via
 * {@code submitCustomGeometry} → {@code BufferSource.endBatch}) is drawn by
 * {@code RenderType.draw}.  The UBO must be bound on the active {@link RenderPass}
 * between {@code setPipeline} and the draw call — wrapping the
 * {@code RenderSystem.bindDefaultUniforms(renderPass)} invocation is the minimal window
 * that has the render pass in hand without fragile local-variable capture.
 *
 * <p>{@link FishtasticOutlineUboRegistry#bind} is an identity-map miss for every vanilla
 * pipeline, so the per-draw overhead for non-outline render types is negligible.
 */
@Mixin(RenderType.class)
public abstract class RenderTypeMixin {

    @Redirect(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/blaze3d/systems/RenderPass;)V"))
    private void fishtastic$bindOutlineUbo(RenderPass renderPass) {
        RenderSystem.bindDefaultUniforms(renderPass);
        FishtasticOutlineUboRegistry.bind(((RenderType) (Object) this).pipeline(), renderPass);
    }
}

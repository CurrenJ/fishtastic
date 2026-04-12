package grill24.fishtastic.mixin;

import grill24.fishtastic.client.renderer.FishtasticGlintState;
import grill24.fishtastic.itemeffect.ItemEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the vanilla foil {@link RenderType} with a custom one from the matching
 * {@link ItemEffect} when rendering items that have a fishtastic quality-glow effect.
 *
 * <h3>Pipeline summary (26.1)</h3>
 * <ol>
 *   <li>{@code ItemModelResolver.updateForTopItem()} resolves the item into
 *       {@code ItemStackRenderState} layers and records the active effect in
 *       {@link FishtasticGlintState#SUBMIT_EFFECT_MAP} (via {@code ItemModelResolverMixin}
 *       and {@code ItemStackRenderStateMixin}).</li>
 *   <li>{@code ItemStackRenderState.submit()} pushes {@code ItemSubmit} records into
 *       a {@code SubmitNodeCollection}.  Each record's {@code quads()} field is the
 *       same {@code List} reference used as the identity key in the map above.</li>
 *   <li>{@link ItemFeatureRenderer#renderSolid}/{@code renderTranslucent} iterates
 *       the collected submits and calls the private {@code renderItem()} for each.</li>
 *   <li>Our {@code renderItem} HEAD inject looks up the quads in the map and stores
 *       the result in a thread-local so that the following static method can read it.</li>
 *   <li>{@link ItemFeatureRenderer#getFoilRenderType} is intercepted to return the
 *       custom {@link RenderType} instead of the vanilla {@code RenderTypes.glint()}
 *       family.</li>
 * </ol>
 */
@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {

    /**
     * Before each item is rendered, store the associated {@link ItemEffect} (if any)
     * in a thread-local so that {@link #fishtastic$useCustomFoilRenderType} can
     * access it without needing an {@code ItemStack} reference.
     */
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void fishtastic$setActiveEffect(
            MultiBufferSource.BufferSource bufferSource,
            OutlineBufferSource outlineBufferSource,
            SubmitNodeStorage.ItemSubmit submit,
            CallbackInfo ci) {
        FishtasticGlintState.ACTIVE_EFFECT.set(
                FishtasticGlintState.SUBMIT_EFFECT_MAP.get(submit.quads()));
    }

    /**
     * After each item is rendered, clear the thread-local to avoid accidental
     * carry-over into the next item.
     */
    @Inject(method = "renderItem", at = @At("RETURN"))
    private void fishtastic$clearActiveEffect(
            MultiBufferSource.BufferSource bufferSource,
            OutlineBufferSource outlineBufferSource,
            SubmitNodeStorage.ItemSubmit submit,
            CallbackInfo ci) {
        FishtasticGlintState.ACTIVE_EFFECT.remove();
    }

    /**
     * When a custom effect is active, return the effect's {@link RenderType} instead
     * of the vanilla glint render type.  The vanilla logic is preserved (transparent
     * vs. opaque glint, sheeted vs. entity) but the texture is replaced by the
     * effect's custom texture.
     *
     * <p>This method is {@code public static}, but it is only called from within
     * {@code ItemFeatureRenderer.getFoilBuffer()}, which is itself only called when
     * the current submit has a non-{@code NONE} foil type.  The thread-local is set
     * immediately before and cleared immediately after the {@code renderItem} call, so
     * there is no risk of the wrong effect leaking into unrelated items.
     */
    @Inject(method = "getFoilRenderType", at = @At("HEAD"), cancellable = true)
    private static void fishtastic$useCustomFoilRenderType(
            RenderType baseRenderType,
            boolean sheeted,
            CallbackInfoReturnable<RenderType> cir) {

        ItemEffect effect = FishtasticGlintState.ACTIVE_EFFECT.get();
        if (effect == null) return;

        // Replicate ItemFeatureRenderer.useTransparentGlint() logic
        boolean useTransparent =
                Minecraft.useShaderTransparency()
                        && baseRenderType.outputTarget() == OutputTarget.ITEM_ENTITY_TARGET;

        if (useTransparent) {
            cir.setReturnValue(effect.qualityGlowTranslucent());
        } else {
            cir.setReturnValue(sheeted ? effect.qualityGlow() : effect.entityQualityGlow());
        }
    }
}


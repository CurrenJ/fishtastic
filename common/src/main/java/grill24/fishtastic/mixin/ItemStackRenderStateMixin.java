package grill24.fishtastic.mixin;

import grill24.fishtastic.client.renderer.FishtasticGlintState;
import grill24.fishtastic.client.renderer.FishtasticItemStackRenderState;
import grill24.fishtastic.itemeffect.ItemEffect;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Extends {@link ItemStackRenderState} so that it can hold and propagate a
 * custom {@link ItemEffect} (the fishtastic quality-glow effect) through the
 * new 26.1 item-rendering pipeline.
 *
 * <p>The {@code LayerRenderState.quads} field is a stable {@code ArrayList}
 * reference that is allocated once per layer object and only ever cleared – never
 * replaced – so we can use it as a reliable identity key in
 * {@link FishtasticGlintState#SUBMIT_EFFECT_MAP}.
 */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements FishtasticItemStackRenderState {

    // These shadow fields give us access to the per-layer data needed to build
    // and clean up the identity map without touching any private LayerRenderState
    // fields directly (we only call the public prepareQuadList()).
    @Shadow
    private int activeLayerCount;
    @Shadow
    private ItemStackRenderState.LayerRenderState[] layers;

    /**
     * At the head of {@code clear()} the active-layer count still reflects the
     * previous frame's layers, so we can iterate them and remove stale entries
     * before the count is reset to 0.
     */
    @Inject(method = "clear", at = @At("HEAD"))
    private void fishtastic$onClear(CallbackInfo ci) {
        fishtastic$cleanupGlintEffects();
        FishtasticGlintState.GUI_EFFECT_MAP.remove((ItemStackRenderState)(Object)this);
    }

    @Override
    public void fishtastic$captureGlintEffects(@Nullable ItemEffect effect) {
        for (int i = 0; i < activeLayerCount; i++) {
            List<?> quads = layers[i].prepareQuadList();
            if (effect != null) {
                FishtasticGlintState.SUBMIT_EFFECT_MAP.put(quads, effect);
            } else {
                FishtasticGlintState.SUBMIT_EFFECT_MAP.remove(quads);
            }
        }
    }

    @Override
    public void fishtastic$cleanupGlintEffects() {
        for (int i = 0; i < activeLayerCount; i++) {
            FishtasticGlintState.SUBMIT_EFFECT_MAP.remove(layers[i].prepareQuadList());
        }
    }

    @Override
    public int fishtastic$getActiveLayerCount() {
        return activeLayerCount;
    }

    @Override
    public ItemStackRenderState.LayerRenderState fishtastic$getLayer(int index) {
        return layers[index];
    }

}


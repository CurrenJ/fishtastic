package grill24.fishtastic.client.renderer;

import grill24.fishtastic.itemeffect.ItemEffect;
import org.jspecify.annotations.Nullable;

/**
 * Mixed into {@code ItemStackRenderState} to carry a custom {@link ItemEffect}
 * through the 26.1 item-rendering pipeline.
 */
public interface FishtasticItemStackRenderState {

    /**
     * Associates every active layer's quads list with the given effect in
     * {@link FishtasticGlintState#SUBMIT_EFFECT_MAP}, or removes the association
     * when effect is {@code null}.
     */
    void fishtastic$captureGlintEffects(@Nullable ItemEffect effect);

    /**
     * Removes stale map entries for this render-state's active layers.
     * Called at the head of {@code ItemStackRenderState.clear()}.
     */
    void fishtastic$cleanupGlintEffects();
}


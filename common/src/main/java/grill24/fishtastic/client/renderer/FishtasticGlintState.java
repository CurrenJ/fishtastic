package grill24.fishtastic.client.renderer;

import grill24.fishtastic.itemeffect.ItemEffect;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Static holder for the state needed to route custom glint effects through the
 * new 26.1 item rendering pipeline:
 *
 *   ItemModelResolver.updateForTopItem  →  captures ItemEffect per quads-list identity
 *          ↓
 *   ItemStackRenderState.clear          →  removes stale entries from the map
 *          ↓
 *   ItemFeatureRenderer.renderItem      →  looks up effect, stores in thread-local
 *          ↓
 *   ItemFeatureRenderer.getFoilRenderType → reads thread-local, returns custom RenderType
 */
public final class FishtasticGlintState {

    /**
     * Maps a {@code LayerRenderState}'s quads list (by object identity) to the
     * {@link ItemEffect} that should be used when rendering the glint for that layer.
     *
     * <p>Key stability: the {@code List<BakedQuad>} inside every {@code LayerRenderState}
     * is allocated once in the constructor and reused (cleared but never replaced) across
     * frames, so the reference is a stable, frame-persistent identity key.
     *
     * <p>Populated in {@code ItemModelResolverMixin} at the tail of
     * {@code updateForTopItem()}; cleaned up by {@code ItemStackRenderStateMixin} at
     * the head of {@code ItemStackRenderState.clear()}.
     */
    public static final IdentityHashMap<List<?>, ItemEffect> SUBMIT_EFFECT_MAP = new IdentityHashMap<>();

    /**
     * Thread-local holding the active {@link ItemEffect} during a single
     * {@code ItemFeatureRenderer.renderItem()} call.  Set at HEAD, cleared at RETURN.
     */
    public static final ThreadLocal<ItemEffect> ACTIVE_EFFECT = new ThreadLocal<>();

    private FishtasticGlintState() {}
}


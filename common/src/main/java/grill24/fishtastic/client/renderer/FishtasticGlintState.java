package grill24.fishtastic.client.renderer;

/**
 * Thread-local flags the GUI sets around a single item draw to ask for a GUI item effect instead
 * of the plain item. Set immediately before {@code GuiGraphics.renderItem} (via gelatin's item
 * button) and cleared immediately after; {@link FishtasticGuiOutlineRenderer} reads them at the
 * head of that same call.
 *
 * <p>26.1.2's class of the same name also carries the identity maps and thread-locals that move an
 * item's effect across the render-state extract/submit split ({@code SUBMIT_EFFECT_MAP},
 * {@code GUI_EFFECT_MAP}, {@code WORLD_OUTLINE_MAP}, ...). 1.21.1 draws immediately with the
 * {@code ItemStack} in scope, so only the two request flags remain.
 */
public final class FishtasticGlintState {
    /** Draw the encyclopedia's never-caught silhouette in place of the item ({@link FishtasticSilhouetteEffect}). */
    public static final ThreadLocal<Boolean> SILHOUETTE_REQUESTED = new ThreadLocal<>();
    /** Add the gear readout's black edge outline around the item ({@link FishtasticBlackOutlineEffect}). */
    public static final ThreadLocal<Boolean> BLACK_OUTLINE_REQUESTED = new ThreadLocal<>();

    private FishtasticGlintState() {}
}

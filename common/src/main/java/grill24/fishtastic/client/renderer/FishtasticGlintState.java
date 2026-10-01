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

    /**
     * Draw the animated gold "look here" highlight around the item ({@link FishtasticHighlightEffect}):
     * steers the player toward compatible items while they hold a rod / bait / hook / charm on the
     * cursor, and drives the tutorial prompts. Set by {@code AbstractContainerScreenMixin} around each
     * container slot's render — same pattern as {@link #SILHOUETTE_REQUESTED} /
     * {@link #BLACK_OUTLINE_REQUESTED}. (26.1.2 also keeps a {@code GUI_HIGHLIGHT_MAP} to carry the flag
     * across the render-state split; 1.21.1 reads this flag at the draw itself.)
     */
    public static final ThreadLocal<Boolean> HIGHLIGHT_REQUESTED = new ThreadLocal<>();

    private FishtasticGlintState() {}
}

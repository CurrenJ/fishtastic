// PORT STUB: deleted in A5.4
package grill24.fishtastic.client.renderer;

/**
 * Stand-in for the GUI shader-effect flags (A5.4c). The GUI code sets and clears these around item
 * draws exactly as on 26.1, but nothing reads them until A5.4 brings the silhouette and black
 * outline effects back, so those items draw plainly for now.
 */
public final class FishtasticGlintState {
    public static final ThreadLocal<Boolean> SILHOUETTE_REQUESTED = new ThreadLocal<>();
    public static final ThreadLocal<Boolean> BLACK_OUTLINE_REQUESTED = new ThreadLocal<>();

    private FishtasticGlintState() {}
}

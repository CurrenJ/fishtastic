package grill24.fishtastic.client.encyclopedia.portrait;

import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.data.FishProfile;
import io.github.currenj.gelatinui.gui.UIScreen;
import org.joml.Vector2f;

/**
 * Facade the info page uses: one portrait's shared bubble model and its three gelatin elements.
 * All elements are positioned in plate-local coordinates (the plate's top-left is 0,0), so the
 * caller either adds them to a container located at the plate origin or calls
 * {@link #placeAt(float, float)}. Z-order the caller must keep:
 * {@code backLayer < its own fish icon layer < frontLayer < toggle}.
 */
public final class Portrait {
    public static final int W = PortraitBubbles.W;
    public static final int H = PortraitBubbles.H;
    public static final int FISH_SLOT_X = 46;
    public static final int FISH_SLOT_Y = 1;
    public static final int FISH_SLOT_SIZE = 64;

    private final PortraitBubbles model;
    private final PortraitBackLayer back;
    private final PortraitFrontLayer front;
    private final PortraitToggle toggle;

    /**
     * @param ui            the screen's UIScreen (tooltip host)
     * @param themeZone     the species' first listed zone
     * @param seed          reproducible seed, e.g. the species id's hash
     * @param uncaught      draw bubbles at 50% opacity
     * @param canvasScaleK  the page canvas's integer scale (tooltip content is drawn at this scale)
     */
    public Portrait(UIScreen ui, FishProfile.Zone themeZone, long seed, boolean uncaught, int canvasScaleK) {
        this(ui, PortraitTheme.forZone(themeZone), seed, uncaught, canvasScaleK);
    }

    public Portrait(UIScreen ui, PortraitTheme theme, long seed, boolean uncaught, int canvasScaleK) {
        this.model = new PortraitBubbles(theme, seed);
        model.setMuted(uncaught);
        model.setPaused(!FishtasticClientConfig.isEncyclopediaPortraitAnimations());
        model.warmStart(PortraitBubbles.WARMUP);
        model.onOpen();
        this.back = new PortraitBackLayer(model);
        this.front = new PortraitFrontLayer(model);
        this.toggle = new PortraitToggle(ui, model, canvasScaleK);
        toggle.setPosition(new Vector2f(PortraitToggle.X, PortraitToggle.Y));
    }

    public PortraitBackLayer backLayer() { return back; }
    public PortraitFrontLayer frontLayer() { return front; }
    public PortraitToggle toggle() { return toggle; }
    /** The shared model (tests, self-test). */
    public PortraitBubbles model() { return model; }

    /** Claim pressed: a rush of bubbles up the plate (skipped when paused or reduced effects). */
    public void claimBurst() {
        model.setReducedEffects(FishtasticClientConfig.isReducedCelebrationEffects());
        model.setPaused(!FishtasticClientConfig.isEncyclopediaPortraitAnimations());
        model.claimBurst();
    }

    /** Moves all three elements so the plate's top-left lands at (x, y) in their shared parent. */
    public void placeAt(float x, float y) {
        back.setPosition(new Vector2f(x, y));
        front.setPosition(new Vector2f(x, y));
        toggle.setPosition(new Vector2f(x + PortraitToggle.X, y + PortraitToggle.Y));
    }
}

package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import io.github.currenj.gelatinui.gui.DirtyFlag;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import net.minecraft.resources.Identifier;

/**
 * A slim two-layer progress bar (empty backdrop + progress-clipped fill) built from
 * fishtastic's own thin bar textures, used in the quest log in place of gelatin-ui's bulkier
 * default {@code SpriteProgressBar} so the bar reads as a slim accent rather than a UI element
 * competing with the row's name/reward text.
 */
public class ThinProgressBar extends UIElement<ThinProgressBar> {
    private static final Identifier TEXTURE_EMPTY = Fishtastic.id("textures/gui/thin_progress_bar_empty.png");
    private static final Identifier TEXTURE_FILLED = Fishtastic.id("textures/gui/thin_progress_bar_filled.png");
    // Both source files are a 64x64 canvas; the opaque bar art only occupies the top-left 64x5 region.
    private static final int SOURCE_FILE_SIZE = 64;
    private static final int SPRITE_WIDTH = 64;
    private static final int SPRITE_HEIGHT = 5;

    public static final float DEFAULT_WIDTH = 63f;
    public static final float DEFAULT_HEIGHT = 5f;

    private float targetProgress = 0f;
    private float displayedProgress = 0f;
    private static final float ANIMATION_SPEED = 5.0f;
    private boolean isAnimating = false;

    public ThinProgressBar() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public ThinProgressBar(float width, float height) {
        setSize(width, height);
    }

    /** Set the progress value (0.0 to 1.0) with smooth animation. */
    public ThinProgressBar progress(float progress) {
        this.targetProgress = Math.max(0f, Math.min(1f, progress));
        this.isAnimating = true;
        markDirty(DirtyFlag.CONTENT);
        return this;
    }

    /** Set the progress value immediately without animation — use when restoring state. */
    public ThinProgressBar progressImmediate(float progress) {
        this.targetProgress = Math.max(0f, Math.min(1f, progress));
        this.displayedProgress = this.targetProgress;
        this.isAnimating = false;
        markDirty(DirtyFlag.CONTENT);
        return this;
    }

    public float getProgress() {
        return displayedProgress;
    }

    @Override
    protected void onUpdate(float deltaTime) {
        if (isAnimating) {
            if (Math.abs(targetProgress - displayedProgress) < 0.01f) {
                displayedProgress = targetProgress;
                isAnimating = false;
            } else {
                displayedProgress += (targetProgress - displayedProgress) * ANIMATION_SPEED * deltaTime;
            }
        }
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        int w = (int) Math.ceil(size.x);
        int h = (int) Math.ceil(size.y);

        context.enableBlend();
        context.drawTexture(TEXTURE_EMPTY, 0, 0, w, h, 0, 0, SPRITE_WIDTH, SPRITE_HEIGHT, SOURCE_FILE_SIZE, SOURCE_FILE_SIZE);

        if (displayedProgress > 0f) {
            int fillSrcWidth = Math.max(1, Math.min(SPRITE_WIDTH, (int) Math.ceil(SPRITE_WIDTH * displayedProgress)));
            int fillDstWidth = Math.max(1, Math.min(w, (int) Math.ceil(w * displayedProgress)));
            context.drawTexture(TEXTURE_FILLED, 0, 0, fillDstWidth, h, 0, 0, fillSrcWidth, SPRITE_HEIGHT, SOURCE_FILE_SIZE, SOURCE_FILE_SIZE);
        }

        context.disableBlend();
    }

    @Override
    protected String getDefaultDebugName() {
        return "ThinProgressBar(progress=" + String.format("%.1f", displayedProgress) + ")";
    }

    @Override
    protected ThinProgressBar self() {
        return this;
    }
}

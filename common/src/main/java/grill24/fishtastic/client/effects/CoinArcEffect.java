package grill24.fishtastic.client.effects;

import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.animation.Easing;
import io.github.currenj.gelatinui.gui.effects.AbstractEffect;
import io.github.currenj.gelatinui.gui.effects.BlendMode;
import io.github.currenj.gelatinui.gui.effects.TransformDelta;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Carries an element from its spawn position to a target displacement along a floaty,
 * hopping arc, shrinking away near the end — used for the mini coins that fly from a
 * claimed quest toward the token balance display.
 * <p>
 * The translation is parametrized directly by normalized time (rather than integrated from
 * a velocity+gravity simulation), so the element is guaranteed to land exactly on the
 * configured displacement regardless of frame timing.
 */
public class CoinArcEffect extends AbstractEffect {
    private float displacementX = 0f;
    private float displacementY = 0f;
    private float arcHeight = 60f;
    private float wobbleAmplitude = 0f;
    private float wobbleFrequency = 2f;
    private float wobblePhase = 0f;
    private float shrinkStart = 0.7f; // normalized time at which the shrink-away begins
    private float minScale = 0.2f; // scale multiplier reached by the end of the effect

    public CoinArcEffect(String channel, int priority, float duration) {
        super(null, channel, priority, BlendMode.ADD, duration);
    }

    /** Set the straight-line target offset (local-space pixels) reached at t=1. */
    public CoinArcEffect setDisplacement(float dx, float dy) {
        this.displacementX = dx;
        this.displacementY = dy;
        return this;
    }

    /** Set the height of the upward hop layered on top of the straight-line path. */
    public CoinArcEffect setArcHeight(float height) {
        this.arcHeight = height;
        return this;
    }

    /** Set a decaying side-to-side wobble for a floaty, non-mechanical feel. */
    public CoinArcEffect setWobble(float amplitude, float frequency, float phase) {
        this.wobbleAmplitude = amplitude;
        this.wobbleFrequency = frequency;
        this.wobblePhase = phase;
        return this;
    }

    /** Set when (normalized time) the element starts shrinking, and the scale it shrinks to by t=1. */
    public CoinArcEffect setShrink(float shrinkStart, float minScale) {
        this.shrinkStart = shrinkStart;
        this.minScale = minScale;
        return this;
    }

    @Override
    protected TransformDelta calculateDelta(UIElement<?> element) {
        float t = getNormalizedTime();
        float easedT = Easing.EASE_OUT_CUBIC.ease(t);

        float wobble = wobbleAmplitude * (float) Math.sin(t * wobbleFrequency * Math.PI * 2 + wobblePhase) * (1f - t);
        float x = displacementX * easedT + wobble;
        float y = displacementY * easedT - arcHeight * (float) Math.sin(t * Math.PI);

        float scale = 1f;
        if (t > shrinkStart) {
            float shrinkT = (t - shrinkStart) / (1f - shrinkStart);
            scale = 1f + (minScale - 1f) * shrinkT;
        }

        return new TransformDelta(new Vector2f(x, y), scale, 1.0f, new Vector3f(0, 0, 0));
    }
}

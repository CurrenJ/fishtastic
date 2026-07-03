package grill24.fishtastic.client.effects;

import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.effects.AbstractEffect;
import io.github.currenj.gelatinui.gui.effects.BlendMode;
import io.github.currenj.gelatinui.gui.effects.TransformDelta;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Swings an element like a damped pendulum about its configured scale pivot (e.g.
 * {@code PivotMode.TOP_CENTER}, for something hanging from a pin), oscillating and
 * settling back to rest as the swing decays. Used for the shop's "item pops back onto
 * its pin" respawn feedback.
 * <p>
 * Uses the closed-form solution of a damped harmonic oscillator —
 * {@code angle(t) = startAngle * e^(-damping * t) * cos(2*pi*frequency * t)} — which is the
 * standard small-angle approximation of a real damped pendulum, rather than a hand-authored
 * easing curve.
 */
public class PendulumSwingEffect extends AbstractEffect {
    private float startAngle = 30f;  // degrees, initial displacement from rest
    private float frequency = 2.5f;  // swings per second
    private float damping = 3.0f;    // decay rate (higher settles faster)

    public PendulumSwingEffect(String channel, int priority, float duration) {
        super(null, channel, priority, BlendMode.ADD, duration);
    }

    public PendulumSwingEffect setStartAngle(float degrees) {
        this.startAngle = degrees;
        return this;
    }

    public PendulumSwingEffect setFrequency(float swingsPerSecond) {
        this.frequency = Math.max(0.01f, swingsPerSecond);
        return this;
    }

    public PendulumSwingEffect setDamping(float damping) {
        this.damping = Math.max(0f, damping);
        return this;
    }

    @Override
    protected TransformDelta calculateDelta(UIElement<?> element) {
        float omega = frequency * (float) (2 * Math.PI);
        float envelope = (float) Math.exp(-damping * elapsed);
        float angle = startAngle * envelope * (float) Math.cos(omega * elapsed);
        return new TransformDelta(new Vector2f(0, 0), 1.0f, 1.0f, new Vector3f(0, 0, angle));
    }
}

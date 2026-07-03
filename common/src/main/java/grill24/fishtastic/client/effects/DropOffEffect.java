package grill24.fishtastic.client.effects;

import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.effects.AbstractEffect;
import io.github.currenj.gelatinui.gui.effects.BlendMode;
import io.github.currenj.gelatinui.gui.effects.TransformDelta;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Drops an element downward with accelerating (gravity-like) motion — used for the shop's
 * "item falls away when purchased" feedback.
 */
public class DropOffEffect extends AbstractEffect {
    private float dropDistance = 300f; // total downward travel in the element's local space (pixels)
    private float rotation = 0f; // degrees of rotation accumulated by the end of the fall

    public DropOffEffect(String channel, int priority, float duration) {
        super(null, channel, priority, BlendMode.ADD, duration);
    }

    /**
     * Set the total downward distance travelled by the end of the effect (local-space pixels).
     */
    public DropOffEffect setDropDistance(float distance) {
        this.dropDistance = Math.max(0f, distance);
        return this;
    }

    /**
     * Set the rotation (degrees) accumulated over the fall, for a tumbling feel.
     */
    public DropOffEffect setRotation(float degrees) {
        this.rotation = degrees;
        return this;
    }

    @Override
    protected TransformDelta calculateDelta(UIElement<?> element) {
        float t = getNormalizedTime();
        // Ease-in quadratic: constant acceleration, matching gravity (y = 0.5 * g * t^2)
        float eased = t * t;
        float yOffset = dropDistance * eased;
        float currentRotation = rotation * t;
        return new TransformDelta(new Vector2f(0, yOffset), 1.0f, 1.0f, new Vector3f(0, 0, currentRotation));
    }
}

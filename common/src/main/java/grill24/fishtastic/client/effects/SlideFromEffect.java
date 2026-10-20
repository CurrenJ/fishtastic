package grill24.fishtastic.client.effects;

import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.animation.Easing;
import io.github.currenj.gelatinui.gui.effects.AbstractEffect;
import io.github.currenj.gelatinui.gui.effects.BlendMode;
import io.github.currenj.gelatinui.gui.effects.TransformDelta;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Starts an element displaced from its laid-out position and eases it home — used so quest
 * cards glide up into the gap left by a removed card instead of snapping.
 */
public class SlideFromEffect extends AbstractEffect {
    private final float startX;
    private final float startY;

    public SlideFromEffect(String channel, int priority, float duration, float startX, float startY) {
        super(null, channel, priority, BlendMode.ADD, duration);
        this.startX = startX;
        this.startY = startY;
    }

    @Override
    protected TransformDelta calculateDelta(UIElement<?> element) {
        float remaining = 1f - Easing.EASE_OUT_CUBIC.ease(getNormalizedTime());
        return new TransformDelta(new Vector2f(startX * remaining, startY * remaining), 1.0f, 1.0f, new Vector3f(0, 0, 0));
    }
}

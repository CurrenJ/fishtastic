package grill24.fishtastic.client;

import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIContainer;
import io.github.currenj.gelatinui.gui.UIEvent;

/**
 * A container that never asserts a position on its children — {@code performLayout()} is a
 * deliberate no-op, exactly like {@code RotatingItemRing}'s. Unlike {@code ManualContainer},
 * which calls the instant {@code setPosition} setter on every child during every layout pass
 * (see the fish encyclopedia implementation plan's critical architectural finding), this
 * container is safe to use as a parent for children that manage their own position via
 * {@code setTargetPosition}, e.g. an icon traveling from the fish disc into the info page.
 *
 * <p>Callers are responsible for positioning children themselves (typically once via
 * {@code setPosition}, or continuously via {@code setTargetPosition} for animated children).
 */
public class FreeformContainer extends UIContainer<FreeformContainer> {
    public FreeformContainer() {
    }

    @Override
    protected void performLayout() {
        // Intentionally empty: children position themselves.
    }

    @Override
    protected void onUpdate(float deltaTime) {
        // Intentionally empty: nothing to drive here.
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        // This container draws nothing itself.
    }

    @Override
    protected boolean onEvent(UIEvent event) {
        return false;
    }

    @Override
    protected FreeformContainer self() {
        return this;
    }
}

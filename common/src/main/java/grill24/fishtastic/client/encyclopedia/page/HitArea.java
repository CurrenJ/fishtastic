package grill24.fishtastic.client.encyclopedia.page;

import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIScreen;

/**
 * A transparent leaf stacked above drawn content. Hit-testing returns the deepest leaf under the
 * cursor, so interactive spots (buttons, tooltips) are separate elements above the static
 * {@link PageSurface} and any item icons they cover. Optionally outlines itself on hover.
 */
public final class HitArea extends UIElement<HitArea> {
    private final boolean outlineOnHover;
    private boolean hovered;

    public HitArea(int w, int h, boolean outlineOnHover) {
        this.size.set(w, h);
        this.outlineOnHover = outlineOnHover;
        onMouseEnter(e -> hovered = true);
        onMouseExit(e -> hovered = false);
    }

    public HitArea click(Runnable action) {
        onClick(e -> action.run());
        return this;
    }

    public HitArea tip(UIScreen ui, UIElement<?> tooltip) {
        tooltip(ui, tooltip);
        return this;
    }

    @Override
    protected void onUpdate(float deltaTime) {
    }

    @Override
    protected void renderSelf(IRenderContext c) {
        if (!hovered || !outlineOnHover) return;
        int w = (int) size.x, h = (int) size.y;
        int white = 0xFFFFFFFF;
        c.fill(0, 0, w, 1, white);
        c.fill(0, h - 1, w, h, white);
        c.fill(0, 1, 1, h - 1, white);
        c.fill(w - 1, 1, w, h - 1, white);
    }

    @Override
    protected HitArea self() {
        return this;
    }
}

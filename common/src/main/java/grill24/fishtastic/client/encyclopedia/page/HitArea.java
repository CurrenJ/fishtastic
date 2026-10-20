package grill24.fishtastic.client.encyclopedia.page;

import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIScreen;

/**
 * A transparent leaf stacked above drawn content. Hit-testing returns the deepest leaf under the
 * cursor, so interactive spots (buttons, tooltips) are separate elements above the static
 * {@link PageSurface} and any item icons they cover. Optionally outlines itself on hover.
 */
public final class HitArea extends UIElement<HitArea> implements grill24.fishtastic.client.Narrated {
    private String narration;
    private String label;
    private String tooltipText;
    private PageSurface surface;
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
        String text = ScaledTooltip.textOf(tooltip);
        if (!text.isBlank()) tooltipText = tooltipText == null ? text : tooltipText + ". " + text;
        return this;
    }

    /** Spoken name for the narrator; shown before any tooltip text. */
    public HitArea label(String label) {
        this.label = label;
        return this;
    }

    /** Lets the hotspot read the page text it sits on, which is what a sighted reader sees. */
    public HitArea bind(PageSurface surface) {
        this.surface = surface;
        return this;
    }

    /** Visible text first, then the hover tooltip as extra detail; never the tooltip alone when text exists. */
    @Override
    public String narration() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (label != null) parts.add(label);
        if (surface != null) {
            var pos = getTargetPosition();
            String visible = surface.textWithin(Math.round(pos.x), Math.round(pos.y), (int) size.x, (int) size.y);
            if (!visible.isBlank() && parts.stream().noneMatch(p -> p.contains(visible) || visible.contains(p))) {
                parts.add(visible);
            }
        }
        if (tooltipText != null && parts.stream().noneMatch(p -> p.equals(tooltipText))) parts.add(tooltipText);
        return parts.isEmpty() ? null : String.join(". ", parts);
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

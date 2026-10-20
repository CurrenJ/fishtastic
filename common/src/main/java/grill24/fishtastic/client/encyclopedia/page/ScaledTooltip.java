package grill24.fishtastic.client.encyclopedia.page;

import grill24.fishtastic.client.FreeformContainer;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A tooltip that matches the page's text size at any canvas scale (gelatin-spike rule 4): tooltips
 * are drawn in screen space outside the canvas scale, and {@code UIScreen} animates the tooltip
 * element's own scale from 0, so the element handed to gelatin is an unscaled wrapper and the
 * content inside it carries {@code k} and its own background (gelatin draws none).
 */
public final class ScaledTooltip {
    private static final int MAX_LINE_WIDTH = 170;

    private ScaledTooltip() {}

    /** One tooltip line and its colour. */
    public record Line(String text, int rgb) {}

    public static Line line(String text, int rgb) {
        return new Line(text, rgb);
    }

    /** Builds the wrapper for {@code lines} at canvas scale {@code k}; long lines wrap. */
    public static UIElement<?> of(int k, List<Line> lines) {
        List<Line> wrapped = new ArrayList<>();
        for (Line line : lines) {
            for (String part : PageSurface.wrap(line.text(), MAX_LINE_WIDTH)) wrapped.add(new Line(part, line.rgb()));
        }
        int w = 0;
        for (Line line : wrapped) w = Math.max(w, PageSurface.width(line.text()));
        w += 6;
        int h = wrapped.size() * 9 + 4;
        Body body = new Body(wrapped, w, h);
        body.setTargetScale(Math.max(1, k), false);
        FreeformContainer wrapper = new FreeformContainer();
        wrapper.addChild(body);
        wrapper.setSize(w * Math.max(1, k), h * Math.max(1, k));
        return wrapper;
    }

    public static UIElement<?> of(int k, Line... lines) {
        return of(k, List.of(lines));
    }

    /** Dark purple-bordered box, sized in canvas units; rendered at the canvas scale. */
    private static final class Body extends UIElement<Body> {
        private final List<Line> lines;

        Body(List<Line> lines, int w, int h) {
            this.lines = lines;
            this.size.set(w, h);
        }

        @Override
        protected void onUpdate(float deltaTime) {
        }

        @Override
        protected void renderSelf(IRenderContext c) {
            int w = (int) size.x;
            int h = (int) size.y;
            c.fill(0, 0, w, h, 0xF0100010);
            int border = 0xC85000FF;
            c.fill(0, 0, w, 1, border);
            c.fill(0, h - 1, w, h, border);
            c.fill(0, 1, 1, h - 1, border);
            c.fill(w - 1, 1, w, h - 1, border);
            for (int i = 0; i < lines.size(); i++) {
                c.drawString(lines.get(i).text(), 3, 3 + i * 9, 0xFF000000 | lines.get(i).rgb());
            }
        }

        @Override
        protected Body self() {
            return this;
        }
    }
}

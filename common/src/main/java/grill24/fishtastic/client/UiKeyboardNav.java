package grill24.fishtastic.client;

import io.github.currenj.gelatinui.gui.IUIElement;
import io.github.currenj.gelatinui.gui.UIContainer;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIScreen;
import io.github.currenj.gelatinui.gui.components.Label;
import io.github.currenj.gelatinui.gui.components.SpriteRectangle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.geom.Rectangle2D;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Keyboard focus and narrator support for gelatin-ui trees, which vanilla's focus system cannot
 * see. Every element with a click handler is a focus stop. Tab / Shift+Tab / arrows move focus,
 * Enter / Space activate (by synthesising a click at the element's centre, so the mouse and
 * keyboard paths run identical code), and the focused element is outlined and narrated.
 */
public final class UiKeyboardNav {
    private static final Field CLICK_ACTIONS = findClickActionsField();
    private static final int RING = 0xFFFFFF55;

    /** Lets a screen bring an element into a scrolled viewport before it is focused. */
    public interface Reveal {
        void reveal(IUIElement element);
    }

    private final List<IUIElement> stops = new ArrayList<>();
    private IUIElement focused;
    private int focusedIndex = -1;
    private boolean keyboardActive;
    private boolean narrate;
    private final Reveal reveal;

    public UiKeyboardNav(Reveal reveal) {
        this.reveal = reveal;
    }

    private static Field findClickActionsField() {
        try {
            Field f = UIElement.class.getDeclaredField("onClickActions");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private java.util.Map<String, String> glyphNames = java.util.Map.of();

    /** Spoken names for glyph-only controls such as ">" and "^", which a narrator would mangle. */
    public UiKeyboardNav glyphNames(java.util.Map<String, String> names) {
        glyphNames = names;
        return this;
    }

    private final java.util.Map<IUIElement, String> labels = new java.util.IdentityHashMap<>();

    /** Explicit spoken text for an element (e.g. "Today, selected"); cleared by {@link #resetLabels}. */
    public void label(IUIElement element, String text) {
        labels.put(element, text);
    }

    public void resetLabels() {
        labels.clear();
    }

    private String narrationOf(IUIElement e) {
        String explicit = labels.get(e);
        if (explicit != null) return explicit;
        if (!(e instanceof Narrated n)) return null;
        String s = n.narration();
        return s == null || s.isBlank() ? null : s;
    }

    private static boolean clickable(IUIElement e) {
        if (CLICK_ACTIONS == null || !(e instanceof UIElement<?>)) return false;
        try {
            return !((List<?>) CLICK_ACTIONS.get(e)).isEmpty();
        } catch (IllegalAccessException ex) {
            return false;
        }
    }

    private void collect(IUIElement e) {
        if (!e.isVisible()) return;
        if (clickable(e) || narrationOf(e) != null) stops.add(e);
        if (e instanceof UIContainer<?> c) {
            for (IUIElement child : new ArrayList<>(c.getChildren())) collect(child);
        }
    }

    /** Re-scan after every rebuild; keeps focus on the same ordinal stop where possible. */
    public void refresh(IUIElement root) {
        int keep = focusedIndex;
        stops.clear();
        if (root != null) collect(root);
        dropEchoes();
        if (keyboardActive && !stops.isEmpty()) {
            focusedIndex = Math.min(Math.max(keep, 0), stops.size() - 1);
            focused = stops.get(focusedIndex);
        } else {
            focused = null;
            focusedIndex = -1;
        }
    }

    public boolean keyPressed(KeyEvent event, UIScreen ui, IUIElement root) {
        int key = event.key();
        boolean forward = key == GLFW.GLFW_KEY_TAB && !event.hasShiftDown()
                || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_RIGHT;
        boolean back = key == GLFW.GLFW_KEY_TAB && event.hasShiftDown()
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_LEFT;
        if (forward || back) {
            refresh(root);
            if (stops.isEmpty()) return false;
            keyboardActive = true;
            int n = stops.size();
            focusedIndex = focusedIndex < 0 ? (forward ? 0 : n - 1)
                    : Math.floorMod(focusedIndex + (forward ? 1 : -1), n);
            focused = stops.get(focusedIndex);
            if (reveal != null) reveal.reveal(focused);
            narrate = true;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)
                && focused != null) {
            Rectangle2D b = focused.getBounds();
            ui.onMouseMove((int) b.getCenterX(), (int) b.getCenterY());
            ui.onMouseClick((int) b.getCenterX(), (int) b.getCenterY(), 0);
            ui.onMouseRelease((int) b.getCenterX(), (int) b.getCenterY(), 0);
            narrate = true;
            return true;
        }
        return false;
    }

    /** Mouse use (or a change of view) drops the ring and the stale stop list. */
    public void mouseUsed() {
        stops.clear();
        keyboardActive = false;
        focused = null;
        focusedIndex = -1;
    }

    public void renderFocus(GuiGraphicsExtractor g) {
        if (focused == null || !focused.isVisible()) return;
        Rectangle2D b = focused.getBounds();
        int x0 = (int) Math.floor(b.getMinX()) - 1;
        int y0 = (int) Math.floor(b.getMinY()) - 1;
        int x1 = (int) Math.ceil(b.getMaxX()) + 1;
        int y1 = (int) Math.ceil(b.getMaxY()) + 1;
        g.fill(x0, y0, x1, y0 + 1, RING);
        g.fill(x0, y1 - 1, x1, y1, RING);
        g.fill(x0, y0, x0 + 1, y1, RING);
        g.fill(x1 - 1, y0, x1, y1, RING);
    }

    public int stopCount() {
        return stops.size();
    }

    /** What the narrator would say for the focused stop, or null when nothing is focused. */
    public String focusedDescription() {
        return focused == null ? null : describe(focused);
    }

    /** True once after a focus change or activation, so the screen can trigger narration. */
    public boolean consumeNarrationRequest() {
        boolean r = narrate;
        narrate = false;
        return r;
    }

    public void narrate(NarrationElementOutput output) {
        if (focused == null) return;
        if (stops.size() > 1) {
            output.add(NarratedElementType.POSITION,
                    Component.translatable("narrator.position.screen", focusedIndex + 1, stops.size()));
        }
        output.add(NarratedElementType.TITLE, Component.literal(describe(focused)));
        output.add(NarratedElementType.USAGE, Component.translatable("narration.fishtastic.nav_usage"));
    }

    /**
     * An unlabelled stop that reads exactly like the one before it (a quest row's preview icon
     * borrowing the row's text, or two expand glyphs side by side) adds nothing for a listener,
     * so the echo is dropped. Explicitly labelled and Narrated stops are never dropped: two
     * undiscovered fish really are two different stops.
     */
    private void dropEchoes() {
        String previous = null;
        for (java.util.Iterator<IUIElement> it = stops.iterator(); it.hasNext(); ) {
            IUIElement stop = it.next();
            String text = describe(stop);
            boolean borrowed = narrationOf(stop) == null;
            if (borrowed && text.equals(previous)) it.remove();
            else previous = text;
        }
    }

    private static List<String> textOf(IUIElement e) {
        List<String> parts = new ArrayList<>();
        gatherText(e, parts);
        return parts;
    }

    private String describe(IUIElement e) {
        String own = narrationOf(e);
        if (own != null) return glyphNames.getOrDefault(own.strip(), own);
        List<String> parts = new ArrayList<>();
        gatherText(e, parts);
        // Icon-only hotspots usually sit beside the label that names them.
        if (parts.isEmpty() && e.getParent() != null) gatherText(e.getParent(), parts);
        // A glyph inside a larger description names a neighbouring control, which is its own stop.
        if (parts.size() > 1) parts.removeIf(part -> glyphNames.containsKey(part.strip()));
        parts.replaceAll(part -> glyphNames.getOrDefault(part.strip(), part));
        if (parts.isEmpty() && e instanceof UIElement<?> ue && ue.getDebugName() != null) {
            parts.add(ue.getDebugName());
        }
        return parts.isEmpty() ? Component.translatable("narration.fishtastic.unlabeled").getString()
                : String.join(", ", parts);
    }

    private static void gatherText(IUIElement e, List<String> out) {
        if (!e.isVisible()) return;
        String text = null;
        if (e instanceof Label l) text = l.getText();
        else if (e instanceof SpriteRectangle<?> s) text = s.getText();
        if (text != null && !text.isBlank() && !out.contains(text.strip())) out.add(text.strip());
        if (e instanceof UIContainer<?> c) {
            for (IUIElement child : c.getChildren()) gatherText(child, out);
        }
    }
}

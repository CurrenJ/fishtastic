package grill24.fishtastic.client.encyclopedia.page;

import grill24.fishtastic.Fishtastic;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import io.github.currenj.gelatinui.gui.components.SpriteRenderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * One non-interactive leaf that replays a list of draw commands in canvas-local pixels: rectangles,
 * outlines, shadowed text, pixel icons, textures and 9-slice panels. The info page's static look is
 * authored against the mockup renderer's own primitives (docs/encyclopedia-redesign/mockups/
 * render_mockups.py), so this mirrors that API one to one. Interactive parts (buttons, hover
 * tooltips) are separate elements stacked above it.
 */
public final class PageSurface extends UIElement<PageSurface> implements grill24.fishtastic.client.Narrated {
    private record Spoken(int y, int x, String text) {}

    /** Text drawn inside a rectangle of this surface, left to right, so a hotspot can read what it covers. */
    public String textWithin(int rx, int ry, int rw, int rh) {
        List<String> out = new ArrayList<>();
        spoken.stream().sorted(java.util.Comparator.comparingInt(Spoken::y).thenComparingInt(Spoken::x)).forEach(t -> {
            int cx = t.x() + width(t.text()) / 2;
            int cy = t.y() + 4;
            if (cx >= rx - 1 && cx <= rx + rw + 1 && cy >= ry - 1 && cy <= ry + rh + 1 && !out.contains(t.text())) {
                out.add(t.text());
            }
        });
        return String.join(" ", out);
    }
    private final List<Spoken> spoken = new ArrayList<>();

    /** Everything drawn as text, top to bottom then left to right, for the narrator. */
    @Override
    public String narration() {
        List<String> out = new ArrayList<>();
        spoken.stream().sorted(java.util.Comparator.comparingInt(Spoken::y).thenComparingInt(Spoken::x))
                .forEach(t -> { if (!out.contains(t.text())) out.add(t.text()); });
        return String.join(". ", out);
    }

    public static final Identifier PANEL = Fishtastic.id("textures/gui/generic_item_panel.png");
    public static final Identifier PANEL_GOLD = Fishtastic.id("textures/gui/generic_item_panel_gold_border.png");
    public static final Identifier PANEL_GREEN = Fishtastic.id("textures/gui/green_generic_item_panel_2.png");

    private interface Cmd {
        void draw(IRenderContext c);
    }

    private final List<Cmd> cmds = new ArrayList<>();

    public PageSurface(int w, int h) {
        this.size.set(w, h);
    }

    // ----- measuring (vanilla advance widths; the ink is one pixel narrower) -----

    public static int width(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    /** Cuts {@code s} to fit {@code maxW} ink pixels, ending in "..." when it had to be cut. */
    public static String truncate(String s, int maxW) {
        if (width(s) - 1 <= maxW) return s;
        while (!s.isEmpty() && width(s + "...") - 1 > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    /** Word-wraps {@code s} to {@code maxW}. */
    public static List<String> wrap(String s, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (width(trial) - 1 <= maxW || cur.length() == 0) {
                cur.setLength(0);
                cur.append(trial);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    // ----- commands -----

    private static int argb(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    public PageSurface rect(int x, int y, int w, int h, int rgb, int alpha) {
        if (w <= 0 || h <= 0) return this;
        int color = argb(rgb, alpha);
        cmds.add(c -> c.fill(x, y, x + w, y + h, color));
        return this;
    }

    public PageSurface rect(int x, int y, int w, int h, int rgb) {
        return rect(x, y, w, h, rgb, 255);
    }

    public PageSurface outline(int x, int y, int w, int h, int rgb, int alpha) {
        rect(x, y, w, 1, rgb, alpha);
        rect(x, y + h - 1, w, 1, rgb, alpha);
        rect(x, y + 1, 1, h - 2, rgb, alpha);
        rect(x + w - 1, y + 1, 1, h - 2, rgb, alpha);
        return this;
    }

    public PageSurface outline(int x, int y, int w, int h, int rgb) {
        return outline(x, y, w, h, rgb, 255);
    }

    /** Text with vanilla's quarter-brightness drop shadow. */
    public PageSurface text(int x, int y, String s, int rgb) {
        if (s.isEmpty()) return this;
        spoken.add(new Spoken(y, x, s));
        int shadow = 0xFF000000 | (((rgb >> 16) & 0xFF) / 4 << 16) | (((rgb >> 8) & 0xFF) / 4 << 8) | ((rgb & 0xFF) / 4);
        int color = argb(rgb, 255);
        cmds.add(c -> {
            c.drawString(s, x + 1, y + 1, shadow);
            c.drawString(s, x, y, color);
        });
        return this;
    }

    public PageSurface textCentered(int cx, int y, String s, int rgb) {
        return text(cx - (width(s) - 1) / 2, y, s, rgb);
    }

    public PageSurface textRight(int rx, int y, String s, int rgb) {
        return text(rx - width(s) + 1, y, s, rgb);
    }

    public PageSurface texture(Identifier texture, int x, int y, int w, int h, int texW, int texH) {
        cmds.add(c -> c.drawTexture(texture, x, y, w, h, 0, 0, texW, texH, texW, texH));
        return this;
    }

    /** The mod's 20x24, 4px-slice card panel, one source texel per logical pixel on the borders. */
    public PageSurface panel(Identifier texture, int x, int y, int w, int h) {
        SpriteData sprite = new SpriteData(texture).uv(0, 0, 20, 24).textureSize(20, 24)
                .renderMode(SpriteRenderMode.SLICE).slice(4, 4, 4, 4);
        cmds.add(c -> c.drawSprite(sprite, x, y, w, h));
        return this;
    }

    /** A pixel icon: one logical pixel per '#'; 'o' draws {@code dim} when it is non-negative. */
    public PageSurface icon(String[] rows, int x, int y, int rgb, int dim) {
        int color = argb(rgb, 255);
        int dimColor = dim < 0 ? 0 : argb(dim, 255);
        cmds.add(c -> {
            for (int j = 0; j < rows.length; j++) {
                for (int i = 0; i < rows[j].length(); i++) {
                    char ch = rows[j].charAt(i);
                    if (ch == '#') c.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    else if (ch == 'o' && dim >= 0) c.fill(x + i, y + j, x + i + 1, y + j + 1, dimColor);
                }
            }
        });
        return this;
    }

    public PageSurface icon(String[] rows, int x, int y, int rgb) {
        return icon(rows, x, y, rgb, -1);
    }

    /** A pixel icon drawn {@code px} screen pixels per '#', for callers that need it larger than the info page's 7x7. */
    public PageSurface icon(String[] rows, int x, int y, int rgb, int dim, int px) {
        int color = argb(rgb, 255);
        int dimColor = dim < 0 ? 0 : argb(dim, 255);
        cmds.add(c -> {
            for (int j = 0; j < rows.length; j++) {
                for (int i = 0; i < rows[j].length(); i++) {
                    char ch = rows[j].charAt(i);
                    if (ch == '#') c.fill(x + i * px, y + j * px, x + (i + 1) * px, y + (j + 1) * px, color);
                    else if (ch == 'o' && dim >= 0) c.fill(x + i * px, y + j * px, x + (i + 1) * px, y + (j + 1) * px, dimColor);
                }
            }
        });
        return this;
    }

    @Override
    protected void onUpdate(float deltaTime) {
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        context.enableBlend();
        for (Cmd cmd : cmds) cmd.draw(context);
    }

    @Override
    protected PageSurface self() {
        return this;
    }
}

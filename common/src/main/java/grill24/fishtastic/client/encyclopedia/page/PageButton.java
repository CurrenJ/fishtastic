package grill24.fishtastic.client.encyclopedia.page;

import grill24.fishtastic.util.Ids;
import io.github.currenj.gelatinui.gui.DirtyFlag;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.components.SpriteData;
import io.github.currenj.gelatinui.gui.components.SpriteRenderMode;
import net.minecraft.resources.ResourceLocation;

import java.awt.geom.Rectangle2D;

/** A vanilla-look button (the stock 200x20, 3px-slice widget sprite) with a shadowed centred label. */
public final class PageButton extends UIElement<PageButton> implements grill24.fishtastic.client.Narrated {
    private static final ResourceLocation BUTTON = Ids.withDefaultNamespace("textures/gui/widgets.png");
    // 1.20.1 has no gui/sprites atlas: the stock button lives in widgets.png (normal at v=66, hovered at v=86).
    private static final int BUTTON_V = 66;
    private static final int BUTTON_HOT_V = 86;

    private final String label;
    private boolean hovered;
    private int hitPadLeft;
    private int hitPadTop;
    private int hitPadRight;
    private int hitPadBottom;

    public PageButton(int w, int h, String label, Runnable action) {
        this.size.set(w, h);
        this.label = label;
        onMouseEnter(e -> hovered = true);
        onMouseExit(e -> hovered = false);
        onClick(e -> action.run());
    }

    @Override
    public String narration() {
        return label;
    }

    /**
     * Expands the interaction bounds without changing the button's rendered size.
     *
     * <p>This is useful for small edge-aligned controls whose visual sprite does not fill every
     * physical pixel after GUI scaling. Padding is expressed in the button's logical pixels.
     */
    public PageButton hitPadding(int left, int top, int right, int bottom) {
        hitPadLeft = Math.max(0, left);
        hitPadTop = Math.max(0, top);
        hitPadRight = Math.max(0, right);
        hitPadBottom = Math.max(0, bottom);
        markDirty(DirtyFlag.LAYOUT);
        return this;
    }

    @Override
    protected Rectangle2D calculateBounds() {
        Rectangle2D bounds = super.calculateBounds();
        float scale = getGlobalScale();
        return new Rectangle2D.Float(
                (float) bounds.getX() - hitPadLeft * scale,
                (float) bounds.getY() - hitPadTop * scale,
                (float) bounds.getWidth() + (hitPadLeft + hitPadRight) * scale,
                (float) bounds.getHeight() + (hitPadTop + hitPadBottom) * scale);
    }

    @Override
    protected void onUpdate(float deltaTime) {
    }

    @Override
    protected void renderSelf(IRenderContext c) {
        int w = (int) size.x, h = (int) size.y;
        SpriteData sprite = new SpriteData(BUTTON).uv(0, hovered ? BUTTON_HOT_V : BUTTON_V, 200, 20).textureSize(256, 256)
                .renderMode(SpriteRenderMode.SLICE).slice(3, 3, 3, 3);
        c.enableBlend();
        c.drawSprite(sprite, 0, 0, w, h);
        int tw = c.getStringWidth(label);
        int x = (w - (tw - 1)) / 2;
        int y = (h - 8) / 2;
        c.drawString(label, x + 1, y + 1, 0xFF3F3F3F);
        c.drawString(label, x, y, hovered ? 0xFFFFFFA0 : 0xFFFFFFFF);
    }

    @Override
    protected PageButton self() {
        return this;
    }
}

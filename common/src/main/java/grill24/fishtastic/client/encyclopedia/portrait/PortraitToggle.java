package grill24.fishtastic.client.encyclopedia.portrait;

import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.FreeformContainer;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The 9x9 pause/play button in the plate's top-right corner. Shows the action (pause bars while
 * playing, play triangle while paused); faint at rest, full contrast with a white outline on hover.
 * Flips the global portrait-animation preference. Its tooltip is an unscaled wrapper around a
 * background+text body drawn at the canvas scale k (gelatin-spike rule 4).
 */
public final class PortraitToggle extends UIElement<PortraitToggle> {
    public static final int SIZE = 9;
    /** Plate-local position of the button. */
    public static final int X = PortraitBubbles.W - 11;
    public static final int Y = 2;

    private static final String[] PAUSE_ICON = {"##.##", "##.##", "##.##", "##.##", "##.##"};
    private static final String[] PLAY_ICON = {".#...", ".##..", ".###.", ".##..", ".#..."};

    private final PortraitBubbles model;
    private final TooltipBody body;
    private final FreeformContainer tipWrapper;
    private final int canvasScale;
    private boolean hovered;

    PortraitToggle(UIScreen ui, PortraitBubbles model, int canvasScaleK) {
        this.model = model;
        this.canvasScale = Math.max(1, canvasScaleK);
        this.size.set(SIZE, SIZE);

        this.body = new TooltipBody();
        body.setTargetScale(canvasScale, false);
        this.tipWrapper = new FreeformContainer();
        tipWrapper.addChild(body);
        refreshTooltip();

        tooltip(ui, tipWrapper);
        onMouseEnter(e -> {
            hovered = true;
            refreshTooltip();
        });
        onMouseExit(e -> hovered = false);
        onClick(e -> {
            boolean animate = !FishtasticClientConfig.isEncyclopediaPortraitAnimations();
            FishtasticClientConfig.setEncyclopediaPortraitAnimations(animate);
            model.setPaused(!animate);
            refreshTooltip();
        });
    }

    /** The tooltip wrapper element (exposed for the self-test). */
    public UIElement<?> tooltipElement() {
        return tipWrapper;
    }

    /** The scaled content inside the tooltip wrapper (exposed for the self-test). */
    public UIElement<?> tooltipContent() {
        return body;
    }

    /** First tooltip line as currently shown (self-test). */
    public String tooltipLine1() {
        return body.line1;
    }

    private boolean playing() {
        return !model.paused();
    }

    private void refreshTooltip() {
        body.refresh(playing());
        tipWrapper.setSize(body.getSize().x * canvasScale, body.getSize().y * canvasScale);
    }

    @Override
    protected void onUpdate(float deltaTime) {
    }

    @Override
    protected void renderSelf(IRenderContext c) {
        c.fill(0, 0, SIZE, SIZE, hovered ? 0xAA000000 : 0x5A000000);
        if (hovered) {
            c.fill(0, 0, SIZE, 1, 0xFFFFFFFF);
            c.fill(0, SIZE - 1, SIZE, SIZE, 0xFFFFFFFF);
            c.fill(0, 1, 1, SIZE - 1, 0xFFFFFFFF);
            c.fill(SIZE - 1, 1, SIZE, SIZE - 1, 0xFFFFFFFF);
        }
        int colour = hovered ? 0xFFFFFFFF : PortraitRenderer.argb(0.6f, 0xD2DCE6);
        String[] icon = playing() ? PAUSE_ICON : PLAY_ICON;
        for (int j = 0; j < icon.length; j++) {
            for (int i = 0; i < icon[j].length(); i++) {
                if (icon[j].charAt(i) == '#') c.fill(2 + i, 2 + j, 3 + i, 3 + j, colour);
            }
        }
    }

    @Override
    protected PortraitToggle self() {
        return this;
    }

    /** Dark purple-bordered two-line box, sized in canvas units; rendered at the canvas scale. */
    static final class TooltipBody extends UIElement<TooltipBody> {
        private String line1 = "";
        private String line2 = "";

        void refresh(boolean playing) {
            var font = Minecraft.getInstance().font;
            line1 = Component.translatable(playing
                    ? "screen.fishtastic.encyclopedia.portrait.pause"
                    : "screen.fishtastic.encyclopedia.portrait.play").getString();
            line2 = Component.translatable("screen.fishtastic.encyclopedia.portrait.applies_all").getString();
            int w = Math.max(font.width(line1), font.width(line2)) + 6;
            setSize(w, 22);
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
            c.drawString(line1, 3, 3, 0xFFFFFFFF);
            c.drawString(line2, 3, 12, 0xFFAAAAAA);
        }

        @Override
        protected TooltipBody self() {
            return this;
        }
    }
}

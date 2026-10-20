package grill24.fishtastic.client.encyclopedia.portrait;

import grill24.fishtastic.client.FishtasticClientConfig;
import io.github.currenj.gelatinui.gui.DirtyFlag;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;

/**
 * Bottom of the portrait stack: the dithered plate texture plus back-layer bubbles. It is also the one
 * element that steps the shared {@link PortraitBubbles} model, with the real frame time, reading the
 * global animation preference every step so the toggle affects every portrait at once.
 */
public final class PortraitBackLayer extends UIElement<PortraitBackLayer> {
    private static volatile boolean manualStepping;

    /**
     * Self-test only: when true the layer stops stepping the model on its own, so the scene can
     * advance every portrait by an exact amount (PortraitBubbles.advance) between captures.
     */
    public static void setManualStepping(boolean manual) {
        manualStepping = manual;
    }

    private final PortraitBubbles model;

    PortraitBackLayer(PortraitBubbles model) {
        this.model = model;
        this.size.set(PortraitBubbles.W, PortraitBubbles.H);
    }

    @Override
    protected void onUpdate(float deltaTime) {
        model.setPaused(!FishtasticClientConfig.isEncyclopediaPortraitAnimations());
        model.setReducedEffects(FishtasticClientConfig.isReducedCelebrationEffects());
        if (!manualStepping) model.step(deltaTime);
        markDirty(DirtyFlag.CONTENT);   // keep being updated every frame
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        context.pushScissor(0, 0, PortraitBubbles.W, PortraitBubbles.H);
        context.drawTexture(model.theme().plateTexture, 0, 0, PortraitBubbles.W, PortraitBubbles.H,
                0, 0, PortraitBubbles.W, PortraitBubbles.H, PortraitBubbles.W, PortraitBubbles.H);
        context.popScissor();
        PortraitRenderer.drawBubbles(context, model, false);
    }

    @Override
    protected PortraitBackLayer self() {
        return this;
    }
}

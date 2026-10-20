package grill24.fishtastic.client.encyclopedia.portrait;

import grill24.fishtastic.client.FishtasticClientConfig;
import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import org.joml.Vector2f;

/**
 * Above the fish icon: front-layer bubbles, the 1px plate frame, and a transparent full-plate click
 * target that fires a plume (and the theme's pop sound) at the cursor.
 */
public final class PortraitFrontLayer extends UIElement<PortraitFrontLayer> {
    private static final int FRAME = 0x78000000;   // prototype: black, alpha 120

    private final PortraitBubbles model;

    PortraitFrontLayer(PortraitBubbles model) {
        this.model = model;
        this.size.set(PortraitBubbles.W, PortraitBubbles.H);
    }

    @Override
    protected void onUpdate(float deltaTime) {
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        PortraitRenderer.drawBubbles(context, model, true);
        int w = PortraitBubbles.W;
        int h = PortraitBubbles.H;
        context.fill(0, 0, w, 1, FRAME);
        context.fill(0, h - 1, w, h, FRAME);
        context.fill(0, 1, 1, h - 1, FRAME);
        context.fill(w - 1, 1, w, h - 1, FRAME);
    }

    @Override
    protected boolean onEvent(UIEvent event) {
        if (event.getType() == UIEvent.Type.CLICK) {
            Vector2f origin = getGlobalPosition();
            float scale = getGlobalScale();
            float px = (event.getMouseX() - origin.x) / scale;
            float py = (event.getMouseY() - origin.y) / scale;
            model.setPaused(!FishtasticClientConfig.isEncyclopediaPortraitAnimations());
            if (model.click(px, py)) playPop(model.theme().isLava());
            return true;
        }
        return super.onEvent(event);
    }

    private static void playPop(boolean lava) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        RandomSource rnd = RandomSource.create();
        if (lava) {
            float pitch = 0.8f + rnd.nextFloat() * 0.3f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LAVA_POP, pitch, 0.2f));
        } else {
            float pitch = 0.9f + rnd.nextFloat() * 0.4f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, pitch, 0.35f));
        }
    }

    @Override
    protected PortraitFrontLayer self() {
        return this;
    }
}

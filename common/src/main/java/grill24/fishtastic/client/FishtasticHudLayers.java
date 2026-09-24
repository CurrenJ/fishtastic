package grill24.fishtastic.client;

import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * PORT-ONLY: Fishtastic's three HUD layers — the tutorial overlay, the fishing minigame bar over
 * it, and the quest notifications on top — and where they're drawn on 1.21.1.
 *
 * <p>Owner decision (2026-09-24): while no screen is open, they're drawn after vanilla's toasts
 * ({@code mixin/GameRendererMixin}), so an advancement or recipe toast can't cover a quest
 * notification. That's deliberately different from 26.1.2, where toasts draw over the HUD. With a
 * screen open they stay in the HUD pass (the loaders' HUD hooks), under the screen, as on 26.1.2 —
 * drawing them after the toasts then would put them over the screen too.
 */
public final class FishtasticHudLayers {

    /** Whether the loaders' HUD hooks should draw the layers this frame (a screen is open). */
    public static boolean drawnInHudPass() {
        return Minecraft.getInstance().screen != null;
    }

    /** All three layers, in order (the 26.1 registration order: tutorial, bar, notifications). */
    public static void render(GuiGraphics graphics, float partialTick) {
        renderTutorial(graphics, partialTick);
        renderMinigameAndNotifications(graphics, partialTick);
    }

    public static void renderTutorial(GuiGraphics graphics, float partialTick) {
        TutorialClientHandler.render(graphics, partialTick);
    }

    public static void renderMinigameAndNotifications(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            ItemActivationAnimation animation = ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation();
            if (animation != null && animation.isActive()) {
                animation.render(mc, graphics, partialTick);
            }
        }
        QuestProgressNotificationManager.getInstance().render(graphics, partialTick);
    }

    private FishtasticHudLayers() {}
}

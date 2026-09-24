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

    /**
     * Self-test instrumentation ({@code client/selftest/RenderSelfTest}): the number of frames
     * {@link #renderAfterToasts} has drawn. At most one of the two paths can run in a frame -- the
     * mixin's guard is the negation of {@link #drawnInHudPass()} -- so a rising count while no
     * screen is open is proof that the layers went out in the toast pass, and a flat one while a
     * screen is open is proof the mixin kept out of it. It sits on {@link #renderAfterToasts} and
     * not on {@link #render} because Fabric's HUD hook calls {@link #render} directly: counting
     * there would count both paths on Fabric and only the toast pass on NeoForge, whose HUD event
     * is split pre/post and calls the two layer methods separately.
     */
    public static int toastPassFrames;

    /**
     * Self-test instrumentation: the number of times the loaders' HUD hooks asked
     * {@link #drawnInHudPass()} and got a yes, i.e. frames that drew the layers in the HUD pass
     * rather than the toast pass. NeoForge asks twice a frame (its HUD event is split pre/post),
     * Fabric once, so only "a rising count" means anything, not the exact number.
     */
    public static int hudPassCalls;

    /** Whether the loaders' HUD hooks should draw the layers this frame (a screen is open). */
    public static boolean drawnInHudPass() {
        if (Minecraft.getInstance().screen == null) {
            return false;
        }
        hudPassCalls++;
        return true;
    }

    /** All three layers, in order (the 26.1 registration order: tutorial, bar, notifications). */
    public static void render(GuiGraphics graphics, float partialTick) {
        renderTutorial(graphics, partialTick);
        renderMinigameAndNotifications(graphics, partialTick);
    }

    /**
     * The toast-pass entry point. Only {@code mixin/GameRendererMixin} calls this, after vanilla's
     * {@code ToastComponent}; the loaders' HUD hooks call {@link #render}, or the two layer methods
     * directly on NeoForge (its HUD event is split pre/post).
     */
    public static void renderAfterToasts(GuiGraphics graphics, float partialTick) {
        toastPassFrames++;
        render(graphics, partialTick);
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

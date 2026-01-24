package grill24.fishtastic.client;

import com.mojang.blaze3d.platform.InputConstants;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Manages custom key bindings for Fishtastic
 */
public class FishtasticKeyBinds {
    public static final String CATEGORY = "key.categories.fishtastic";

    public static KeyMapping fishingMinigameImpulse;

    /**
     * Initialize key mappings. Called during client initialization.
     */
    public static void init() {
        fishingMinigameImpulse = new KeyMapping(
            "key.fishtastic.fishing_minigame_impulse",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_SPACE, // Default to spacebar
            CATEGORY
        );
    }

    /**
     * Handle key presses. Should be called during client tick.
     */
    public static void handleKeyPress(Minecraft minecraft) {
        if (fishingMinigameImpulse.consumeClick()) {
            // Check if there's an active fishing minigame animation
            IGameRendererExtension gameRendererExt = (IGameRendererExtension) minecraft.gameRenderer;
            var activeAnimation = gameRendererExt.fishtastic$getActiveAnimation();
            if (activeAnimation instanceof FishingMinigameAnimation animation) {
                // Apply impulse when key is pressed
                animation.applyPlayerImpulse();
            }
        }
    }
}

package grill24.fishtastic.client;

import com.mojang.blaze3d.platform.InputConstants;
import grill24.fishtastic.network.RequestQuestLogPacket;
import grill24.fishtastic.network.ToggleEditModePacket;
import grill24.fishtastic.client.TutorialClientHandler;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * Manages custom key bindings for Fishtastic
 */
public class FishtasticKeyBinds {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        net.minecraft.resources.Identifier.fromNamespaceAndPath("fishtastic", "fishtastic")
    );

    public static KeyMapping fishingMinigameImpulse;
    public static KeyMapping openQuestLog;
    public static KeyMapping toggleFishTankEditMode;

    /**
     * Initialize key mappings. Called during client initialization.
     */
    public static void init() {
        fishingMinigameImpulse = new KeyMapping(
            "key.fishtastic.fishing_minigame_impulse",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_SPACE,
            CATEGORY
        );
        openQuestLog = new KeyMapping(
            "key.fishtastic.open_quest_log",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            CATEGORY
        );
        toggleFishTankEditMode = new KeyMapping(
            "key.fishtastic.toggle_fish_tank_edit_mode",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
        );
    }

    /**
     * Handle key presses. Should be called during client tick.
     */
    public static void handleKeyPress(Minecraft minecraft) {
        if (fishingMinigameImpulse.consumeClick()) {
            IGameRendererExtension gameRendererExt = (IGameRendererExtension) minecraft.gameRenderer;
            var activeAnimation = gameRendererExt.fishtastic$getActiveAnimation();
            if (activeAnimation instanceof FishingMinigameAnimation animation) {
                animation.applyPlayerImpulse();
                TutorialClientHandler.onMinigameImpulse();
            }
        }
        if (openQuestLog != null && openQuestLog.consumeClick()) {
            TutorialClientHandler.onQuestLogKeyPressed();
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new RequestQuestLogPacket()));
            }
        }
        if (toggleFishTankEditMode != null && toggleFishTankEditMode.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new ToggleEditModePacket()));
            }
        }
    }
}

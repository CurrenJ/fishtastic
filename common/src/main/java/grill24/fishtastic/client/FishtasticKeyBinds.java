package grill24.fishtastic.client;

import com.mojang.blaze3d.platform.InputConstants;
import grill24.fishtastic.network.NetworkApiSided;
import grill24.fishtastic.network.RequestFishEncyclopediaPacket;
import grill24.fishtastic.network.RequestLeaderboardScreenPacket;
import grill24.fishtastic.network.RequestQuestLogPacket;
import grill24.fishtastic.client.TutorialClientHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Manages custom key bindings for Fishtastic
 */
public class FishtasticKeyBinds {
    /** Category translation key; the same key 26.1's {@code KeyMapping.Category.register(fishtastic:fishtastic)} derives. */
    public static final String CATEGORY = "key.category.fishtastic.fishtastic";

    public static KeyMapping fishingMinigameImpulse;
    public static KeyMapping openQuestLog;
    public static KeyMapping openFishEncyclopedia;
    public static KeyMapping openLeaderboards;

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
        openFishEncyclopedia = new KeyMapping(
            "key.fishtastic.open_fish_encyclopedia",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            CATEGORY
        );
        openLeaderboards = new KeyMapping(
            "key.fishtastic.open_leaderboards",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_L,
            CATEGORY
        );
    }

    /**
     * Handle key presses. Should be called during client tick.
     */
    public static void handleKeyPress(Minecraft minecraft) {
        if (fishingMinigameImpulse.consumeClick()) {
            // Drain the click queue unconditionally so stale clicks don't fire after the
            // minigame ends. When a FishingMinigameAnimation is active its render() method
            // handles the impulse at frame rate via rising-edge key detection — applying it
            // here a second time would double the impulse on the same press.
            //
            // Deliberately does NOT auto-reel-in/start the minigame when no session is active:
            // this key defaults to SPACE, the same physical key as vanilla jump, so both
            // keybindings fire off one jump press. Auto-reeling here meant jumping near water
            // with a hook out silently discarded the bobber (confirmed via repro 2026-09-26).
            // Starting the minigame stays exclusively a right-click/use-item action, like vanilla.
        }
        if (openQuestLog != null && openQuestLog.consumeClick()) {
            TutorialClientHandler.onQuestLogKeyPressed();
            if (minecraft.player != null && minecraft.screen == null) {
                NetworkApiSided.sendToServer(new RequestQuestLogPacket(), RequestQuestLogPacket.STREAM_CODEC);
            }
        }
        if (openFishEncyclopedia != null && openFishEncyclopedia.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                NetworkApiSided.sendToServer(new RequestFishEncyclopediaPacket(true), RequestFishEncyclopediaPacket.STREAM_CODEC);
            }
        }
        if (openLeaderboards != null && openLeaderboards.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                NetworkApiSided.sendToServer(new RequestLeaderboardScreenPacket(), RequestLeaderboardScreenPacket.STREAM_CODEC);
            }
        }
    }
}

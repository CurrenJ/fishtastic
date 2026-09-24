package grill24.fishtastic.client;

import com.mojang.blaze3d.platform.InputConstants;
import grill24.fishtastic.network.RequestFishEncyclopediaPacket;
import grill24.fishtastic.network.RequestLeaderboardScreenPacket;
import grill24.fishtastic.network.RequestQuestLogPacket;
import grill24.fishtastic.client.TutorialClientHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;

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
            // minigame ends.  When a FishingMinigameAnimation is active its render() method
            // handles the impulse at frame rate via rising-edge key detection — applying it
            // here a second time would double the impulse on the same press.
            //
            // If no minigame is running yet but the player has a bobber out (e.g. it just
            // got a bite), let the impulse key also reel it in and start the minigame, the
            // same way right-click/use-item already does.
            Player player = minecraft.player;
            if (!FishingMinigameClientHandler.hasActiveSession() && player != null && player.fishing != null && minecraft.screen == null) {
                if (player.getMainHandItem().getItem() instanceof FishingRodItem) {
                    minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                } else if (player.getOffhandItem().getItem() instanceof FishingRodItem) {
                    minecraft.gameMode.useItem(player, InteractionHand.OFF_HAND);
                }
            }
        }
        if (openQuestLog != null && openQuestLog.consumeClick()) {
            TutorialClientHandler.onQuestLogKeyPressed();
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new RequestQuestLogPacket()));
            }
        }
        if (openFishEncyclopedia != null && openFishEncyclopedia.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new RequestFishEncyclopediaPacket(true)));
            }
        }
        if (openLeaderboards != null && openLeaderboards.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.player.connection.send(new ServerboundCustomPayloadPacket(new RequestLeaderboardScreenPacket()));
            }
        }
    }
}

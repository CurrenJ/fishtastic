package grill24.fishtastic.compat;

import grill24.fishtastic.Fishtastic;

import java.lang.reflect.Method;

import net.minecraft.server.level.ServerPlayer;

/**
 * Optional integration helper for opening Gelatin UI menus by id.
 *
 * This class never requires Gelatin UI at compile time. It detects the library
 * via reflection at runtime and falls back to no-op when it's not present.
 */
public final class GelatinOpenMenuCompat {
    private static final String GELATIN_MENUS_CLASS = "grill24.fishtastic.compat.GelatinMenus";
    private static final String GELATIN_MENUS_OPEN_METHOD = "openMenuById";

    private static boolean openMenu(ServerPlayer player, String id) {
        if (!CompatUtil.isGelatinUiPresent()) return false;
        try {
            // Resolve the declared parameter type, including for ServerPlayer subclasses.
            Method method = Class.forName(GELATIN_MENUS_CLASS)
                    .getMethod(GELATIN_MENUS_OPEN_METHOD, ServerPlayer.class, String.class);
            method.invoke(null, player, id);
            return true;
        } catch (ReflectiveOperationException e) {
            Fishtastic.LOGGER.warn("Could not open Fishtastic menu {}", id, e);
            return false;
        }
    }

    public static void openFishtasticMenu(ServerPlayer player) {
        boolean success = openMenu(player, "leaderboard");

        if (success) {
            Fishtastic.LOGGER.info("Opened Fishtastic menu for player {} via Gelatin UI.", player.getName().getString());
        } else {
            Fishtastic.LOGGER.warn("Failed to open Fishtastic menu; Gelatin UI not present.");
        }
    }

    public static void openQuestLogMenu(ServerPlayer player) {
        boolean success = openMenu(player, "quest_log");

        if (success) {
            grill24.fishtastic.tutorial.TutorialManager.onQuestLogOpened(player);
        } else {
            Fishtastic.LOGGER.warn("Failed to open Quest Log menu; Gelatin UI not present.");
        }
    }

    public static void openFishEncyclopediaMenu(ServerPlayer player) {
        boolean success = openMenu(player, "fish_encyclopedia");

        if (success) {
            var data = grill24.fishtastic.server.FishCatchSavedData.getOrCreate(player.level().getServer());
            grill24.fishtastic.network.QuestSyncPacket.sendToPlayer(player, data);
            grill24.fishtastic.tutorial.TutorialManager.onEncyclopediaOpened(player);
            grill24.fishtastic.tutorial.EncyclopediaTutorialManager.onEncyclopediaOpened(player);
            grill24.fishtastic.tutorial.CompanionTutorialManager.onEncyclopediaOpened(player);
        } else {
            Fishtastic.LOGGER.warn("Failed to open Fish Encyclopedia menu; Gelatin UI not present.");
        }
    }
}

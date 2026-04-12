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

    public static void openFishtasticMenu(ServerPlayer player) {
        boolean success = CompatUtil.invokeIfDependencyPresent(
            CompatUtil.GELATIN_UI_CLASS,
            GELATIN_MENUS_CLASS,
            GELATIN_MENUS_OPEN_METHOD,
            player,
            "leaderboard"
        );

        if (success) {
            Fishtastic.LOGGER.info("Opened Fishtastic menu for player {} via Gelatin UI.", player.getName().getString());
        } else {
            Fishtastic.LOGGER.warn("Failed to open Fishtastic menu; Gelatin UI not present.");
        }
    }
}

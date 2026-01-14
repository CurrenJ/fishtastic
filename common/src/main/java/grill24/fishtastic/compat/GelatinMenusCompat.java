package grill24.fishtastic.compat;

import grill24.fishtastic.Fishtastic;

/**
 * Optional integration helper for the Gelatin UI soft dependency - Menus.
 *
 * This class never requires Gelatin UI at compile time. It detects the library
 * via reflection at runtime and falls back to no-op when it's not present.
 * Use this class from common code to call optional UI menu features safely.
 */
public final class GelatinMenusCompat {
    private static final String GELATIN_MENUS_CLASS = "grill24.fishtastic.compat.GelatinMenus";
    private static final String GELATIN_MENUS_REGISTER_METHOD = "registerMenus";
    /**
     * Initialize detection. Safe to call multiple times.
     */
    public static synchronized void init() {
        boolean success = CompatUtil.invokeIfDependencyPresent(
            CompatUtil.GELATIN_UI_CLASS,
            GELATIN_MENUS_CLASS,
            GELATIN_MENUS_REGISTER_METHOD
        );

        if (success) {
            Fishtastic.LOGGER.info("Gelatin UI detected; optional menu features enabled.");
        } else {
            Fishtastic.LOGGER.info("Gelatin UI not found on classpath; optional menu features disabled.");
        }
    }
}

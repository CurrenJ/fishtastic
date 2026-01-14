package grill24.fishtastic.compat;

import grill24.fishtastic.Fishtastic;

import java.lang.reflect.Method;

/**
 * Optional integration helper for the Gelatin UI soft dependency - Screens.
 *
 * This class never requires Gelatin UI at compile time. It detects the library
 * via reflection at runtime and falls back to no-op when it's not present.
 * Use this class from common code to call optional UI screen features safely.
 */
public final class GelatinScreensCompat {
    private static final String GELATIN_SCREENS_CLASS = "grill24.fishtastic.compat.GelatinScreens";
    private static final String GELATIN_SCREENS_REGISTER_METHOD = "registerScreens";

    /**
     * Initialize detection. Safe to call multiple times.
     */
    public static synchronized void init() {
        boolean success = CompatUtil.invokeIfDependencyPresent(
                CompatUtil.GELATIN_UI_CLASS,
                GELATIN_SCREENS_CLASS,
                GELATIN_SCREENS_REGISTER_METHOD
        );

        if (success) {
            Fishtastic.LOGGER.info("Gelatin UI detected; optional screen features enabled.");
        } else {
            Fishtastic.LOGGER.info("Gelatin UI not found on classpath; optional screen features disabled.");
        }
    }
}

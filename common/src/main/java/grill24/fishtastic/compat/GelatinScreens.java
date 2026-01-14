package grill24.fishtastic.compat;

import grill24.fishtastic.Fishtastic;
import io.github.currenj.gelatinui.registration.menu.ScreenRegistrationEvent;

public class GelatinScreens {
    /**
     * On NeoForge, invoked by reflection in {@link GelatinScreensCompat} to force static initializer execution.
     * On Fabric, use this method as gelatinui:screen_registration entrypoint.
     * Only safe to load this class if Gelatin UI is present.
     */
    public static void registerScreens() {
        ScreenRegistrationEvent.registerListener(GelatinScreens::_registerScreens);
        Fishtastic.LOGGER.info("Registered {} screen registration listener with Gelatin UI.", Fishtastic.MOD_ID);
    }

    private static void _registerScreens(ScreenRegistrationEvent.ScreenRegistrar registrar) {
        // ----- REGISTER CUSTOM SCREENS USING REGISTRAR HERE -----
        Fishtastic.LOGGER.info("Registered {} screen with Gelatin UI.", Fishtastic.MOD_ID);
    }
}

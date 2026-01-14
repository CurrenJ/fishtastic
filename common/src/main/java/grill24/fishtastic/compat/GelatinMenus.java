package grill24.fishtastic.compat;

import io.github.currenj.gelatinui.GelatinUi;
import io.github.currenj.gelatinui.registration.menu.MenuRegistration;
import io.github.currenj.gelatinui.registration.menu.MenuRegistrationEvent;
import net.minecraft.server.level.ServerPlayer;

public class GelatinMenus {
    /**
     * On NeoForge, called via reflection in {@link GelatinMenusCompat} to pull menus from registered listeners and register them in-game.
     * On Fabric, use this method as gelatinui:menu_registration entrypoint.
     * Called during mod initialization using optional-dependency safe {@link GelatinMenusCompat}
     */
    public static void registerMenus() {
        MenuRegistrationEvent.registerListener(GelatinMenus::_registerMenus);
        GelatinUi.LOGGER.info("Registered Fishtastic menu registration listener with Gelatin UI.");
    }

    /**
     * Open a Gelatin UI menu by its registered id.
     * Safely call from {@link GelatinOpenMenuCompat}
     * @param serverPlayer
     * @param menuId
     */
    public static void openMenuById(ServerPlayer serverPlayer, String menuId) {
        MenuRegistration.openMenuById(serverPlayer, menuId);
    }

    private static void _registerMenus(MenuRegistrationEvent.MenuRegistrar registrar) {
        registrar.registerDebugMenu("fishtastic");
        GelatinUi.LOGGER.info("Registered Fishtastic menu with Gelatin UI.");
    }
}

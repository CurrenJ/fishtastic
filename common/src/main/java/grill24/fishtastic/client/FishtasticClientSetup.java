package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import grill24.fishtastic.menu.FishTankBrowserMenu;
import net.minecraft.world.inventory.MenuType;


public final class FishtasticClientSetup {
    /**
     * The Fish Tank Assembly menu type, cast down from the wildcard {@code Holder<MenuType<?>>}.
     * Registering the matching screen against vanilla {@code MenuScreens}/NeoForge's
     * {@code RegisterMenuScreensEvent} has to happen in loader-specific code — the private
     * {@code MenuScreens.ScreenConstructor} type isn't accessible from common-module code
     * even with an access widener (see {@code fishtastic.accesswidener}).
     */
    @SuppressWarnings("unchecked")
    public static MenuType<FishTankAssemblyMenu> fishTankAssemblyMenuType() {
        return (MenuType<FishTankAssemblyMenu>) (MenuType<?>) FishtasticMenuTypes.FISH_TANK_ASSEMBLY.value();
    }

    @SuppressWarnings("unchecked")
    public static MenuType<ElectricFishOrganizerMenu> electricFishOrganizerMenuType() {
        return (MenuType<ElectricFishOrganizerMenu>) (MenuType<?>) FishtasticMenuTypes.ELECTRIC_FISH_ORGANIZER.value();
    }

    @SuppressWarnings("unchecked")
    public static MenuType<FishTankBrowserMenu> fishTankBrowserMenuType() {
        return (MenuType<FishTankBrowserMenu>) (MenuType<?>) FishtasticMenuTypes.FISH_TANK_BROWSER.value();
    }
}



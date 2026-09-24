// PORT STUB: deleted in A5
package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import grill24.fishtastic.menu.FishTankBrowserMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Stand-in for the real class (A5: its item model types are rendering code). Keeps the three
 * menu-type accessors the A4 menu-screen registrations need, copied verbatim.
 */
public final class FishtasticClientSetup {
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

    private FishtasticClientSetup() {}
}

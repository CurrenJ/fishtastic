package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import grill24.fishtastic.menu.FishTankBrowserMenu;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.MenuType;

public class FishtasticMenuTypes {
    public static Holder<MenuType<?>> FISH_TANK_ASSEMBLY;
    public static Holder<MenuType<?>> ELECTRIC_FISH_ORGANIZER;
    public static Holder<MenuType<?>> FISH_TANK_BROWSER;

    public static void registerMenuTypes() {
        FISH_TANK_ASSEMBLY = RegistrationApiSided.getInstance().registerMenuType(
                "fish_tank_assembly",
                FishTankAssemblyMenu::new
        );
        ELECTRIC_FISH_ORGANIZER = RegistrationApiSided.getInstance().registerMenuType(
                "electric_fish_organizer",
                ElectricFishOrganizerMenu::new
        );
        FISH_TANK_BROWSER = RegistrationApiSided.getInstance().registerMenuType(
                "fish_tank_browser",
                FishTankBrowserMenu::new
        );
    }
}

package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.MenuType;

public class FishtasticMenuTypes {
    public static Holder<MenuType<?>> FISH_TANK_ASSEMBLY;

    public static void registerMenuTypes() {
        FISH_TANK_ASSEMBLY = RegistrationApiSided.getInstance().registerMenuType(
                "fish_tank_assembly",
                FishTankAssemblyMenu::new
        );
    }
}

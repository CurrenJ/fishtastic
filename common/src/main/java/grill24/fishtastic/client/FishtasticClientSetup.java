package grill24.fishtastic.client;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.client.renderer.CosmeticStructureItemModel;
import grill24.fishtastic.client.renderer.PileOfFishItemModel;
import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.inventory.MenuType;

import java.lang.reflect.Field;

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
    public static void registerItemModelTypes() {
        // ItemModels.ID_MAPPER is private — access widener works on Fabric but not
        // NeoForge, so we fall back to reflection when direct access fails.
        try {
            Field field = ItemModels.class.getDeclaredField("ID_MAPPER");
            field.setAccessible(true);
            ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> idMapper =
                    (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>>) field.get(null);
            idMapper.put(
                    Fishtastic.id("pile_of_fish_layers"),
                    PileOfFishItemModel.Unbaked.MAP_CODEC
            );
            idMapper.put(
                    Fishtastic.id("cosmetic_structure"),
                    CosmeticStructureItemModel.Unbaked.MAP_CODEC
            );
            Fishtastic.LOGGER.info("Registered pile_of_fish_layers and cosmetic_structure item model types.");
        } catch (ReflectiveOperationException e) {
            Fishtastic.LOGGER.error("Failed to register pile_of_fish_layers item model type!", e);
        }
    }
}



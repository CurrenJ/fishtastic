package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;

public class FishtasticCreativeTabs {
    public static Holder<CreativeModeTab> FISHTASTIC_TAB;

    public static void registerCreativeTabs() {
        FISHTASTIC_TAB = RegistrationApiSided.getInstance()
                .registerCreativeModeTab("fishtastic_tab", loc -> CreativeModeTab
                .builder(CreativeModeTab.Row.TOP, 7)
                .displayItems((parameters, output) -> {
                    // Fishing rods
                    output.accept(FishtasticItems.COPPER_FISHING_ROD.value());

                    // Fish items
                    output.accept(FishtasticItems.ACUTE_IASPIS.value());
                    output.accept(FishtasticItems.BLAZED_GRUB.value());
                    output.accept(FishtasticItems.BLUEGILL.value());
                    output.accept(FishtasticItems.FRIED_SHRIMP.value());
                    output.accept(FishtasticItems.FROZEN_GIANT_MANTA_RAY.value());
                    output.accept(FishtasticItems.GARDEN_EEL.value());
                    output.accept(FishtasticItems.GIANT_MANTA_RAY.value());
                    output.accept(FishtasticItems.GUMMY_WORMS.value());
                    output.accept(FishtasticItems.LIZARDFISH.value());
                    output.accept(FishtasticItems.LONGNOSE_GAR.value());
                    output.accept(FishtasticItems.MOLTEN_MOORISH_IDOL.value());
                    output.accept(FishtasticItems.MOORISH_IDOL.value());
                    output.accept(FishtasticItems.NEON_TETRA.value());
                    output.accept(FishtasticItems.NORTHERN_PIKE.value());
                    output.accept(FishtasticItems.OCEAN_SUNFISH.value());
                    output.accept(FishtasticItems.PARROTFISH.value());
                    output.accept(FishtasticItems.PORTUGUESE_MAN_O_WAR.value());
                    output.accept(FishtasticItems.RAINFORDIA.value());
                    output.accept(FishtasticItems.ROYAL_GARDEN_EEL.value());
                    output.accept(FishtasticItems.SHRIMP.value());
                    output.accept(FishtasticItems.STARFISH.value());
                    output.accept(FishtasticItems.WORMS.value());

                    // Fish tank
                    output.accept(FishtasticBlocks.FISH_TANK.value());

                    // Borderless stained glass (all colors)
                    for (DyeColor color : DyeColor.values()) {
                        output.accept(FishtasticBlocks.BORDERLESS_STAINED_GLASS.get(color).value());
                    }

                    // Clear stained glass (all colors)
                    for (DyeColor color : DyeColor.values()) {
                        output.accept(FishtasticBlocks.CLEAR_STAINED_GLASS.get(color).value());
                    }
                })
                .icon(() -> FishtasticItems.MOLTEN_MOORISH_IDOL.value().asItem().getDefaultInstance())
                .title(Component.literal("Fishtastic"))
                .build()
        );
    }

}

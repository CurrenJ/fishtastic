package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

public class FishtasticCreativeTabs {
    public static Holder<CreativeModeTab> FISHTASTIC_TAB;
    public static Holder<CreativeModeTab> FISHTASTIC_DECORATIONS_TAB;

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
                    output.accept(FishtasticItems.FLAPJACK_OCTOPUS.value());
                    output.accept(FishtasticItems.FRIED_SHRIMP.value());
                    output.accept(FishtasticItems.FROZEN_GIANT_MANTA_RAY.value());
                    output.accept(FishtasticItems.GARDEN_EEL.value());
                    output.accept(FishtasticItems.GIANT_MANTA_RAY.value());
                    output.accept(FishtasticItems.GLASS_SQUID.value());
                    output.accept(FishtasticItems.GUMMY_WORMS.value());
                    output.accept(FishtasticItems.LEAFY_SEA_DRAGON.value());
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
                    output.accept(FishtasticItems.FRESHWATER_BAIT.value());
                    output.accept(FishtasticItems.OCEAN_BAIT.value());
                    output.accept(FishtasticItems.PREDATOR_BAIT.value());
                    output.accept(FishtasticItems.DEEP_SEA_BAIT.value());
                    output.accept(FishtasticItems.HOOK.value());
                    output.accept(FishtasticItems.OLD_COPPER_HOOK.value());
                    output.accept(FishtasticItems.AMETHYST_CHARM.value());
                    output.accept(FishtasticItems.CRYSTAL_BALL_CHARM.value());
                    output.accept(FishtasticItems.FOUR_LEAF_CHARM.value());
                    output.accept(FishtasticItems.LUNA_CHARM.value());
                    output.accept(FishtasticItems.BANANA_CHARM.value());
                    output.accept(FishtasticItems.PILE_OF_COINS.value());
                    output.accept(FishtasticItems.QUEST_TOKEN.value());

                    // Trash
                    output.accept(FishtasticItems.SEA_GLASS.value());
                    output.accept(FishtasticItems.OLD_TIRE.value());
                    output.accept(FishtasticItems.PLASTIC_LITTER.value());

                    // Fish tank
                    output.accept(FishtasticBlocks.FISH_TANK.value());
                    output.accept(FishtasticBlocks.WORM_BIN.value());

                    // Undyed glass
                    output.accept(FishtasticBlocks.BORDERLESS_GLASS.value());
                    output.accept(FishtasticBlocks.CLEAR_GLASS.value());

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

        FISHTASTIC_DECORATIONS_TAB = RegistrationApiSided.getInstance()
                .registerCreativeModeTab("fishtastic_decorations_tab", loc -> CreativeModeTab
                .builder(CreativeModeTab.Row.TOP, 8)
                .displayItems((parameters, output) -> {
                    output.accept(FishtasticItems.COSMETIC_TREASURE_CHEST.value());
                    output.accept(FishtasticItems.COSMETIC_MOSSY_BOULDER.value());
                    output.accept(FishtasticItems.COSMETIC_PETALS.value());
                    output.accept(FishtasticItems.COSMETIC_SPRUCE_GAZEBO.value());
                    output.accept(FishtasticItems.COSMETIC_CATERPILLER.value());
                    output.accept(FishtasticItems.COSMETIC_LEAF_LITTER.value());
                    output.accept(FishtasticItems.COSMETIC_CORAL_REEF_1.value());
                    output.accept(FishtasticItems.COSMETIC_CORAL_REEF_2.value());
                    output.accept(FishtasticItems.COSMETIC_OAK_TREE.value());
                    output.accept(FishtasticItems.COSMETIC_BIRCH_TREE.value());
                    output.accept(FishtasticItems.COSMETIC_DYNAMIC_DUO.value());

                    // Lamp variants
                    for (Holder<Item> lamp : FishtasticItems.COSMETIC_LAMP.values()) {
                        output.accept(lamp.value());
                    }

                    // Fence arch variants (all wood types)
                    for (Holder<Item> fenceArch : FishtasticItems.COSMETIC_FENCE_ARCH.values()) {
                        output.accept(fenceArch.value());
                    }
                })
                .icon(() -> FishtasticItems.COSMETIC_TREASURE_CHEST.value().asItem().getDefaultInstance())
                .title(Component.literal("Fishtastic Decorations"))
                .build()
        );
    }

}

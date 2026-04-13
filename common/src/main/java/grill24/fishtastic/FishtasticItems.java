package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.item.PileOfFishItem;
import grill24.fishtastic.item.TestItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BundleContents;

public class FishtasticItems {
    /** Helper to create Item.Properties with the item ID already set (required in MC 26.1.2+). */
    private static Item.Properties props(Identifier loc) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, loc));
    }

    // ----- Items for Rendering Only -----
    public static Holder<Item> FISHING_MINIGAME_ROD_BACKGROUND;
    public static Holder<Item> FISHING_MINIGAME_BOBBER;
    public static Holder<Item> SPARKLE;
    public static Holder<Item> GENERIC_FISH;
    public static Holder<Item> REWARD_CHEST;

    // ----- Fishing Rods -----
    public static Holder<Item> COPPER_FISHING_ROD;

    // ----- Fish Items -----
    public static Holder<Item> ACUTE_IASPIS;

    // ----- Pile of Fish -----
    public static Holder<Item> PILE_OF_FISH;

    public static Holder<Item> BLAZED_GRUB;
    public static Holder<Item> BLUEGILL;
    public static Holder<Item> FRIED_SHRIMP;
    public static Holder<Item> FROZEN_GIANT_MANTA_RAY;
    public static Holder<Item> GARDEN_EEL;
    public static Holder<Item> GIANT_MANTA_RAY;
    public static Holder<Item> GUMMY_WORMS;
    public static Holder<Item> LIZARDFISH;
    public static Holder<Item> LONGNOSE_GAR;
    public static Holder<Item> MOLTEN_MOORISH_IDOL;
    public static Holder<Item> MOORISH_IDOL;
    public static Holder<Item> NEON_TETRA;
    public static Holder<Item> NORTHERN_PIKE;
    public static Holder<Item> OCEAN_SUNFISH;
    public static Holder<Item> PARROTFISH;
    public static Holder<Item> PORTUGUESE_MAN_O_WAR;
    public static Holder<Item> RAINFORDIA;
    public static Holder<Item> ROYAL_GARDEN_EEL;
    public static Holder<Item> SHRIMP;
    public static Holder<Item> STARFISH;
    public static Holder<Item> WORMS;

    public static void registerItems() {
        FISHING_MINIGAME_ROD_BACKGROUND = RegistrationApiSided.getInstance().registerItem("fishing_minigame_rod_background", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_BOBBER = RegistrationApiSided.getInstance().registerItem("fishing_minigame_bobber", loc -> new TestItem(props(loc).stacksTo(1)));
        SPARKLE = RegistrationApiSided.getInstance().registerItem("sparkle", loc -> new Item(props(loc).stacksTo(1)));
        GENERIC_FISH = RegistrationApiSided.getInstance().registerItem("generic_fish", loc -> new FishtasticFishItem(props(loc)));
        REWARD_CHEST = RegistrationApiSided.getInstance().registerItem("reward_chest", loc -> new Item(props(loc).stacksTo(1)));

        COPPER_FISHING_ROD = RegistrationApiSided.getInstance().registerItem("copper_fishing_rod", loc -> new CopperFishingRod(props(loc).durability(250)));
        ACUTE_IASPIS = RegistrationApiSided.getInstance().registerItem("acute_iaspis", loc -> FishtasticFishItem.create(props(loc), 45, 15));
        BLAZED_GRUB = RegistrationApiSided.getInstance().registerItem("blazed_grub", loc -> FishtasticFishItem.create(props(loc), 30, 10));
        BLUEGILL = RegistrationApiSided.getInstance().registerItem("bluegill", loc -> FishtasticFishItem.createDefault(props(loc)));
        FRIED_SHRIMP = RegistrationApiSided.getInstance().registerItem("fried_shrimp", loc -> FishtasticFishItem.create(props(loc), 25, 5));
        FROZEN_GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("frozen_giant_manta_ray", loc -> FishtasticFishItem.create(props(loc), 80, 15));
        GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("garden_eel", loc -> FishtasticFishItem.create(props(loc), 35, 10));
        GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("giant_manta_ray", loc -> FishtasticFishItem.create(props(loc), 75, 15));
        GUMMY_WORMS = RegistrationApiSided.getInstance().registerItem("gummy_worms", loc -> FishtasticFishItem.create(props(loc), 35, 5));
        LIZARDFISH = RegistrationApiSided.getInstance().registerItem("lizardfish", loc -> FishtasticFishItem.createDefault(props(loc)));
        LONGNOSE_GAR = RegistrationApiSided.getInstance().registerItem("longnose_gar", loc -> FishtasticFishItem.create(props(loc), 70, 15));
        MOLTEN_MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("molten_moorish_idol", loc -> FishtasticFishItem.create(props(loc), 55, 10));
        MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("moorish_idol", loc -> FishtasticFishItem.create(props(loc), 55, 15));
        NEON_TETRA = RegistrationApiSided.getInstance().registerItem("neon_tetra", loc -> FishtasticFishItem.create(props(loc), 30, 5));
        NORTHERN_PIKE = RegistrationApiSided.getInstance().registerItem("northern_pike", loc -> FishtasticFishItem.create(props(loc), 70, 15));
        OCEAN_SUNFISH = RegistrationApiSided.getInstance().registerItem("ocean_sunfish", loc -> FishtasticFishItem.create(props(loc), 90, 20));
        PARROTFISH = RegistrationApiSided.getInstance().registerItem("parrotfish", loc -> FishtasticFishItem.createDefault(props(loc)));
        PORTUGUESE_MAN_O_WAR = RegistrationApiSided.getInstance().registerItem("portuguese_man_o_war", loc -> FishtasticFishItem.create(props(loc), 40, 10));
        RAINFORDIA = RegistrationApiSided.getInstance().registerItem("rainfordia", loc -> FishtasticFishItem.createDefault(props(loc)));
        ROYAL_GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("royal_garden_eel", loc -> FishtasticFishItem.create(props(loc), 40, 10));
        SHRIMP = RegistrationApiSided.getInstance().registerItem("shrimp", loc -> FishtasticFishItem.create(props(loc), 25, 5));
        STARFISH = RegistrationApiSided.getInstance().registerItem("starfish", loc -> FishtasticFishItem.create(props(loc), 40, 10));
        WORMS = RegistrationApiSided.getInstance().registerItem("worms", loc -> FishtasticFishItem.create(props(loc), 35, 5));

        PILE_OF_FISH = RegistrationApiSided.getInstance().registerItem("pile_of_fish",
                loc -> new PileOfFishItem(
                        props(loc)
                                .stacksTo(1)
                                .component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                )
        );
    }
}

package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.item.TestItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;

public class FishtasticItems {
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
        FISHING_MINIGAME_ROD_BACKGROUND = RegistrationApiSided.getInstance().registerItem("fishing_minigame_rod_background", loc -> new TestItem(new Item.Properties().stacksTo(1)));
        FISHING_MINIGAME_BOBBER = RegistrationApiSided.getInstance().registerItem("fishing_minigame_bobber", loc -> new TestItem(new Item.Properties().stacksTo(1)));
        SPARKLE = RegistrationApiSided.getInstance().registerItem("sparkle", loc -> new Item(new Item.Properties().stacksTo(1)));
        GENERIC_FISH = RegistrationApiSided.getInstance().registerItem("generic_fish", loc -> new FishtasticFishItem(new Item.Properties()));
        REWARD_CHEST = RegistrationApiSided.getInstance().registerItem("reward_chest", loc -> new Item(new Item.Properties().stacksTo(1)));

        COPPER_FISHING_ROD = RegistrationApiSided.getInstance().registerItem("copper_fishing_rod", loc -> new CopperFishingRod(new Item.Properties().durability(250)));
        ACUTE_IASPIS = RegistrationApiSided.getInstance().registerItem("acute_iaspis", loc -> FishtasticFishItem.create(45, 15));
        BLAZED_GRUB = RegistrationApiSided.getInstance().registerItem("blazed_grub", loc -> FishtasticFishItem.create(30, 10));
        BLUEGILL = RegistrationApiSided.getInstance().registerItem("bluegill", FishtasticFishItem::createDefault);
        FRIED_SHRIMP = RegistrationApiSided.getInstance().registerItem("fried_shrimp", loc -> FishtasticFishItem.create(25, 5));
        FROZEN_GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("frozen_giant_manta_ray", loc -> FishtasticFishItem.create(80, 15));
        GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("garden_eel", loc -> FishtasticFishItem.create(35, 10));
        GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("giant_manta_ray", loc -> FishtasticFishItem.create(75, 15));
        GUMMY_WORMS = RegistrationApiSided.getInstance().registerItem("gummy_worms", loc -> FishtasticFishItem.create(35, 5));
        LIZARDFISH = RegistrationApiSided.getInstance().registerItem("lizardfish", FishtasticFishItem::createDefault);
        LONGNOSE_GAR = RegistrationApiSided.getInstance().registerItem("longnose_gar", loc -> FishtasticFishItem.create(70, 15));
        MOLTEN_MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("molten_moorish_idol", loc -> FishtasticFishItem.create(55, 10));
        MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("moorish_idol", loc -> FishtasticFishItem.create(55, 15));
        NEON_TETRA = RegistrationApiSided.getInstance().registerItem("neon_tetra", loc -> FishtasticFishItem.create(30, 5));
        NORTHERN_PIKE = RegistrationApiSided.getInstance().registerItem("northern_pike", loc -> FishtasticFishItem.create(70, 15));
        OCEAN_SUNFISH = RegistrationApiSided.getInstance().registerItem("ocean_sunfish", loc -> FishtasticFishItem.create(90, 20));
        PARROTFISH = RegistrationApiSided.getInstance().registerItem("parrotfish", FishtasticFishItem::createDefault);
        PORTUGUESE_MAN_O_WAR = RegistrationApiSided.getInstance().registerItem("portuguese_man_o_war", loc -> FishtasticFishItem.create(40, 10));
        RAINFORDIA = RegistrationApiSided.getInstance().registerItem("rainfordia", FishtasticFishItem::createDefault);
        ROYAL_GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("royal_garden_eel", loc -> FishtasticFishItem.create(40, 10));
        SHRIMP = RegistrationApiSided.getInstance().registerItem("shrimp", loc -> FishtasticFishItem.create(25, 5));
        STARFISH = RegistrationApiSided.getInstance().registerItem("starfish", loc -> FishtasticFishItem.create(40, 10));
        WORMS = RegistrationApiSided.getInstance().registerItem("worms", loc -> FishtasticFishItem.create(35, 5));
    }
}

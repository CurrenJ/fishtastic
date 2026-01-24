package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishtasticFish;
import grill24.fishtastic.item.TestItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.FishingRodItem;
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
        GENERIC_FISH = RegistrationApiSided.getInstance().registerItem("generic_fish", loc -> new FishtasticFish(new Item.Properties()));
        REWARD_CHEST = RegistrationApiSided.getInstance().registerItem("reward_chest", loc -> new TestItem(new Item.Properties().stacksTo(1)));

        COPPER_FISHING_ROD = RegistrationApiSided.getInstance().registerItem("copper_fishing_rod", loc -> new CopperFishingRod(new Item.Properties().durability(250)));

        BLAZED_GRUB = RegistrationApiSided.getInstance().registerItem("blazed_grub", loc -> new FishtasticFish(new Item.Properties()));
        BLUEGILL = RegistrationApiSided.getInstance().registerItem("bluegill", loc -> new FishtasticFish(new Item.Properties()));
        FRIED_SHRIMP = RegistrationApiSided.getInstance().registerItem("fried_shrimp", loc -> new FishtasticFish(new Item.Properties()));
        FROZEN_GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("frozen_giant_manta_ray", loc -> new FishtasticFish(new Item.Properties()));
        GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("garden_eel", loc -> new FishtasticFish(new Item.Properties()));
        GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("giant_manta_ray", loc -> new FishtasticFish(new Item.Properties()));
        GUMMY_WORMS = RegistrationApiSided.getInstance().registerItem("gummy_worms", loc -> new FishtasticFish(new Item.Properties()));
        LIZARDFISH = RegistrationApiSided.getInstance().registerItem("lizardfish", loc -> new FishtasticFish(new Item.Properties()));
        LONGNOSE_GAR = RegistrationApiSided.getInstance().registerItem("longnose_gar", loc -> new FishtasticFish(new Item.Properties()));
        MOLTEN_MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("molten_moorish_idol", loc -> new FishtasticFish(new Item.Properties()));
        MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("moorish_idol", loc -> new FishtasticFish(new Item.Properties()));
        NEON_TETRA = RegistrationApiSided.getInstance().registerItem("neon_tetra", loc -> new FishtasticFish(new Item.Properties()));
        NORTHERN_PIKE = RegistrationApiSided.getInstance().registerItem("northern_pike", loc -> new FishtasticFish(new Item.Properties()));
        OCEAN_SUNFISH = RegistrationApiSided.getInstance().registerItem("ocean_sunfish", loc -> new FishtasticFish(new Item.Properties()));
        PARROTFISH = RegistrationApiSided.getInstance().registerItem("parrotfish", loc -> new FishtasticFish(new Item.Properties()));
        PORTUGUESE_MAN_O_WAR = RegistrationApiSided.getInstance().registerItem("portuguese_man_o_war", loc -> new FishtasticFish(new Item.Properties()));
        RAINFORDIA = RegistrationApiSided.getInstance().registerItem("rainfordia", loc -> new FishtasticFish(new Item.Properties()));
        ROYAL_GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("royal_garden_eel", loc -> new FishtasticFish(new Item.Properties()));
        SHRIMP = RegistrationApiSided.getInstance().registerItem("shrimp", loc -> new FishtasticFish(new Item.Properties()));
        STARFISH = RegistrationApiSided.getInstance().registerItem("starfish", loc -> new FishtasticFish(new Item.Properties()));
        WORMS = RegistrationApiSided.getInstance().registerItem("worms", loc -> new FishtasticFish(new Item.Properties()));
    }
}

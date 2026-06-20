package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.RodBaitContents;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.item.PileOfFishItem;
import grill24.fishtastic.item.TestItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BundleContents;

import java.util.List;
import java.util.Optional;

public class FishtasticItems {
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

    // ----- Bait Items -----
    public static Holder<Item> BLAZED_GRUB;
    public static Holder<Item> GUMMY_WORMS;
    public static Holder<Item> WORMS;
    public static Holder<Item> FRESHWATER_BAIT;
    public static Holder<Item> OCEAN_BAIT;
    public static Holder<Item> PREDATOR_BAIT;
    public static Holder<Item> DEEP_SEA_BAIT;

    // ----- Quest Items -----
    public static Holder<Item> QUEST_TOKEN;

    // ----- Trash Items -----
    public static Holder<Item> SEA_GLASS;
    public static Holder<Item> OLD_TIRE;
    public static Holder<Item> PLASTIC_LITTER;

    // ----- Fish Tank Cosmetics -----
    public static Holder<Item> COSMETIC_TREASURE_CHEST;
    public static Holder<Item> COSMETIC_SEA_LANTERN;

    // ----- Fish Items -----
    public static Holder<Item> BLUEGILL;
    public static Holder<Item> FRIED_SHRIMP;
    public static Holder<Item> FROZEN_GIANT_MANTA_RAY;
    public static Holder<Item> GARDEN_EEL;
    public static Holder<Item> GIANT_MANTA_RAY;
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

    public static void registerItems() {
        FISHING_MINIGAME_ROD_BACKGROUND = RegistrationApiSided.getInstance().registerItem("fishing_minigame_rod_background", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_BOBBER = RegistrationApiSided.getInstance().registerItem("fishing_minigame_bobber", loc -> new TestItem(props(loc).stacksTo(1)));
        SPARKLE = RegistrationApiSided.getInstance().registerItem("sparkle", loc -> new Item(props(loc).stacksTo(1)));
        GENERIC_FISH = RegistrationApiSided.getInstance().registerItem("generic_fish", loc -> new FishtasticFishItem(props(loc)));
        REWARD_CHEST = RegistrationApiSided.getInstance().registerItem("reward_chest", loc -> new Item(props(loc).stacksTo(1)));

        COPPER_FISHING_ROD = RegistrationApiSided.getInstance().registerItem("copper_fishing_rod",
                loc -> new CopperFishingRod(props(loc).durability(250)
                        .component(FishtasticDataComponents.ROD_BAIT_CONTENTS.value(), RodBaitContents.EMPTY)));

        // Fish items — size/weight now defined in fish_profile data entries
        ACUTE_IASPIS = RegistrationApiSided.getInstance().registerItem("acute_iaspis", loc -> new FishtasticFishItem(props(loc)));
        BLUEGILL = RegistrationApiSided.getInstance().registerItem("bluegill", loc -> new FishtasticFishItem(props(loc)));
        FRIED_SHRIMP = RegistrationApiSided.getInstance().registerItem("fried_shrimp", loc -> new FishtasticFishItem(props(loc)));
        FROZEN_GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("frozen_giant_manta_ray", loc -> new FishtasticFishItem(props(loc)));
        GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("garden_eel", loc -> new FishtasticFishItem(props(loc)));
        GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("giant_manta_ray", loc -> new FishtasticFishItem(props(loc)));
        LIZARDFISH = RegistrationApiSided.getInstance().registerItem("lizardfish", loc -> new FishtasticFishItem(props(loc)));
        LONGNOSE_GAR = RegistrationApiSided.getInstance().registerItem("longnose_gar", loc -> new FishtasticFishItem(props(loc)));
        MOLTEN_MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("molten_moorish_idol", loc -> new FishtasticFishItem(props(loc)));
        MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("moorish_idol", loc -> new FishtasticFishItem(props(loc)));
        NEON_TETRA = RegistrationApiSided.getInstance().registerItem("neon_tetra", loc -> new FishtasticFishItem(props(loc)));
        NORTHERN_PIKE = RegistrationApiSided.getInstance().registerItem("northern_pike", loc -> new FishtasticFishItem(props(loc)));
        OCEAN_SUNFISH = RegistrationApiSided.getInstance().registerItem("ocean_sunfish", loc -> new FishtasticFishItem(props(loc)));
        PARROTFISH = RegistrationApiSided.getInstance().registerItem("parrotfish", loc -> new FishtasticFishItem(props(loc)));
        PORTUGUESE_MAN_O_WAR = RegistrationApiSided.getInstance().registerItem("portuguese_man_o_war", loc -> new FishtasticFishItem(props(loc)));
        RAINFORDIA = RegistrationApiSided.getInstance().registerItem("rainfordia", loc -> new FishtasticFishItem(props(loc)));
        ROYAL_GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("royal_garden_eel", loc -> new FishtasticFishItem(props(loc)));
        SHRIMP = RegistrationApiSided.getInstance().registerItem("shrimp", loc -> new FishtasticFishItem(props(loc)));
        STARFISH = RegistrationApiSided.getInstance().registerItem("starfish", loc -> new FishtasticFishItem(props(loc)));

        // Bait items — BaitEffect component drives fishing behavior
        WORMS = RegistrationApiSided.getInstance().registerItem("worms",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), BaitEffect.WORMS)));
        GUMMY_WORMS = RegistrationApiSided.getInstance().registerItem("gummy_worms",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), BaitEffect.GUMMY_WORMS)));
        BLAZED_GRUB = RegistrationApiSided.getInstance().registerItem("blazed_grub",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), BaitEffect.BLAZED_GRUB)));

        PILE_OF_FISH = RegistrationApiSided.getInstance().registerItem("pile_of_fish",
                loc -> new PileOfFishItem(
                        props(loc)
                                .stacksTo(1)
                                .component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                )
        );

        // Specialist bait items — heavily bias toward one affinity group via a strong weight multiplier
        FRESHWATER_BAIT = RegistrationApiSided.getInstance().registerItem("freshwater_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                0.3f, 0.05f, 0.1f, 0, 1.0f, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.FRESHWATER_FISH, 2.0f))))));
        OCEAN_BAIT = RegistrationApiSided.getInstance().registerItem("ocean_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                0.3f, 0.10f, 0.1f, 0, 1.0f, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.OCEAN_FISH, 2.0f))))));
        PREDATOR_BAIT = RegistrationApiSided.getInstance().registerItem("predator_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                1.0f, 0.10f, 0.1f, 0, 1.0f, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.PREDATOR_FISH, 2.0f))))));
        DEEP_SEA_BAIT = RegistrationApiSided.getInstance().registerItem("deep_sea_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                1.5f, 0.15f, 0.1f, -1, 1.0f, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.DEEP_SEA_FISH, 2.0f))))));

        QUEST_TOKEN = RegistrationApiSided.getInstance().registerItem("quest_token",
                loc -> new Item(props(loc).stacksTo(64)));

        // Trash items — caught instead of fish/treasure, feed the global cleanup goal
        SEA_GLASS = RegistrationApiSided.getInstance().registerItem("sea_glass", loc -> new Item(props(loc).stacksTo(64)));
        OLD_TIRE = RegistrationApiSided.getInstance().registerItem("old_tire", loc -> new Item(props(loc).stacksTo(64)));
        PLASTIC_LITTER = RegistrationApiSided.getInstance().registerItem("plastic_litter", loc -> new Item(props(loc).stacksTo(64)));

        // Fish tank cosmetics — custom items for quest rewards / rare drops
        COSMETIC_TREASURE_CHEST = RegistrationApiSided.getInstance().registerItem("cosmetic_treasure_chest",
                loc -> new FishTankCosmeticItem(Blocks.CHEST, props(loc).stacksTo(1)));
        COSMETIC_SEA_LANTERN = RegistrationApiSided.getInstance().registerItem("cosmetic_sea_lantern",
                loc -> new FishTankCosmeticItem(Blocks.SEA_LANTERN, props(loc).stacksTo(1)));

    }
}

package grill24.fishtastic;

import grill24.FishtasticRegistries;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.component.RodBaitContents;
import grill24.fishtastic.component.RodCharmContents;
import grill24.fishtastic.component.RodHookContents;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.CosmeticCaptureWandItem;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // ----- Debug Tools -----
    public static Holder<Item> COSMETIC_CAPTURE_WAND;

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

    // ----- Hook Items -----
    public static Holder<Item> HOOK;
    public static Holder<Item> OLD_COPPER_HOOK;

    // ----- Charm Items -----
    public static Holder<Item> AMETHYST_CHARM;
    public static Holder<Item> CRYSTAL_BALL_CHARM;
    public static Holder<Item> FOUR_LEAF_CHARM;
    public static Holder<Item> LUNA_CHARM;
    public static Holder<Item> BANANA_CHARM;

    // ----- Quest Items -----
    // Currency display icon (pile-of-coins texture) — used wherever a token balance/cost/reward is shown.
    public static Holder<Item> PILE_OF_COINS;
    // Single-coin texture, decorative only — used for the mini coin-fly particles on quest claim.
    public static Holder<Item> QUEST_TOKEN;

    // ----- Trash Items -----
    public static Holder<Item> SEA_GLASS;
    public static Holder<Item> OLD_TIRE;
    public static Holder<Item> PLASTIC_LITTER;

    // ----- Fish Tank Cosmetics -----
    public static Holder<Item> COSMETIC_TREASURE_CHEST;
    public static Holder<Item> COSMETIC_MOSSY_BOULDER;
    public static Holder<Item> COSMETIC_PETALS;
    public static Holder<Item> COSMETIC_SPRUCE_GAZEBO;
    public static Holder<Item> COSMETIC_CATERPILLER;
    public static Holder<Item> COSMETIC_LEAF_LITTER;
    public static Holder<Item> COSMETIC_CORAL_REEF_1;
    public static Holder<Item> COSMETIC_CORAL_REEF_2;
    public static Holder<Item> COSMETIC_OAK_TREE;
    public static Holder<Item> COSMETIC_BIRCH_TREE;
    public static Holder<Item> COSMETIC_DYNAMIC_DUO;

    /** Wood types the fence-arch cosmetic is generated for — see fabric datagen's {@code CosmeticStructureProvider}. */
    public static final List<String> FENCE_ARCH_WOOD_TYPES = List.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "pale_oak", "mangrove", "cherry", "crimson", "warped"
    );
    /** One {@link FishTankStructureCosmeticItem} per {@link #FENCE_ARCH_WOOD_TYPES} entry, keyed by wood name. */
    public static final Map<String, Holder<Item>> COSMETIC_FENCE_ARCH = new LinkedHashMap<>();

    /** Base block variants the simple-lamp cosmetic is generated for — see fabric datagen's {@code CosmeticStructureProvider}. */
    public static final List<String> LAMP_VARIANTS = List.of(
            "chiseled_quartz", "glazed_terracotta_light_blue", "glazed_terracotta_cyan", "glazed_terracotta_magenta",
            "amethyst", "prismarine_bricks", "crying_obsidian"
    );
    /** One {@link FishTankStructureCosmeticItem} per {@link #LAMP_VARIANTS} entry, keyed by variant name. */
    public static final Map<String, Holder<Item>> COSMETIC_LAMP = new LinkedHashMap<>();

    // ----- Fish Items -----
    public static Holder<Item> BLUEGILL;
    public static Holder<Item> FLAPJACK_OCTOPUS;
    public static Holder<Item> FRIED_SHRIMP;
    public static Holder<Item> FROZEN_GIANT_MANTA_RAY;
    public static Holder<Item> GARDEN_EEL;
    public static Holder<Item> GIANT_MANTA_RAY;
    public static Holder<Item> GLASS_SQUID;
    public static Holder<Item> LEAFY_SEA_DRAGON;
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
        COSMETIC_CAPTURE_WAND = RegistrationApiSided.getInstance().registerItem("cosmetic_capture_wand",
                loc -> new CosmeticCaptureWandItem(props(loc).stacksTo(1)));

        FISHING_MINIGAME_ROD_BACKGROUND = RegistrationApiSided.getInstance().registerItem("fishing_minigame_rod_background", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_BOBBER = RegistrationApiSided.getInstance().registerItem("fishing_minigame_bobber", loc -> new TestItem(props(loc).stacksTo(1)));
        SPARKLE = RegistrationApiSided.getInstance().registerItem("sparkle", loc -> new Item(props(loc).stacksTo(1)));
        GENERIC_FISH = RegistrationApiSided.getInstance().registerItem("generic_fish", loc -> new FishtasticFishItem(props(loc)));
        REWARD_CHEST = RegistrationApiSided.getInstance().registerItem("reward_chest", loc -> new Item(props(loc).stacksTo(1)));

        COPPER_FISHING_ROD = RegistrationApiSided.getInstance().registerItem("copper_fishing_rod",
                loc -> new CopperFishingRod(props(loc).durability(250)
                        .component(FishtasticDataComponents.ROD_BAIT_CONTENTS.value(), RodBaitContents.EMPTY)
                        .component(FishtasticDataComponents.ROD_HOOK_CONTENTS.value(), RodHookContents.EMPTY)
                        .component(FishtasticDataComponents.ROD_CHARM_CONTENTS.value(), RodCharmContents.EMPTY)));

        // Fish items — size/weight now defined in fish_profile data entries
        ACUTE_IASPIS = RegistrationApiSided.getInstance().registerItem("acute_iaspis", loc -> new FishtasticFishItem(props(loc)));
        BLUEGILL = RegistrationApiSided.getInstance().registerItem("bluegill", loc -> new FishtasticFishItem(props(loc)));
        FLAPJACK_OCTOPUS = RegistrationApiSided.getInstance().registerItem("flapjack_octopus", loc -> new FishtasticFishItem(props(loc)));
        FRIED_SHRIMP = RegistrationApiSided.getInstance().registerItem("fried_shrimp", loc -> new FishtasticFishItem(props(loc)));
        FROZEN_GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("frozen_giant_manta_ray", loc -> new FishtasticFishItem(props(loc)));
        GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("garden_eel", loc -> new FishtasticFishItem(props(loc)));
        GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("giant_manta_ray", loc -> new FishtasticFishItem(props(loc)));
        GLASS_SQUID = RegistrationApiSided.getInstance().registerItem("glass_squid", loc -> new FishtasticFishItem(props(loc)));
        LEAFY_SEA_DRAGON = RegistrationApiSided.getInstance().registerItem("leafy_sea_dragon", loc -> new FishtasticFishItem(props(loc)));
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
                                0.3f, 0.05f, 0.1f, 0, 0.25f, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.FRESHWATER_FISH, 4.0f))))));
        OCEAN_BAIT = RegistrationApiSided.getInstance().registerItem("ocean_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                0.3f, 0.10f, 0.1f, 0, 0.25f, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.OCEAN_FISH, 4.0f))))));
        PREDATOR_BAIT = RegistrationApiSided.getInstance().registerItem("predator_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                1.0f, 0.10f, 0.1f, 0, 0.25f, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.PREDATOR_FISH, 4.0f))))));
        DEEP_SEA_BAIT = RegistrationApiSided.getInstance().registerItem("deep_sea_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                1.5f, 0.15f, 0.1f, -1, 0.25f, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.DEEP_SEA_FISH, 4.0f))))));

        // Hook items — loaded into the rod's hook slot; affect quality bias and trash chance
        HOOK = RegistrationApiSided.getInstance().registerItem("hook",
                loc -> new FishtasticFishItem(props(loc).durability(100)
                        .component(FishtasticDataComponents.HOOK_EFFECT.value(), HookEffect.HOOK)));
        OLD_COPPER_HOOK = RegistrationApiSided.getInstance().registerItem("old_copper_hook",
                loc -> new FishtasticFishItem(props(loc).durability(50)
                        .component(FishtasticDataComponents.HOOK_EFFECT.value(), HookEffect.OLD_COPPER_HOOK)));

        // Charm items — loaded into the rod's charm slot; affect fishing minigame physics
        AMETHYST_CHARM = RegistrationApiSided.getInstance().registerItem("amethyst_charm",
                loc -> new FishtasticFishItem(props(loc).durability(200)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.AMETHYST_CHARM)));
        CRYSTAL_BALL_CHARM = RegistrationApiSided.getInstance().registerItem("crystal_ball_charm",
                loc -> new FishtasticFishItem(props(loc).durability(200)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.CRYSTAL_BALL_CHARM)));
        FOUR_LEAF_CHARM = RegistrationApiSided.getInstance().registerItem("four_leaf_charm",
                loc -> new FishtasticFishItem(props(loc).durability(200)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.FOUR_LEAF_CHARM)));
        LUNA_CHARM = RegistrationApiSided.getInstance().registerItem("luna_charm",
                loc -> new FishtasticFishItem(props(loc).durability(200)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.LUNA_CHARM)));
        BANANA_CHARM = RegistrationApiSided.getInstance().registerItem("banana_charm",
                loc -> new FishtasticFishItem(props(loc).durability(200)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.BANANA_CHARM)));

        PILE_OF_COINS = RegistrationApiSided.getInstance().registerItem("pile_of_coins",
                loc -> new Item(props(loc).stacksTo(64)));
        QUEST_TOKEN = RegistrationApiSided.getInstance().registerItem("quest_token",
                loc -> new Item(props(loc).stacksTo(64)));

        // Trash items — caught instead of fish/treasure, feed the global cleanup goal
        SEA_GLASS = RegistrationApiSided.getInstance().registerItem("sea_glass", loc -> new Item(props(loc).stacksTo(64)));
        OLD_TIRE = RegistrationApiSided.getInstance().registerItem("old_tire", loc -> new Item(props(loc).stacksTo(64)));
        PLASTIC_LITTER = RegistrationApiSided.getInstance().registerItem("plastic_litter", loc -> new Item(props(loc).stacksTo(64)));

        // Fish tank cosmetics — custom items for quest rewards / rare drops
        COSMETIC_TREASURE_CHEST = RegistrationApiSided.getInstance().registerItem("cosmetic_treasure_chest",
                loc -> new FishTankCosmeticItem(Blocks.CHEST, props(loc).stacksTo(1)));
        COSMETIC_MOSSY_BOULDER = RegistrationApiSided.getInstance().registerItem("cosmetic_mossy_boulder",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("mossy_boulder")),
                        props(loc).stacksTo(1)));
        COSMETIC_PETALS = RegistrationApiSided.getInstance().registerItem("cosmetic_petals",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("petals")),
                        props(loc).stacksTo(1)));
        COSMETIC_SPRUCE_GAZEBO = RegistrationApiSided.getInstance().registerItem("cosmetic_spruce_gazebo",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("spruce_gazebo")),
                        props(loc).stacksTo(1)));
        COSMETIC_CATERPILLER = RegistrationApiSided.getInstance().registerItem("cosmetic_caterpiller",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("caterpiller")),
                        props(loc).stacksTo(1)));
        COSMETIC_LEAF_LITTER = RegistrationApiSided.getInstance().registerItem("cosmetic_leaf_litter",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("leaf_litter")),
                        props(loc).stacksTo(1)));
        COSMETIC_CORAL_REEF_1 = RegistrationApiSided.getInstance().registerItem("cosmetic_coral_reef_1",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("coral_reef_1")),
                        props(loc).stacksTo(1)));
        COSMETIC_CORAL_REEF_2 = RegistrationApiSided.getInstance().registerItem("cosmetic_coral_reef_2",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("coral_reef_2")),
                        props(loc).stacksTo(1)));
        COSMETIC_OAK_TREE = RegistrationApiSided.getInstance().registerItem("cosmetic_oak_tree",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("oak_tree")),
                        props(loc).stacksTo(1)));
        COSMETIC_BIRCH_TREE = RegistrationApiSided.getInstance().registerItem("cosmetic_birch_tree",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("birch_tree")),
                        props(loc).stacksTo(1)));
        COSMETIC_DYNAMIC_DUO = RegistrationApiSided.getInstance().registerItem("cosmetic_dynamic_duo",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("dynamic_duo")),
                        props(loc).stacksTo(1)));
        for (String wood : FENCE_ARCH_WOOD_TYPES) {
            String id = "cosmetic_fence_arch_" + wood;
            Holder<Item> fenceArch = RegistrationApiSided.getInstance().registerItem(id,
                    loc -> new FishTankStructureCosmeticItem(
                            ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft(id)),
                            props(loc).stacksTo(1)));
            COSMETIC_FENCE_ARCH.put(wood, fenceArch);
        }
        for (String variant : LAMP_VARIANTS) {
            String id = "cosmetic_lamp_" + variant;
            Holder<Item> lamp = RegistrationApiSided.getInstance().registerItem(id,
                    loc -> new FishTankStructureCosmeticItem(
                            ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft(id)),
                            props(loc).stacksTo(1)));
            COSMETIC_LAMP.put(variant, lamp);
        }
    }
}

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
import grill24.fishtastic.item.ObsidianFishingRod;
import grill24.fishtastic.item.CosmeticCaptureWandItem;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.item.PileOfFishItem;
import grill24.fishtastic.item.StormCharmItem;
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

    // Zone icons for the fishing minigame HUD (top-right of the bar) — one per FishProfile.Zone.
    // Textures are placeholders pending dedicated art; see FishtasticModelProvider.
    public static Holder<Item> FISHING_MINIGAME_ZONE_OCEAN;
    public static Holder<Item> FISHING_MINIGAME_ZONE_DEEP_OCEAN;
    public static Holder<Item> FISHING_MINIGAME_ZONE_RIVER;
    public static Holder<Item> FISHING_MINIGAME_ZONE_NETHER;
    public static Holder<Item> FISHING_MINIGAME_ZONE_CAVE;
    public static Holder<Item> FISHING_MINIGAME_ZONE_HIGH_ALTITUDE;

    // ----- Debug Tools -----
    public static Holder<Item> COSMETIC_CAPTURE_WAND;

    // ----- Fishing Rods -----
    public static Holder<Item> COPPER_FISHING_ROD;
    public static Holder<Item> OBSIDIAN_FISHING_ROD;

    // ----- Fish Items -----
    public static Holder<Item> ACUTE_IASPIS;
    public static Holder<Item> BETTA;

    // ----- Pile of Fish -----
    public static Holder<Item> PILE_OF_FISH;

    // ----- Bait Items -----
    public static Holder<Item> BLAZED_GRUB;
    public static Holder<Item> GUMMY_WORMS;
    public static Holder<Item> WORMS;
    public static Holder<Item> SMALL_FISH_BAIT;
    public static Holder<Item> CALM_BAIT;
    public static Holder<Item> FRENZY_BAIT;
    public static Holder<Item> TROPHY_BAIT;

    // ----- Hook Items -----
    public static Holder<Item> HOOK;
    public static Holder<Item> OLD_COPPER_HOOK;

    // ----- Charm Items -----
    public static Holder<Item> AMETHYST_CHARM;
    public static Holder<Item> CRYSTAL_BALL_CHARM;
    public static Holder<Item> FOUR_LEAF_CHARM;
    public static Holder<Item> LUNA_CHARM;
    public static Holder<Item> STORM_CHARM;
    public static Holder<Item> BANANA_CHARM;
    public static Holder<Item> LITTLE_FISH_BOX;
    public static Holder<Item> ANGLERS_ALMANAC;

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
    public static Holder<Item> COSMETIC_VINTAGE_VINE;
    public static Holder<Item> COSMETIC_CASTLE_RUIN;
    public static Holder<Item> COSMETIC_WAX_SKULL_CANDLE;

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
    public static Holder<Item> ANGLER_FISH;
    public static Holder<Item> ARCTIC_CHAR;
    public static Holder<Item> BLIND_CAVEFISH;
    public static Holder<Item> BLIND_CAVE_TETRA;
    public static Holder<Item> BLUEGILL;
    public static Holder<Item> BRIDLE_SHINER;
    public static Holder<Item> BULL_TROUT;
    public static Holder<Item> COMMON_OCTOPUS;
    public static Holder<Item> DEVILS_HOLE_PUPFISH;
    public static Holder<Item> DISCUS;
    public static Holder<Item> EUROPEAN_GRAYLING;
    public static Holder<Item> FLAPJACK_OCTOPUS;
    public static Holder<Item> FRIED_SHRIMP;
    public static Holder<Item> FROZEN_GIANT_MANTA_RAY;
    public static Holder<Item> GARDEN_EEL;
    public static Holder<Item> GIANT_MANTA_RAY;
    public static Holder<Item> GIRAFFE_CICHLID;
    public static Holder<Item> GLASS_CATFISH;
    public static Holder<Item> GLASS_SQUID;
    public static Holder<Item> GOLDEN_TROUT;
    public static Holder<Item> GREENSTRIPE_BARB;
    public static Holder<Item> INDIAN_GLASSY_FISH;
    public static Holder<Item> JAPANESE_SPIDER_CRAB;
    public static Holder<Item> LEAFY_SEA_DRAGON;
    public static Holder<Item> LIZARDFISH;
    public static Holder<Item> LONGNOSE_GAR;
    public static Holder<Item> MOLTEN_MOORISH_IDOL;
    public static Holder<Item> MOORISH_IDOL;
    public static Holder<Item> NEON_GOBY;
    public static Holder<Item> NEON_TETRA;
    public static Holder<Item> NORTHERN_PIKE;
    public static Holder<Item> OCEAN_SUNFISH;
    public static Holder<Item> PARROTFISH;
    public static Holder<Item> PORTUGUESE_MAN_O_WAR;
    public static Holder<Item> RAINBOW_TROUT;
    public static Holder<Item> RAINFORDIA;
    public static Holder<Item> RED_BELLIED_PIRAHNA;
    public static Holder<Item> ROYAL_GARDEN_EEL;
    public static Holder<Item> SHRIMP;
    public static Holder<Item> STARFISH;
    public static Holder<Item> SULPHUR_MOLLY;
    public static Holder<Item> TRAPANIA_SCURRA;
    public static Holder<Item> WATERFALL_CLIMBING_CAVE_FISH;
    public static Holder<Item> WILLANS_CHROMODORIS;
    public static Holder<Item> YELLOWLINE_GOBY;
    public static Holder<Item> YELLOWSTRIPE_GRUNT;

    public static void registerItems() {
        COSMETIC_CAPTURE_WAND = RegistrationApiSided.getInstance().registerItem("cosmetic_capture_wand",
                loc -> new CosmeticCaptureWandItem(props(loc).stacksTo(1)));

        FISHING_MINIGAME_ROD_BACKGROUND = RegistrationApiSided.getInstance().registerItem("fishing_minigame_rod_background", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_BOBBER = RegistrationApiSided.getInstance().registerItem("fishing_minigame_bobber", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_ZONE_OCEAN = RegistrationApiSided.getInstance().registerItem("fishing_minigame_zone_ocean", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_ZONE_DEEP_OCEAN = RegistrationApiSided.getInstance().registerItem("fishing_minigame_zone_deep_ocean", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_ZONE_RIVER = RegistrationApiSided.getInstance().registerItem("fishing_minigame_zone_river", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_ZONE_NETHER = RegistrationApiSided.getInstance().registerItem("fishing_minigame_zone_nether", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_ZONE_CAVE = RegistrationApiSided.getInstance().registerItem("fishing_minigame_zone_cave", loc -> new TestItem(props(loc).stacksTo(1)));
        FISHING_MINIGAME_ZONE_HIGH_ALTITUDE = RegistrationApiSided.getInstance().registerItem("fishing_minigame_zone_high_altitude", loc -> new TestItem(props(loc).stacksTo(1)));
        SPARKLE = RegistrationApiSided.getInstance().registerItem("sparkle", loc -> new Item(props(loc).stacksTo(1)));
        GENERIC_FISH = RegistrationApiSided.getInstance().registerItem("generic_fish", loc -> new FishtasticFishItem(props(loc)));
        REWARD_CHEST = RegistrationApiSided.getInstance().registerItem("reward_chest", loc -> new Item(props(loc).stacksTo(1)));

        COPPER_FISHING_ROD = RegistrationApiSided.getInstance().registerItem("copper_fishing_rod",
                loc -> new CopperFishingRod(props(loc).durability(250)
                        .component(FishtasticDataComponents.ROD_BAIT_CONTENTS.value(), RodBaitContents.EMPTY)
                        .component(FishtasticDataComponents.ROD_HOOK_CONTENTS.value(), RodHookContents.EMPTY)
                        .component(FishtasticDataComponents.ROD_CHARM_CONTENTS.value(), RodCharmContents.EMPTY)));

        OBSIDIAN_FISHING_ROD = RegistrationApiSided.getInstance().registerItem("obsidian_fishing_rod",
                loc -> new ObsidianFishingRod(props(loc).durability(250)
                        .component(FishtasticDataComponents.ROD_BAIT_CONTENTS.value(), RodBaitContents.EMPTY)
                        .component(FishtasticDataComponents.ROD_HOOK_CONTENTS.value(), RodHookContents.EMPTY)
                        .component(FishtasticDataComponents.ROD_CHARM_CONTENTS.value(), RodCharmContents.EMPTY)));

        // Fish items — size/weight now defined in fish_profile data entries
        ACUTE_IASPIS = RegistrationApiSided.getInstance().registerItem("acute_iaspis", loc -> new FishtasticFishItem(props(loc)));
        ANGLER_FISH = RegistrationApiSided.getInstance().registerItem("angler_fish", loc -> new FishtasticFishItem(props(loc)));
        ARCTIC_CHAR = RegistrationApiSided.getInstance().registerItem("arctic_char", loc -> new FishtasticFishItem(props(loc)));
        BETTA = RegistrationApiSided.getInstance().registerItem("betta", loc -> new FishtasticFishItem(props(loc)));
        BLIND_CAVEFISH = RegistrationApiSided.getInstance().registerItem("blind_cavefish", loc -> new FishtasticFishItem(props(loc)));
        BLIND_CAVE_TETRA = RegistrationApiSided.getInstance().registerItem("blind_cave_tetra", loc -> new FishtasticFishItem(props(loc)));
        BLUEGILL = RegistrationApiSided.getInstance().registerItem("bluegill", loc -> new FishtasticFishItem(props(loc)));
        BRIDLE_SHINER = RegistrationApiSided.getInstance().registerItem("bridle_shiner", loc -> new FishtasticFishItem(props(loc)));
        BULL_TROUT = RegistrationApiSided.getInstance().registerItem("bull_trout", loc -> new FishtasticFishItem(props(loc)));
        COMMON_OCTOPUS = RegistrationApiSided.getInstance().registerItem("common_octopus", loc -> new FishtasticFishItem(props(loc)));
        DEVILS_HOLE_PUPFISH = RegistrationApiSided.getInstance().registerItem("devils_hole_pupfish", loc -> new FishtasticFishItem(props(loc)));
        DISCUS = RegistrationApiSided.getInstance().registerItem("discus", loc -> new FishtasticFishItem(props(loc)));
        EUROPEAN_GRAYLING = RegistrationApiSided.getInstance().registerItem("european_grayling", loc -> new FishtasticFishItem(props(loc)));
        FLAPJACK_OCTOPUS = RegistrationApiSided.getInstance().registerItem("flapjack_octopus", loc -> new FishtasticFishItem(props(loc)));
        FRIED_SHRIMP = RegistrationApiSided.getInstance().registerItem("fried_shrimp", loc -> new FishtasticFishItem(props(loc)));
        FROZEN_GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("frozen_giant_manta_ray", loc -> new FishtasticFishItem(props(loc)));
        GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("garden_eel", loc -> new FishtasticFishItem(props(loc)));
        GIANT_MANTA_RAY = RegistrationApiSided.getInstance().registerItem("giant_manta_ray", loc -> new FishtasticFishItem(props(loc)));
        GIRAFFE_CICHLID = RegistrationApiSided.getInstance().registerItem("giraffe_cichlid", loc -> new FishtasticFishItem(props(loc)));
        GLASS_CATFISH = RegistrationApiSided.getInstance().registerItem("glass_catfish", loc -> new FishtasticFishItem(props(loc)));
        GLASS_SQUID = RegistrationApiSided.getInstance().registerItem("glass_squid", loc -> new FishtasticFishItem(props(loc)));
        GOLDEN_TROUT = RegistrationApiSided.getInstance().registerItem("golden_trout", loc -> new FishtasticFishItem(props(loc)));
        GREENSTRIPE_BARB = RegistrationApiSided.getInstance().registerItem("greenstripe_barb", loc -> new FishtasticFishItem(props(loc)));
        INDIAN_GLASSY_FISH = RegistrationApiSided.getInstance().registerItem("indian_glassy_fish", loc -> new FishtasticFishItem(props(loc)));
        JAPANESE_SPIDER_CRAB = RegistrationApiSided.getInstance().registerItem("japanese_spider_crab", loc -> new FishtasticFishItem(props(loc)));
        LEAFY_SEA_DRAGON = RegistrationApiSided.getInstance().registerItem("leafy_sea_dragon", loc -> new FishtasticFishItem(props(loc)));
        LIZARDFISH = RegistrationApiSided.getInstance().registerItem("lizardfish", loc -> new FishtasticFishItem(props(loc)));
        LONGNOSE_GAR = RegistrationApiSided.getInstance().registerItem("longnose_gar", loc -> new FishtasticFishItem(props(loc)));
        MOLTEN_MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("molten_moorish_idol", loc -> new FishtasticFishItem(props(loc)));
        MOORISH_IDOL = RegistrationApiSided.getInstance().registerItem("moorish_idol", loc -> new FishtasticFishItem(props(loc)));
        NEON_GOBY = RegistrationApiSided.getInstance().registerItem("neon_goby", loc -> new FishtasticFishItem(props(loc)));
        NEON_TETRA = RegistrationApiSided.getInstance().registerItem("neon_tetra", loc -> new FishtasticFishItem(props(loc)));
        NORTHERN_PIKE = RegistrationApiSided.getInstance().registerItem("northern_pike", loc -> new FishtasticFishItem(props(loc)));
        OCEAN_SUNFISH = RegistrationApiSided.getInstance().registerItem("ocean_sunfish", loc -> new FishtasticFishItem(props(loc)));
        PARROTFISH = RegistrationApiSided.getInstance().registerItem("parrotfish", loc -> new FishtasticFishItem(props(loc)));
        PORTUGUESE_MAN_O_WAR = RegistrationApiSided.getInstance().registerItem("portuguese_man_o_war", loc -> new FishtasticFishItem(props(loc)));
        RAINBOW_TROUT = RegistrationApiSided.getInstance().registerItem("rainbow_trout", loc -> new FishtasticFishItem(props(loc)));
        RAINFORDIA = RegistrationApiSided.getInstance().registerItem("rainfordia", loc -> new FishtasticFishItem(props(loc)));
        RED_BELLIED_PIRAHNA = RegistrationApiSided.getInstance().registerItem("red_bellied_pirahna", loc -> new FishtasticFishItem(props(loc)));
        ROYAL_GARDEN_EEL = RegistrationApiSided.getInstance().registerItem("royal_garden_eel", loc -> new FishtasticFishItem(props(loc)));
        SHRIMP = RegistrationApiSided.getInstance().registerItem("shrimp", loc -> new FishtasticFishItem(props(loc)));
        STARFISH = RegistrationApiSided.getInstance().registerItem("starfish", loc -> new FishtasticFishItem(props(loc)));
        SULPHUR_MOLLY = RegistrationApiSided.getInstance().registerItem("sulphur_molly", loc -> new FishtasticFishItem(props(loc)));
        TRAPANIA_SCURRA = RegistrationApiSided.getInstance().registerItem("trapania_scurra", loc -> new FishtasticFishItem(props(loc)));
        WATERFALL_CLIMBING_CAVE_FISH = RegistrationApiSided.getInstance().registerItem("waterfall_climbing_cave_fish", loc -> new FishtasticFishItem(props(loc)));
        WILLANS_CHROMODORIS = RegistrationApiSided.getInstance().registerItem("willans_chromodoris", loc -> new FishtasticFishItem(props(loc)));
        YELLOWLINE_GOBY = RegistrationApiSided.getInstance().registerItem("yellowline_goby", loc -> new FishtasticFishItem(props(loc)));
        YELLOWSTRIPE_GRUNT = RegistrationApiSided.getInstance().registerItem("yellowstripe_grunt", loc -> new FishtasticFishItem(props(loc)));

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

        // Specialist bait items — boost a targeted subset and suppress the rest (0.3x)
        // without hard-excluding it. Item ids kept as-is (freshwater/ocean/predator/deep_sea_bait)
        // to reuse existing recipes/models/shop entries; only their identity/effect changed —
        // these tags used to echo Zone (redundant once zone gating went in), now they're genuine
        // zone-orthogonal traits (size, temperament-difficulty cluster).
        //
        // rarityExponent flattens each group's *internal* base_weight spread (independent of
        // multiplier, which still controls the group's overall share vs the rest of the pool) —
        // without it, a handful of high base_weight members (e.g. bluegill/neon_tetra/blazed_grub
        // at 75-100 vs most tag-mates at 2-7) would eat almost all of the boosted weight. Chosen
        // per tag from its actual base_weight spread: small_fish/frenzy_fish span 2-100 (strong
        // flattening), calm_fish spans 2-75 (strong), big_fish is already tight at 2-15 (mild).
        SMALL_FISH_BAIT = RegistrationApiSided.getInstance().registerItem("freshwater_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                0.0f, 0.05f, 0.1f, 1, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.SMALL_FISH, 2.0f, 0.3f, 0.35f))))));
        CALM_BAIT = RegistrationApiSided.getInstance().registerItem("ocean_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                0.0f, 0.10f, 0.1f, 0, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.CALM_FISH, 2.0f, 0.3f, 0.4f))))));
        // multiplier raised 1.5->2.2 (and, for Trophy Bait, rarityExponent steepened 0.6->0.45)
        // per the 2026-07-24 bait balance audit: at the old values both baits were mathematically
        // dominated by Gummy Worms across most or all of their own target group, since Gummy
        // Worms' flat 2.0x modFishMultiplier applied everywhere beat a weaker in-group-only
        // multiplier. The new values win Frenzy/Trophy Bait back their group's rare/uncommon
        // members (crossover point vs Gummy Worms now sits around base_weight 12-13, up from
        // ~4.4 for Frenzy and ~0.6 for Trophy) — see [[feedback_fish_multiplier_cap]] memory.
        FRENZY_BAIT = RegistrationApiSided.getInstance().registerItem("predator_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                0.0f, 0.10f, 0.1f, 0, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.FRENZY_FISH, 2.2f, 0.3f, 0.35f))))));
        TROPHY_BAIT = RegistrationApiSided.getInstance().registerItem("deep_sea_bait",
                loc -> new FishtasticFishItem(props(loc)
                        .component(FishtasticDataComponents.BAIT_EFFECT.value(), new BaitEffect(
                                0.0f, 0.15f, 0.1f, -1, 1.0f, 0.25f,
                                Optional.empty(), List.of(new BaitEffect.FishGroupAffinity(FishtasticItemTags.BIG_FISH, 2.2f, 0.3f, 0.45f))))));

        // Hook items — loaded into the rod's hook slot; affect quality bias and trash chance
        HOOK = RegistrationApiSided.getInstance().registerItem("hook",
                loc -> new FishtasticFishItem(props(loc).durability(100)
                        .component(FishtasticDataComponents.HOOK_EFFECT.value(), HookEffect.HOOK)));
        OLD_COPPER_HOOK = RegistrationApiSided.getInstance().registerItem("old_copper_hook",
                loc -> new FishtasticFishItem(props(loc).durability(50)
                        .component(FishtasticDataComponents.HOOK_EFFECT.value(), HookEffect.OLD_COPPER_HOOK)));

        // Charm items — loaded into the rod's charm slot; affect fishing minigame physics
        AMETHYST_CHARM = RegistrationApiSided.getInstance().registerItem("amethyst_charm",
                loc -> new FishtasticFishItem(props(loc).durability(64)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.AMETHYST_CHARM)));
        CRYSTAL_BALL_CHARM = RegistrationApiSided.getInstance().registerItem("crystal_ball_charm",
                loc -> new FishtasticFishItem(props(loc).durability(64)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.CRYSTAL_BALL_CHARM)));
        FOUR_LEAF_CHARM = RegistrationApiSided.getInstance().registerItem("four_leaf_charm",
                loc -> new FishtasticFishItem(props(loc).durability(64)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.FOUR_LEAF_CHARM)));
        LUNA_CHARM = RegistrationApiSided.getInstance().registerItem("luna_charm",
                loc -> new FishtasticFishItem(props(loc).durability(64)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.LUNA_CHARM)));
        BANANA_CHARM = RegistrationApiSided.getInstance().registerItem("banana_charm",
                loc -> new FishtasticFishItem(props(loc).durability(64)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.BANANA_CHARM)));
        ANGLERS_ALMANAC = RegistrationApiSided.getInstance().registerItem("anglers_almanac",
                loc -> new FishtasticFishItem(props(loc).durability(64)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.ANGLERS_ALMANAC)));
        // Not a rod-slot charm and deliberately absent from FISHING_CHARMS — single-use, used
        // from the hand, and it changes the world's weather for real. See StormCharmItem.
        STORM_CHARM = RegistrationApiSided.getInstance().registerItem("storm_charm",
                loc -> new StormCharmItem(props(loc).stacksTo(16)));
        LITTLE_FISH_BOX = RegistrationApiSided.getInstance().registerItem("little_fish_box",
                loc -> new FishtasticFishItem(props(loc).durability(64)
                        .component(FishtasticDataComponents.CHARM_EFFECT.value(), CharmEffect.LITTLE_FISH_BOX)));

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
        COSMETIC_VINTAGE_VINE = RegistrationApiSided.getInstance().registerItem("cosmetic_vintage_vine",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("vintage_vine")),
                        props(loc).stacksTo(1)));
        COSMETIC_CASTLE_RUIN = RegistrationApiSided.getInstance().registerItem("cosmetic_castle_ruin",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("castle_ruin")),
                        props(loc).stacksTo(1)));
        COSMETIC_WAX_SKULL_CANDLE = RegistrationApiSided.getInstance().registerItem("cosmetic_wax_skull_candle",
                loc -> new FishTankStructureCosmeticItem(
                        ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, grill24.fishtastic.util.Utility.ft("wax_skull_candle")),
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

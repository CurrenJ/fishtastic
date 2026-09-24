package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.util.Ids;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.data.models.model.ModelTemplate;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Optional;

public class FishtasticModelProvider extends FabricModelProvider {
    public FishtasticModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators blockModelGenerators) {
        // Generate models for undyed glass
        generateModelsForGlassBlock(blockModelGenerators,
                Fishtastic.id("block/glass/borderless_glass"),
                Fishtastic.id("block/glass/borderless_glass"),
                FishtasticBlocks.BORDERLESS_GLASS);
        generateModelsForGlassBlock(blockModelGenerators,
                Fishtastic.id("block/glass/clear_glass"),
                Fishtastic.id("block/glass/clear_glass"),
                FishtasticBlocks.CLEAR_GLASS);

        // Generate models for borderless stained glass (all colors)
        generateGlassModels(blockModelGenerators, FishtasticBlocks.BORDERLESS_STAINED_GLASS);

        // Generate models for clear stained glass (all colors)
        generateGlassModels(blockModelGenerators, FishtasticBlocks.CLEAR_STAINED_GLASS);
    }

    private void generateGlassModels(BlockModelGenerators blockModelGenerators, Map<DyeColor, Holder<Block>> glassBlocks) {
        for (DyeColor color : DyeColor.values()) {
            Holder<Block> block = glassBlocks.get(color);
            ResourceLocation textureLoc = getGlassTextureLoc(block);
            ResourceLocation modelLoc = getGlassModelLoc(block);

            generateModelsForGlassBlock(blockModelGenerators, modelLoc, textureLoc, block);
        }
    }

    private static void generateModelsForGlassBlock(BlockModelGenerators blockModelGenerators, ResourceLocation modelLoc, ResourceLocation textureLoc, Holder<Block> block) {
        // Create block model in glass/ subdirectory
        ModelTemplates.CUBE_ALL.create(modelLoc, TextureMapping.cube(textureLoc), blockModelGenerators.modelOutput);

        // Create blockstate pointing to the model in glass subdirectory
        blockModelGenerators.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block.value(), modelLoc));

        // Create item model in default directory (required by Minecraft's hardcoded item model lookup)
        blockModelGenerators.delegateItemModel(block.value(), modelLoc);
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
        // ----- Rendering-only items with custom texture paths -----
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_ROD_BACKGROUND.value(), Fishtastic.id("item/fishing_bar"));
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_BOBBER.value(), Fishtastic.id("item/fishing_bobber"));

        // Zone HUD icons
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_ZONE_OCEAN.value(), Fishtastic.id("item/zone/ocean"));
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_ZONE_DEEP_OCEAN.value(), Fishtastic.id("item/zone/deep_ocean"));
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_ZONE_RIVER.value(), Fishtastic.id("item/zone/river"));
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_ZONE_NETHER.value(), Fishtastic.id("item/zone/nether"));
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_ZONE_CAVE.value(), Fishtastic.id("item/zone/cave"));
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_ZONE_HIGH_ALTITUDE.value(), Fishtastic.id("item/zone/high_altitude"));

        // Standard flat items
        itemModelGenerators.generateFlatItem(FishtasticItems.SPARKLE.value(), ModelTemplates.FLAT_ITEM);
        itemModelGenerators.generateFlatItem(FishtasticItems.REWARD_CHEST.value(), ModelTemplates.FLAT_ITEM);

        // Debug tools — reuse a vanilla texture as a placeholder, no dedicated art needed
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.COSMETIC_CAPTURE_WAND.value(),
                Ids.of("minecraft", "item/stick"));

        // ----- Copper / obsidian fishing rods (fishing rod style with _cast variant) -----
        generateFishingRod(itemModelGenerators, FishtasticItems.COPPER_FISHING_ROD.value());
        generateFishingRod(itemModelGenerators, FishtasticItems.OBSIDIAN_FISHING_ROD.value());

        // ----- Fish items (textures in item/fish/ subdirectory) -----
        generateFishItemModel(itemModelGenerators, FishtasticItems.GENERIC_FISH.value(), "generic_fish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ACUTE_IASPIS.value(), "acute_iaspis");
        generateFishItemModel(itemModelGenerators, FishtasticItems.AMERICAN_PADDLEFISH.value(), "american_paddlefish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ANGLER_FISH.value(), "angler_fish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ARCTIC_CHAR.value(), "arctic_char");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BETTA.value(), "betta");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BLACK_GHOST_KNIFEFISH.value(), "black_ghost_knifefish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BLAZED_GRUB.value(), "blazed_grub");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BLIND_CAVEFISH.value(), "blind_cavefish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BLIND_CAVE_TETRA.value(), "blind_cave_tetra");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BLUEGILL.value(), "bluegill");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BRIDLE_SHINER.value(), "bridle_shiner");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BULL_TROUT.value(), "bull_trout");
        generateFishItemModel(itemModelGenerators, FishtasticItems.CHAUNACOPS.value(), "chaunacops");
        generateFishItemModel(itemModelGenerators, FishtasticItems.CLOWN_LOACH.value(), "clown_loach");
        generateFishItemModel(itemModelGenerators, FishtasticItems.COMMON_OCTOPUS.value(), "common_octopus");
        generateFishItemModel(itemModelGenerators, FishtasticItems.DEVILS_HOLE_PUPFISH.value(), "devils_hole_pupfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.DISCUS.value(), "discus");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ELECTRIC_EEL.value(), "electric_eel");
        generateFishItemModel(itemModelGenerators, FishtasticItems.EUROPEAN_GRAYLING.value(), "european_grayling");
        generateFishItemModel(itemModelGenerators, FishtasticItems.FIRE_DARTFISH.value(), "fire_dartfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.FLAPJACK_OCTOPUS.value(), "flapjack_octopus");
        generateFishItemModel(itemModelGenerators, FishtasticItems.FRIED_SHRIMP.value(), "fried_shrimp");
        generateFishItemModel(itemModelGenerators, FishtasticItems.FROZEN_GIANT_MANTA_RAY.value(), "frozen_giant_manta_ray");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GARDEN_EEL.value(), "garden_eel");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GIANT_MANTA_RAY.value(), "giant_manta_ray");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GIRAFFE_CICHLID.value(), "giraffe_cichlid");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GLASS_CATFISH.value(), "glass_catfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GLASS_SQUID.value(), "glass_squid");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GOLDEN_MAHSEER.value(), "golden_mahseer");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GOLDEN_TROUT.value(), "golden_trout");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GREENSTRIPE_BARB.value(), "greenstripe_barb");
        generateFishItemModel(itemModelGenerators, FishtasticItems.INDIAN_GLASSY_FISH.value(), "indian_glassy_fish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.JAPANESE_SPIDER_CRAB.value(), "japanese_spider_crab");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LARGETOOTH_SAWFISH.value(), "largetooth_sawfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LEAFY_SEA_DRAGON.value(), "leafy_sea_dragon");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LINED_SEAHORSE.value(), "lined_seahorse");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LIONFISH.value(), "lionfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LIZARDFISH.value(), "lizardfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LONGNOSE_GAR.value(), "longnose_gar");
        generateFishItemModel(itemModelGenerators, FishtasticItems.MOLTEN_MOORISH_IDOL.value(), "molten_moorish_idol");
        generateFishItemModel(itemModelGenerators, FishtasticItems.MOORISH_IDOL.value(), "moorish_idol");
        generateFishItemModel(itemModelGenerators, FishtasticItems.NEON_GOBY.value(), "neon_goby");
        generateFishItemModel(itemModelGenerators, FishtasticItems.NEON_TETRA.value(), "neon_tetra");
        generateFishItemModel(itemModelGenerators, FishtasticItems.NORTHERN_PIKE.value(), "northern_pike");
        generateFishItemModel(itemModelGenerators, FishtasticItems.OCEAN_SUNFISH.value(), "ocean_sunfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.OPHISTERNON_CANDIDUM.value(), "ophisternon_candidum");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ORANGE_AUSTRALE_KILLIFISH.value(), "orange_australe_killifish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ORNATE_BICHIR.value(), "ornate_bichir");
        generateFishItemModel(itemModelGenerators, FishtasticItems.OSCAR.value(), "oscar");
        generateFishItemModel(itemModelGenerators, FishtasticItems.PARROTFISH.value(), "parrotfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.PLAICE.value(), "plaice");
        generateFishItemModel(itemModelGenerators, FishtasticItems.PORTUGUESE_MAN_O_WAR.value(), "portuguese_man_o_war");
        generateFishItemModel(itemModelGenerators, FishtasticItems.RAINBOW_TROUT.value(), "rainbow_trout");
        generateFishItemModel(itemModelGenerators, FishtasticItems.RAINFORDIA.value(), "rainfordia");
        generateFishItemModel(itemModelGenerators, FishtasticItems.RED_BELLIED_PIRAHNA.value(), "red_bellied_pirahna");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ROYAL_GARDEN_EEL.value(), "royal_garden_eel");
        generateFishItemModel(itemModelGenerators, FishtasticItems.SHRIMP.value(), "shrimp");
        generateFishItemModel(itemModelGenerators, FishtasticItems.STARFISH.value(), "starfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.SULPHUR_MOLLY.value(), "sulphur_molly");
        generateFishItemModel(itemModelGenerators, FishtasticItems.TRAPANIA_SCURRA.value(), "trapania_scurra");
        generateFishItemModel(itemModelGenerators, FishtasticItems.WATERFALL_CLIMBING_CAVE_FISH.value(), "waterfall_climbing_cave_fish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.WILLANS_CHROMODORIS.value(), "willans_chromodoris");
        generateFishItemModel(itemModelGenerators, FishtasticItems.YELLOWLINE_GOBY.value(), "yellowline_goby");
        generateFishItemModel(itemModelGenerators, FishtasticItems.YELLOWSTRIPE_GRUNT.value(), "yellowstripe_grunt");

        // ----- Bait items (textures in item/bait/ subdirectory) -----
        generateBaitItemModel(itemModelGenerators, FishtasticItems.WORMS.value(), "worms");
        generateBaitItemModel(itemModelGenerators, FishtasticItems.GUMMY_WORMS.value(), "gummy_worms");

        // ----- Trash items (textures in item/trash/ subdirectory) -----
        generateTrashItemModel(itemModelGenerators, FishtasticItems.SEA_GLASS.value(), "sea_glass");
        generateTrashItemModel(itemModelGenerators, FishtasticItems.OLD_TIRE.value(), "old_tire");
        generateTrashItemModel(itemModelGenerators, FishtasticItems.PLASTIC_LITTER.value(), "plastic_litter");

        // ----- The fish tank item: the tank block model, whose per-stack materials the loaders'
        // tank models resolve (A5.2; 26.1's fish_tank_composite item model type doesn't exist on 1.21.1).
        // Fabric resolves this id to its own tank model before the JSON is read; NeoForge bakes the
        // block model's "fishtastic:fish_tank" loader through this parent.
        generateItemWithParent(itemModelGenerators, FishtasticBlocks.FISH_TANK.value().asItem(),
                Ids.of("fishtastic", "block/fish_tank"));

        // ----- Items drawn by a BlockEntityWithoutLevelRenderer (A5.3) -----
        // 26.1's custom item model types (fish_pile_block, cosmetic_structure, the chest select)
        // don't exist on 1.21.1; these items render through builtin/entity instead.
        generateBuiltinEntityItem(itemModelGenerators, FishtasticBlocks.FISH_PILE.value().asItem());
        generateBuiltinEntityItem(itemModelGenerators, FishtasticItems.COSMETIC_TREASURE_CHEST.value());
        BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof FishTankStructureCosmeticItem)
                .forEach(item -> generateBuiltinEntityItem(itemModelGenerators, item));
        generateItemWithParent(itemModelGenerators, FishtasticItems.COSMETIC_LIT_CAMPFIRE.value(),
                Ids.withDefaultNamespace("block/campfire"));

        // ----- Menu-opening items — swap to the _alert texture whenever HAS_ALERT is present -----
        generateAlertConditionedItem(itemModelGenerators, FishtasticItems.FISHOPEDIA.value(), "fishopedia");
        generateAlertConditionedItem(itemModelGenerators, FishtasticItems.QUEST_BOOK.value(), "quest_book");
    }

    /**
     * Generates a base + "_alert" flat item model pair. The base model's {@code fishtastic:has_alert}
     * override swaps to the alert one while {@link FishtasticDataComponents#HAS_ALERT} is on the stack,
     * the same way the rods' {@code minecraft:cast} override swaps to their cast model.
     */
    private static void generateAlertConditionedItem(ItemModelGenerators itemModelGenerators, Item item, String textureName) {
        ResourceLocation alertModel = ModelTemplates.FLAT_ITEM.create(
                ModelLocationUtils.getModelLocation(item, "_alert"),
                TextureMapping.layer0(Fishtastic.id("item/" + textureName + "_alert")),
                itemModelGenerators.output);
        createWithOverride(itemModelGenerators, ModelTemplates.FLAT_ITEM, ModelLocationUtils.getModelLocation(item),
                TextureMapping.layer0(Fishtastic.id("item/" + textureName)),
                Fishtastic.id("has_alert"), alertModel);
    }

    /** The vanilla fishing rod's model pair: a {@code handheld_rod} base and {@code _cast}, switched by {@code minecraft:cast}. */
    private static void generateFishingRod(ItemModelGenerators itemModelGenerators, Item item) {
        ResourceLocation castModel = ModelTemplates.FLAT_HANDHELD_ROD_ITEM.create(
                ModelLocationUtils.getModelLocation(item, "_cast"),
                TextureMapping.layer0(TextureMapping.getItemTexture(item, "_cast")),
                itemModelGenerators.output);
        createWithOverride(itemModelGenerators, ModelTemplates.FLAT_HANDHELD_ROD_ITEM, ModelLocationUtils.getModelLocation(item),
                TextureMapping.layer0(item),
                Ids.withDefaultNamespace("cast"), castModel);
    }

    /** Writes {@code template} at {@code modelLoc} with one {@code overrides} entry: {@code predicate} = 1 selects {@code overrideModel}. */
    private static void createWithOverride(ItemModelGenerators itemModelGenerators, ModelTemplate template, ResourceLocation modelLoc,
                                           TextureMapping textures, ResourceLocation predicate, ResourceLocation overrideModel) {
        template.create(modelLoc, textures, itemModelGenerators.output, (location, slots) -> {
            JsonObject json = template.createBaseTemplate(location, slots);
            JsonObject predicates = new JsonObject();
            predicates.addProperty(predicate.toString(), 1);
            JsonObject override = new JsonObject();
            override.add("predicate", predicates);
            override.addProperty("model", overrideModel.toString());
            JsonArray overrides = new JsonArray();
            overrides.add(override);
            json.add("overrides", overrides);
            return json;
        });
    }

    private static void generateBuiltinEntityItem(ItemModelGenerators itemModelGenerators, Item item) {
        generateItemWithParent(itemModelGenerators, item, Ids.withDefaultNamespace("builtin/entity"));
    }

    /** An item model that is only a {@code parent} reference. */
    private static void generateItemWithParent(ItemModelGenerators itemModelGenerators, Item item, ResourceLocation parent) {
        new ModelTemplate(Optional.of(parent), Optional.empty())
                .create(ModelLocationUtils.getModelLocation(item), new TextureMapping(), itemModelGenerators.output);
    }

    /**
     * Generate a flat item model for a fish item whose texture is in the item/fish/ subdirectory.
     */
    private static void generateFishItemModel(ItemModelGenerators itemModelGenerators, Item item, String textureName) {
        generateFlatItemWithCustomTexture(itemModelGenerators, item, Fishtastic.id("item/fish/" + textureName));
    }

    /**
     * Generate a flat item model for a bait item whose texture is in the item/bait/ subdirectory.
     */
    private static void generateBaitItemModel(ItemModelGenerators itemModelGenerators, Item item, String textureName) {
        generateFlatItemWithCustomTexture(itemModelGenerators, item, Fishtastic.id("item/bait/" + textureName));
    }

    /**
     * Generate a flat item model for a trash item whose texture is in the item/trash/ subdirectory.
     */
    private static void generateTrashItemModel(ItemModelGenerators itemModelGenerators, Item item, String textureName) {
        generateFlatItemWithCustomTexture(itemModelGenerators, item, Fishtastic.id("item/trash/" + textureName));
    }

    /**
     * Generate a flat item model with a custom texture path.
     */
    private static void generateFlatItemWithCustomTexture(ItemModelGenerators itemModelGenerators, Item item, ResourceLocation texturePath) {
        ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(item), TextureMapping.layer0(texturePath), itemModelGenerators.output);
    }

    private static ResourceLocation getGlassTextureLoc(Holder<Block> block) {
        Optional<ResourceKey<Block>> key = block.unwrapKey();
        if (key.isPresent()) {
            return key.get().location().withPrefix("block/glass/");
        } else {
            throw new RuntimeException("Failed to access block holder.");
        }
    }

    private static ResourceLocation getGlassModelLoc(Holder<Block> block) {
        Optional<ResourceKey<Block>> key = block.unwrapKey();
        if (key.isPresent()) {
            return key.get().location().withPrefix("block/glass/");
        } else {
            throw new RuntimeException("Failed to access block holder.");
        }
    }
}

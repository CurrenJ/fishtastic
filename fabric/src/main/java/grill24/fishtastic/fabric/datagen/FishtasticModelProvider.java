package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Optional;

public class FishtasticModelProvider extends FabricModelProvider {
    public FishtasticModelProvider(FabricPackOutput output) {
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
            Identifier textureLoc = getGlassTextureLoc(block);
            Identifier modelLoc = getGlassModelLoc(block);

            generateModelsForGlassBlock(blockModelGenerators, modelLoc, textureLoc, block);
        }
    }

    private static void generateModelsForGlassBlock(BlockModelGenerators blockModelGenerators, Identifier modelLoc, Identifier textureLoc, Holder<Block> block) {
        // Create block model in glass/ subdirectory using Material for texture
        ModelTemplates.CUBE_ALL.create(modelLoc, TextureMapping.cube(new Material(textureLoc)), blockModelGenerators.modelOutput);

        // Create blockstate pointing to the model in glass subdirectory
        blockModelGenerators.blockStateOutput.accept(
            BlockModelGenerators.createSimpleBlock(block.value(),
                BlockModelGenerators.plainVariant(modelLoc))
        );

        // Create item model in default directory (required by Minecraft's hardcoded item model lookup)
        blockModelGenerators.registerSimpleItemModel(block.value(), modelLoc);
    }

    @Override
    public void generateItemModels(ItemModelGenerators itemModelGenerators) {
        // ----- Rendering-only items with custom texture paths -----
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_ROD_BACKGROUND.value(), Fishtastic.id("item/fishing_bar"));
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.FISHING_MINIGAME_BOBBER.value(), Fishtastic.id("item/fishing_bobber"));

        // Standard flat items
        itemModelGenerators.generateFlatItem(FishtasticItems.SPARKLE.value(), ModelTemplates.FLAT_ITEM);
        itemModelGenerators.generateFlatItem(FishtasticItems.REWARD_CHEST.value(), ModelTemplates.FLAT_ITEM);

        // Debug tools — reuse a vanilla texture as a placeholder, no dedicated art needed
        generateFlatItemWithCustomTexture(itemModelGenerators, FishtasticItems.COSMETIC_CAPTURE_WAND.value(),
                Identifier.fromNamespaceAndPath("minecraft", "item/stick"));

        // ----- Copper fishing rod (fishing rod style with _cast variant) -----
        itemModelGenerators.generateFishingRod(FishtasticItems.COPPER_FISHING_ROD.value());

        // ----- Fish items (textures in item/fish/ subdirectory) -----
        generateFishItemModel(itemModelGenerators, FishtasticItems.GENERIC_FISH.value(), "generic_fish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ACUTE_IASPIS.value(), "acute_iaspis");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BLAZED_GRUB.value(), "blazed_grub");
        generateFishItemModel(itemModelGenerators, FishtasticItems.BLUEGILL.value(), "bluegill");
        generateFishItemModel(itemModelGenerators, FishtasticItems.FLAPJACK_OCTOPUS.value(), "flapjack_octopus");
        generateFishItemModel(itemModelGenerators, FishtasticItems.FRIED_SHRIMP.value(), "fried_shrimp");
        generateFishItemModel(itemModelGenerators, FishtasticItems.FROZEN_GIANT_MANTA_RAY.value(), "frozen_giant_manta_ray");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GARDEN_EEL.value(), "garden_eel");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GIANT_MANTA_RAY.value(), "giant_manta_ray");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GLASS_SQUID.value(), "glass_squid");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GREENSTRIPE_BARB.value(), "greenstripe_barb");
        generateFishItemModel(itemModelGenerators, FishtasticItems.GUMMY_WORMS.value(), "gummy_worms");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LEAFY_SEA_DRAGON.value(), "leafy_sea_dragon");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LIZARDFISH.value(), "lizardfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.LONGNOSE_GAR.value(), "longnose_gar");
        generateFishItemModel(itemModelGenerators, FishtasticItems.MOLTEN_MOORISH_IDOL.value(), "molten_moorish_idol");
        generateFishItemModel(itemModelGenerators, FishtasticItems.MOORISH_IDOL.value(), "moorish_idol");
        generateFishItemModel(itemModelGenerators, FishtasticItems.NEON_TETRA.value(), "neon_tetra");
        generateFishItemModel(itemModelGenerators, FishtasticItems.NORTHERN_PIKE.value(), "northern_pike");
        generateFishItemModel(itemModelGenerators, FishtasticItems.OCEAN_SUNFISH.value(), "ocean_sunfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.PARROTFISH.value(), "parrotfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.PORTUGUESE_MAN_O_WAR.value(), "portuguese_man_o_war");
        generateFishItemModel(itemModelGenerators, FishtasticItems.RAINFORDIA.value(), "rainfordia");
        generateFishItemModel(itemModelGenerators, FishtasticItems.RED_BELLIED_PIRAHNA.value(), "red_bellied_pirahna");
        generateFishItemModel(itemModelGenerators, FishtasticItems.ROYAL_GARDEN_EEL.value(), "royal_garden_eel");
        generateFishItemModel(itemModelGenerators, FishtasticItems.SHRIMP.value(), "shrimp");
        generateFishItemModel(itemModelGenerators, FishtasticItems.STARFISH.value(), "starfish");
        generateFishItemModel(itemModelGenerators, FishtasticItems.TRAPANIA_SCURRA.value(), "trapania_scurra");
        generateFishItemModel(itemModelGenerators, FishtasticItems.WILLANS_CHROMODORIS.value(), "willans_chromodoris");
        generateFishItemModel(itemModelGenerators, FishtasticItems.YELLOWLINE_GOBY.value(), "yellowline_goby");
        generateFishItemModel(itemModelGenerators, FishtasticItems.WORMS.value(), "worms");

        // ----- Trash items (textures in item/fish/ subdirectory) -----
        generateFishItemModel(itemModelGenerators, FishtasticItems.SEA_GLASS.value(), "sea_glass");
        generateFishItemModel(itemModelGenerators, FishtasticItems.OLD_TIRE.value(), "old_tire");
        generateFishItemModel(itemModelGenerators, FishtasticItems.PLASTIC_LITTER.value(), "plastic_litter");

        // ----- Block items -----
        // Fish tank: declare as custom model (uses custom block model / renderer)
        itemModelGenerators.declareCustomModelItem(FishtasticBlocks.FISH_TANK.value().asItem());
    }

    /**
     * Generate a flat item model for a fish item whose texture is in the item/fish/ subdirectory.
     */
    private static void generateFishItemModel(ItemModelGenerators itemModelGenerators, Item item, String textureName) {
        generateFlatItemWithCustomTexture(itemModelGenerators, item, Fishtastic.id("item/fish/" + textureName));
    }

    /**
     * Generate a flat item model with a custom texture path.
     */
    private static void generateFlatItemWithCustomTexture(ItemModelGenerators itemModelGenerators, Item item, Identifier texturePath) {
        Identifier modelLoc = ModelLocationUtils.getModelLocation(item);
        Identifier createdModel = ModelTemplates.FLAT_ITEM.create(modelLoc, TextureMapping.layer0(new Material(texturePath)), itemModelGenerators.modelOutput);
        itemModelGenerators.itemModelOutput.accept(item, ItemModelUtils.plainModel(createdModel));
    }

    private static Identifier getGlassTextureLoc(Holder<Block> block) {
        Optional<ResourceKey<Block>> key = block.unwrapKey();
        if (key.isPresent()) {
            return key.get().identifier().withPrefix("block/glass/");
        } else {
            throw new RuntimeException("Failed to access block holder.");
        }
    }

    private static Identifier getGlassModelLoc(Holder<Block> block) {
        Optional<ResourceKey<Block>> key = block.unwrapKey();
        if (key.isPresent()) {
            return key.get().identifier().withPrefix("block/glass/");
        } else {
            throw new RuntimeException("Failed to access block holder.");
        }
    }
}

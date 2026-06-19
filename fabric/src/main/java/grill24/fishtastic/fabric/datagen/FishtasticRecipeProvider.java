package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

/**
 * Generates crafting recipes for all Fishtastic items.
 *
 * <ul>
 *   <li>Copper Fishing Rod – shaped, 3 copper ingots + 2 strings</li>
 *   <li>Fish Tank – shaped, 4 iron ingots + 4 glass blocks</li>
 *   <li>Borderless Stained Glass (×16) – shapeless, 1 vanilla stained glass → 1 borderless</li>
 *   <li>Clear Stained Glass (×16) – shapeless, 1 vanilla stained glass + 1 glass → 1 clear</li>
 *   <li>Bait items – shapeless, themed vanilla ingredients</li>
 *   <li>Fried Shrimp – smelting shrimp</li>
 * </ul>
 */
public class FishtasticRecipeProvider extends FabricRecipeProvider {

    public FishtasticRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        return new RecipeProvider(registries, output) {
            @Override
            public void buildRecipes() {
                HolderGetter<Item> items = this.registries.lookupOrThrow(Registries.ITEM);
                buildEquipmentRecipes(items);
                buildGlassRecipes(items);
                buildBaitAndFoodRecipes(items);
            }

            // -----------------------------------------------------------------
            // Equipment
            // -----------------------------------------------------------------

            private void buildEquipmentRecipes(HolderGetter<Item> items) {
                // Copper Fishing Rod: diagonal of 3 copper ingots + 2 strings
                ShapedRecipeBuilder.shaped(items, RecipeCategory.TOOLS, FishtasticItems.COPPER_FISHING_ROD.value())
                        .pattern("  C")
                        .pattern(" CS")
                        .pattern("C S")
                        .define('C', Items.COPPER_INGOT)
                        .define('S', Items.STRING)
                        .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                        .save(this.output);

                // Fish Tank: glass walls with iron corners (hollow 3×3)
                ShapedRecipeBuilder.shaped(items, RecipeCategory.DECORATIONS, FishtasticBlocks.FISH_TANK.value())
                        .pattern("IGI")
                        .pattern("G G")
                        .pattern("IGI")
                        .define('I', Items.IRON_INGOT)
                        .define('G', Items.GLASS)
                        .unlockedBy("has_glass", has(Items.GLASS))
                        .save(this.output);
            }

            // -----------------------------------------------------------------
            // Glass variants
            // -----------------------------------------------------------------

            private void buildGlassRecipes(HolderGetter<Item> items) {
                // Undyed glass variants
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.BORDERLESS_GLASS.value())
                        .requires(Items.GLASS)
                        .unlockedBy("has_glass", has(Items.GLASS))
                        .save(this.output);
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.CLEAR_GLASS.value())
                        .requires(Items.GLASS)
                        .requires(Items.GLASS)
                        .unlockedBy("has_glass", has(Items.GLASS))
                        .save(this.output);

                // Also allow crafting clear glass from borderless glass + plain glass
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.CLEAR_GLASS.value())
                        .requires(FishtasticBlocks.BORDERLESS_GLASS.value())
                        .requires(Items.GLASS)
                        .unlockedBy("has_borderless_glass", has(FishtasticBlocks.BORDERLESS_GLASS.value()))
                        .save(this.output, "borderless_glass_plus_glass_to_clear_glass");

                for (DyeColor color : DyeColor.values()) {
                    Block vanilla = getVanillaStainedGlass(color);
                    Holder<Block> borderless = FishtasticBlocks.BORDERLESS_STAINED_GLASS.get(color);
                    Holder<Block> clear = FishtasticBlocks.CLEAR_STAINED_GLASS.get(color);

                    // Borderless: polish one stained glass block to remove the border
                    ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, borderless.value())
                            .requires(vanilla)
                            .unlockedBy("has_stained_glass", has(vanilla))
                            .save(this.output);

                    // Clear: combine stained glass with plain glass to clarify the tint
                    ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, clear.value())
                            .requires(vanilla)
                            .requires(Items.GLASS)
                            .unlockedBy("has_stained_glass", has(vanilla))
                            .save(this.output);

                    // Also allow crafting clear from borderless + plain glass
                    ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, clear.value())
                            .requires(borderless.value())
                            .requires(Items.GLASS)
                            .unlockedBy("has_borderless_stained_glass", has(borderless.value()))
                            .save(this.output, borderless.getRegisteredName() + "_plus_glass_to_clear");
                }
            }

            // -----------------------------------------------------------------
            // Bait and food
            // -----------------------------------------------------------------

            private void buildBaitAndFoodRecipes(HolderGetter<Item> items) {
                // Worms: dig through 3 dirt blocks
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.WORMS.value())
                        .requires(Items.DIRT)
                        .requires(Items.DIRT)
                        .requires(Items.DIRT)
                        .unlockedBy("has_dirt", has(Items.DIRT))
                        .save(this.output);

                // Gummy Worms
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.GUMMY_WORMS.value())
                        .requires(FishtasticItems.WORMS.value())
                        .requires(Items.GOLDEN_CARROT)
                        .requires(Items.SUGAR)
                        .requires(Items.DIAMOND)
                        .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                        .save(this.output);

                // Blazed Grub: worms infused with blaze powder
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.BLAZED_GRUB.value())
                        .requires(FishtasticItems.WORMS.value())
                        .requires(Items.BLAZE_POWDER)
                        .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                        .save(this.output);

                // Ocean Bait: worms + kelp
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.OCEAN_BAIT.value())
                        .requires(FishtasticItems.WORMS.value())
                        .requires(Items.KELP)
                        .requires(Items.SEAGRASS)
                        .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                        .save(this.output);

                // Freshwater Bait: worms + mushrooms
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.FRESHWATER_BAIT.value())
                        .requires(FishtasticItems.WORMS.value())
                        .requires(Items.BROWN_MUSHROOM)
                        .requires(Items.RED_MUSHROOM)
                        .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                        .save(this.output);

                // Predator Bait: worms + raw meat
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.PREDATOR_BAIT.value())
                        .requires(FishtasticItems.WORMS.value())
                        .requires(ItemTags.MEAT)
                        .requires(ItemTags.MEAT)
                        .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                        .save(this.output);
            }

        };
    }

    @Override
    public String getName() {
        return "Fishtastic Recipes";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Block getVanillaStainedGlass(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_STAINED_GLASS;
            case ORANGE -> Blocks.ORANGE_STAINED_GLASS;
            case MAGENTA -> Blocks.MAGENTA_STAINED_GLASS;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            case YELLOW -> Blocks.YELLOW_STAINED_GLASS;
            case LIME -> Blocks.LIME_STAINED_GLASS;
            case PINK -> Blocks.PINK_STAINED_GLASS;
            case GRAY -> Blocks.GRAY_STAINED_GLASS;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_STAINED_GLASS;
            case CYAN -> Blocks.CYAN_STAINED_GLASS;
            case PURPLE -> Blocks.PURPLE_STAINED_GLASS;
            case BLUE -> Blocks.BLUE_STAINED_GLASS;
            case BROWN -> Blocks.BROWN_STAINED_GLASS;
            case GREEN -> Blocks.GREEN_STAINED_GLASS;
            case RED -> Blocks.RED_STAINED_GLASS;
            case BLACK -> Blocks.BLACK_STAINED_GLASS;
        };
    }
}
